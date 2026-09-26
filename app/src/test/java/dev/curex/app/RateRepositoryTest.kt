package dev.curex.app

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate
import java.util.concurrent.Executor

class RateRepositoryTest {
    @get:Rule val folder = TemporaryFolder()
    private val direct = Executor { it.run() }
    private val date = LocalDate.of(2026, 9, 25)
    private val rates = mapOf("USD" to Rate(BigDecimal.ONE, date), "EUR" to Rate(BigDecimal("0.8"), date))

    @Test fun firstFetchPersistsRatesAndCatalogSeparatelyAndUsesCacheUntilDue() {
        val directory = folder.newFolder()
        val server = Server(rates)
        var time = 100L
        val repository = RateRepository(LocalStore(directory), server, direct) { time }
        assertEquals(rates, repository.refresh().get().snapshot!!.rates)
        time += REFRESH_INTERVAL - 1
        repository.refresh().get()
        assertEquals(1, server.rateCalls)
        time += 1
        repository.refresh().get()
        assertEquals(2, server.rateCalls)
        assertEquals(1, server.catalogCalls)
        assertEquals(rates, RateRepository(LocalStore(directory), server, direct).load().snapshot!!.rates)
    }

    @Test fun failedRefreshRetainsLastValidCacheAndSuccessfulFetchTime() {
        val directory = folder.newFolder()
        val server = Server(rates)
        val repository = RateRepository(LocalStore(directory), server, direct) { 100 }
        val previous = repository.refresh().get()
        server.failure = true
        val failed = repository.refresh(force = true).get()
        assertTrue(failed.error)
        assertEquals(previous.snapshot, failed.snapshot)
        assertEquals(previous.snapshot, LocalStore(directory).readSnapshot())
        server.failure = false
        server.response = mapOf("USD" to Rate(BigDecimal.ZERO, date), "EUR" to Rate(BigDecimal.ONE, date))
        assertTrue(repository.refresh(true).get().error)
        assertEquals(previous.snapshot, repository.load().snapshot)
    }

    @Test fun overlappingRefreshesShareOneRequestEvenForManualRefresh() {
        val queued = mutableListOf<Runnable>()
        val server = Server(rates)
        val repository = RateRepository(LocalStore(folder.newFolder()), server, Executor { queued += it })
        val first = repository.refresh()
        val second = repository.refresh(true)
        assertSame(first, second)
        queued.single().run()
        assertEquals(rates, first.get().snapshot!!.rates)
        assertEquals(1, server.rateCalls)
    }

    @Test fun catalogFailureDoesNotDiscardUsableRatesAndNextAttemptRecovers() {
        val server = Server(rates).apply { catalogFailure = true }
        val repository = RateRepository(LocalStore(folder.newFolder()), server, direct) { 100 }
        val first = repository.refresh().get()
        assertTrue(first.error)
        assertNull(first.catalog)
        assertEquals(rates, first.snapshot!!.rates)
        server.catalogFailure = false
        assertNotNull(repository.refresh().get().catalog)
        assertEquals(1, server.rateCalls)
    }

    @Test fun freshLegacyCacheRefreshesOnUpgradeAndRetainsRatesWhenOffline() {
        val directory = folder.newFolder()
        java.io.DataOutputStream(java.io.File(directory, "rates").outputStream()).use { out ->
            out.writeInt(1)
            out.writeLong(100)
            out.writeInt(rates.size)
            rates.forEach { (code, rate) ->
                out.writeUTF(code); out.writeUTF(rate.value.toPlainString()); out.writeUTF(rate.date.toString())
            }
        }
        val expanded = rates + ("AMD" to Rate(BigDecimal("383.5"), date))
        val server = Server(expanded).apply { failure = true }
        val repository = RateRepository(LocalStore(directory), server, direct) { 101 }
        val offline = repository.refresh().get()
        assertTrue(offline.error)
        assertEquals(rates, offline.snapshot!!.rates)
        assertEquals(100L, offline.snapshot.fetchedAt)
        server.failure = false
        val upgraded = repository.refresh().get()
        assertEquals(expanded, upgraded.snapshot!!.rates)
        assertEquals(expanded, LocalStore(directory).readSnapshot()!!.rates)
        repository.refresh().get()
        assertEquals(2, server.rateCalls)
    }

    private class Server(var response: Map<String, Rate>) : RateGateway {
        var rateCalls = 0
        var catalogCalls = 0
        var failure = false
        var catalogFailure = false
        override fun rates(): Map<String, Rate> {
            rateCalls++
            if (failure) throw IOException("offline")
            return response
        }
        override fun currencies(): List<CurrencyInfo> {
            catalogCalls++
            if (catalogFailure) throw IOException("offline")
            return listOf(CurrencyInfo("USD", "US Dollar"), CurrencyInfo("EUR", "Euro"))
        }
    }
}
