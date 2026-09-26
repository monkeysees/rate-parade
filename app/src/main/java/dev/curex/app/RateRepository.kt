package dev.curex.app

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

data class Catalog(val currencies: List<CurrencyInfo>, val fetchedAt: Long)
data class RateData(val catalog: Catalog?, val snapshot: Snapshot?, val error: Boolean = false)

interface RateGateway {
    fun currencies(): List<CurrencyInfo>
    fun rates(): Map<String, Rate>
}

class RateRepository(
    private val store: LocalStore,
    private val gateway: RateGateway,
    private val executor: Executor,
    private val now: () -> Long = System::currentTimeMillis,
) {
    @Volatile private var cached: RateData? = null
    private var pending: CompletableFuture<RateData>? = null

    @Synchronized fun load(): RateData = cached ?: RateData(store.readCatalog(), store.readSnapshot()).also { cached = it }

    @Synchronized fun refresh(force: Boolean = false): CompletableFuture<RateData> {
        pending?.let { return it }
        val future = CompletableFuture<RateData>()
        pending = future
        executor.execute {
            val old = load()
            var catalog = old.catalog
            var snapshot = old.snapshot
            var failed = false
            try {
                if (catalog == null || now() < catalog.fetchedAt || now() - catalog.fetchedAt >= 30 * 24 * 60 * 60 * 1000L) {
                    val fresh = Catalog(gateway.currencies(), now())
                    require(fresh.currencies.isNotEmpty())
                    store.writeCatalog(fresh)
                    catalog = fresh
                }
            } catch (_: Exception) { failed = true }
            if (force || refreshDue(snapshot?.fetchedAt, now())) {
                try {
                    val fresh = Snapshot(gateway.rates().toMap(), now())
                    store.writeSnapshot(fresh)
                    snapshot = fresh
                } catch (_: Exception) { failed = true }
            }
            val result = RateData(catalog, snapshot, failed)
            synchronized(this) { cached = result; pending = null }
            future.complete(result)
        }
        return future
    }
}
