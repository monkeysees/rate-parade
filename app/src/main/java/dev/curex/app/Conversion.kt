package dev.curex.app

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.util.Locale

data class CurrencyInfo(val code: String, val name: String)
data class Rate(val value: BigDecimal, val date: LocalDate)
data class Snapshot(val rates: Map<String, Rate>, val fetchedAt: Long, val coverageVersion: Int = 2) {
    init {
        require(rates.size >= 2 && rates["USD"]?.value?.compareTo(BigDecimal.ONE) == 0)
        require(rates.all { (code, rate) ->
            code.matches(Regex("[A-Z]{3}")) && rate.value.signum() > 0 &&
                rate.value.precision() <= 40 && rate.value.scale() in -20..30
        })
    }
}

data class ConversionState(
    val selected: List<String> = emptyList(),
    val source: String? = null,
    val input: String = "1",
    val decimalSeparator: Char = '.',
    val initialized: Boolean = false,
) {
    init {
        require(selected.distinct() == selected && selected.all { it.matches(Regex("[A-Z]{3}")) })
        require(source == null || source.matches(Regex("[A-Z]{3}")))
        require(input.length <= MAX_INPUT)
    }

    fun add(code: String): ConversionState = if (code in selected) this else
        copy(selected = selected + code, source = source ?: code, initialized = true)

    // Keep a removed source as the conversion anchor; never recalculate from a rounded row.
    fun remove(code: String) = copy(selected = selected - code, initialized = true)
    fun move(code: String, position: Int): ConversionState {
        if (code !in selected) return this
        val order = selected.toMutableList().apply { remove(code); add(position.coerceIn(0, size), code) }
        return copy(selected = order)
    }

    fun drop(code: String, target: String, after: Boolean): ConversionState {
        if (code !in selected || target !in selected || code == target) return this
        val remaining = selected - code
        return move(code, remaining.indexOf(target) + if (after) 1 else 0)
    }

    fun edit(code: String, text: String, separator: Char): ConversionState {
        require(code in selected)
        return copy(source = code, input = text, decimalSeparator = separator)
    }

    fun amount(target: String, snapshot: Snapshot?): BigDecimal? {
        val amount = parseInput(input, decimalSeparator) ?: return null
        if (target == source) return amount
        val sourceRate = snapshot?.rates?.get(source)?.value ?: return null
        val targetRate = snapshot.rates[target]?.value ?: return null
        return amount.multiply(targetRate).divide(sourceRate, MathContext(80, RoundingMode.HALF_EVEN))
    }

    companion object {
        // Leave room for full derived values; reject oversized numeric input without truncation.
        const val MAX_INPUT = 512
        const val MAX_NUMBER_LENGTH = 64
    }
}

fun parseInput(text: String, separator: Char): BigDecimal? {
    if (text.isEmpty() || text.length > ConversionState.MAX_NUMBER_LENGTH) return null
    val normalized = buildString {
        text.forEach { char ->
            when {
                char == separator -> append('.')
                char == '-' -> append(char)
                char.digitToIntOrNull() != null -> append(char.digitToInt())
                else -> return null
            }
        }
    }
    if (!normalized.matches(Regex("-?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)"))) return null
    return normalized.toBigDecimalOrNull()
}

fun displayAmount(amount: BigDecimal, code: String, locale: Locale): String {
    val minimum = runCatching { java.util.Currency.getInstance(code).defaultFractionDigits }.getOrDefault(2).coerceIn(0, 6)
    val rounded = amount.setScale(6, RoundingMode.HALF_EVEN).stripTrailingZeros()
    return rounded.setScale(maxOf(minimum, rounded.scale())).toPlainString()
        .replace('.', DecimalFormatSymbols.getInstance(locale).decimalSeparator)
}

const val REFRESH_INTERVAL = 8 * 60 * 60 * 1000L
fun refreshDue(lastSuccess: Long?, now: Long): Boolean =
    lastSuccess == null || now < lastSuccess || now - lastSuccess >= REFRESH_INTERVAL
