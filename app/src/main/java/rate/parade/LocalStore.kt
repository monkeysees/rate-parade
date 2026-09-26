package rate.parade

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate

/** Versioned, independent records; failed writes cannot replace the last complete record. */
class LocalStore(private val directory: File) {
    init { check(directory.isDirectory || directory.mkdirs()) }

    fun readState(): ConversionState = read("selection") {
        val initialized = readBoolean()
        val selected = List(count()) { readUTF() }
        val source = readUTF().ifEmpty { null }
        ConversionState(selected, source, readUTF(), readChar(), initialized)
    } ?: ConversionState()

    fun writeState(state: ConversionState) = write("selection") {
        writeBoolean(state.initialized)
        writeInt(state.selected.size)
        state.selected.forEach(::writeUTF)
        writeUTF(state.source.orEmpty()); writeUTF(state.input); writeChar(state.decimalSeparator.code)
    }

    fun readSnapshot(): Snapshot? = read("rates", version = 2) { version ->
        val time = readLong()
        val rates = List(count()) { readUTF() to Rate(readUTF().toBigDecimal(), LocalDate.parse(readUTF())) }.toMap()
        Snapshot(rates, time, coverageVersion = version)
    }

    fun writeSnapshot(snapshot: Snapshot) = write("rates", version = snapshot.coverageVersion) {
        writeLong(snapshot.fetchedAt); writeInt(snapshot.rates.size)
        snapshot.rates.forEach { (code, rate) ->
            writeUTF(code); writeUTF(rate.value.toPlainString()); writeUTF(rate.date.toString())
        }
    }

    fun readCatalog(): Catalog? = read("catalog") {
        val time = readLong()
        Catalog(List(count()) { CurrencyInfo(readUTF(), readUTF()) }, time)
    }

    fun writeCatalog(catalog: Catalog) = write("catalog") {
        writeLong(catalog.fetchedAt); writeInt(catalog.currencies.size)
        catalog.currencies.forEach { writeUTF(it.code); writeUTF(it.name) }
    }

    private fun DataInputStream.count() = readInt().also { require(it in 0..1000) }
    private fun <T> read(name: String, version: Int = 1, body: DataInputStream.(Int) -> T): T? {
        val file = File(directory, name)
        if (!file.exists()) return null
        return try {
            require(file.length() <= 1_000_000)
            DataInputStream(file.inputStream().buffered()).use { stream ->
                val storedVersion = stream.readInt()
                require(storedVersion in 1..version)
                stream.body(storedVersion).also { require(stream.read() == -1) }
            }
        } catch (_: Exception) { null }
    }

    private fun write(name: String, version: Int = 1, body: DataOutputStream.() -> Unit) {
        val pending = File(directory, "$name.pending")
        try {
            FileOutputStream(pending).use { file ->
                val output = DataOutputStream(file)
                output.writeInt(version); output.body(); output.flush(); file.fd.sync()
            }
            Files.move(pending.toPath(), File(directory, name).toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { pending.delete() }
    }
}
