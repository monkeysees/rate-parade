package dev.curex.app

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale

class ConversionTest {
    private val date = LocalDate.of(2026, 9, 25)
    private val snapshot = Snapshot(mapOf("USD" to Rate(BigDecimal.ONE, date),
        "EUR" to Rate(BigDecimal("0.8"), date), "JPY" to Rate(BigDecimal("160"), date)), 100)
    private val state = ConversionState(listOf("USD", "EUR", "JPY"), "EUR", "12.50", '.', true)

    @Test fun convertsFromAnySourceUsingCommonBase() {
        assertEquals(0, BigDecimal("15.625").compareTo(state.amount("USD", snapshot)))
        assertEquals(0, BigDecimal("2500").compareTo(state.amount("JPY", snapshot)))
        val yenSource = state.edit("JPY", "100", '.')
        assertEquals(0, BigDecimal("0.5").compareTo(yenSource.amount("EUR", snapshot)))
    }

    @Test fun roundsOnlyForDisplayAndKeepsHighPrecision() {
        val third = snapshot.copy(rates = snapshot.rates + ("EUR" to Rate(BigDecimal("3"), date)))
        val one = state.edit("EUR", "1", '.')
        assertEquals("0.333333", displayAmount(one.amount("USD", third)!!, "USD", Locale.US))
        assertTrue(one.amount("USD", third)!!.precision() >= 70)
        assertEquals("53.333333", displayAmount(one.amount("JPY", third)!!, "JPY", Locale.US))
        val large = state.edit("USD", "123456789012345678901234567890.123456789", '.')
        assertEquals(BigDecimal("98765431209876543120987654312.0987654312"), large.amount("EUR", snapshot))
    }

    @Test fun preservesPartialInputAndDoesNotConfuseEmptyWithZero() {
        assertNull(parseInput("", '.'))
        assertNull(parseInput("-", '.'))
        assertNull(parseInput(".", '.'))
        assertEquals(BigDecimal("12"), parseInput("12.", '.'))
        assertEquals("12.", state.edit("USD", "12.", '.').input)
        assertEquals(0, state.edit("USD", "0", '.').amount("JPY", snapshot)!!.signum())
        assertNull(state.edit("USD", "", '.').amount("JPY", snapshot))
        assertNull(parseInput("1,000", '.'))
        assertNull(parseInput("1e5", '.'))
        assertNull(parseInput("NaN", '.'))
        assertNull(parseInput("1.2.3", '.'))
    }

    @Test fun supportsLocaleSeparatorsAndDigits() {
        assertEquals(BigDecimal("12.50"), parseInput("12,50", ','))
        assertEquals(BigDecimal("12.5"), parseInput("١٢٫٥", '٫'))
        assertEquals(BigDecimal("-0.5"), parseInput("-,5", ','))
        assertNull(parseInput("12.50", ','))
        assertEquals("12,50", displayAmount(BigDecimal("12.5"), "EUR", Locale.GERMANY))
    }

    @Test fun reorderingAndRemovingSourcePreserveExactAnchor() {
        val moved = state.move("EUR", 0)
        assertEquals(listOf("EUR", "USD", "JPY"), moved.selected)
        assertEquals(state.source, moved.source)
        assertEquals(state.input, moved.input)
        assertEquals(state.amount("JPY", snapshot), moved.amount("JPY", snapshot))
        assertEquals(state.amount("JPY", snapshot), moved.remove("EUR").amount("JPY", snapshot))
        assertEquals(state, state.add("EUR"))
    }

    @Test fun droppingOnEitherHalfOfARowPlacesCurrencyAtThatEdge() {
        assertEquals(listOf("EUR", "USD", "JPY"), state.drop("USD", "EUR", false).selected)
        assertEquals(listOf("EUR", "JPY", "USD"), state.drop("USD", "JPY", true).selected)
        assertEquals(listOf("JPY", "USD", "EUR"), state.drop("JPY", "USD", false).selected)
        assertEquals(listOf("USD", "JPY", "EUR"), state.drop("JPY", "USD", true).selected)
        assertEquals(state, state.drop("USD", "USD", true))
        assertEquals(state.source, state.drop("USD", "JPY", true).source)
        assertEquals(state.input, state.drop("USD", "JPY", true).input)
    }

    @Test fun newSnapshotRecomputesTogetherAndMissingRatesStayUnavailable() {
        val fresh = snapshot.copy(rates = snapshot.rates + ("JPY" to Rate(BigDecimal("80"), date.minusDays(1))))
        assertEquals(0, BigDecimal("1250").compareTo(state.amount("JPY", fresh)))
        assertEquals("12.50", state.input)
        assertNull(state.amount("GBP", snapshot))
        assertNull(state.amount("USD", snapshot.copy(rates = snapshot.rates - "EUR")))
    }

    @Test fun refreshEligibilityHandlesBoundaryAndClockRollback() {
        assertTrue(refreshDue(null, 100))
        assertFalse(refreshDue(100, 100 + REFRESH_INTERVAL - 1))
        assertTrue(refreshDue(100, 100 + REFRESH_INTERVAL))
        assertTrue(refreshDue(100, 99))
    }

    @Test fun largeDerivedAmountsFitTheEditorAndOversizedEditsAreNotTruncated() {
        val large = state.edit("USD", "9".repeat(64), '.')
        val rendered = displayAmount(large.amount("JPY", snapshot)!!, "JPY", Locale.US)
        assertTrue(rendered.length > 64)
        assertTrue(rendered.length < ConversionState.MAX_INPUT)
        val edited = large.edit("JPY", rendered, '.')
        assertEquals(rendered, edited.input)
        assertNull(edited.amount("USD", snapshot))
    }
}
