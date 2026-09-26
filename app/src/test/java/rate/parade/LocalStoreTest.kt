package rate.parade

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate

class LocalStoreTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun newProcessRestoresOrderRemovedSourceAndPartialInput() {
        val directory = folder.newFolder()
        val original = ConversionState(listOf("JPY", "USD"), "EUR", "12,", ',', true)
        LocalStore(directory).writeState(original)
        assertEquals(original, LocalStore(directory).readState())
        val empty = original.copy(selected = emptyList())
        LocalStore(directory).writeState(empty)
        assertEquals(empty, LocalStore(directory).readState())
    }

    @Test fun catalogAndSnapshotSurviveRecreationWithExactDecimalsAndDates() {
        val directory = folder.newFolder()
        val store = LocalStore(directory)
        val day = LocalDate.of(2026, 9, 25)
        val snapshot = Snapshot(mapOf("USD" to Rate(BigDecimal.ONE, day),
            "EUR" to Rate(BigDecimal("0.1234567890123456789012345"), day.minusDays(1))), 500)
        val catalog = Catalog(listOf(CurrencyInfo("EUR", "Euro")), 200)
        store.writeCatalog(catalog); store.writeSnapshot(snapshot)
        val reopened = LocalStore(directory)
        assertEquals(snapshot, reopened.readSnapshot())
        assertEquals(catalog, reopened.readCatalog())
    }

    @Test fun interruptedWriteLeavesCommittedRecordAndCorruptionIsRejected() {
        val directory = folder.newFolder()
        val original = ConversionState().add("USD")
        LocalStore(directory).writeState(original)
        File(directory, "selection.pending").writeBytes(byteArrayOf(1, 2))
        assertEquals(original, LocalStore(directory).readState())
        File(directory, "selection").writeBytes(byteArrayOf(1, 2))
        assertEquals(ConversionState(), LocalStore(directory).readState())
    }
}
