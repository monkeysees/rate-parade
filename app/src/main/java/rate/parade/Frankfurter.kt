package rate.parade

import android.util.JsonReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.URL
import java.time.LocalDate
import javax.net.ssl.HttpsURLConnection

class Frankfurter : RateGateway {
    override fun currencies(): List<CurrencyInfo> = request("currencies") { reader ->
        val result = mutableListOf<CurrencyInfo>()
        reader.beginArray()
        while (reader.hasNext()) {
            var code: String? = null
            var name: String? = null
            reader.beginObject()
            while (reader.hasNext()) when (reader.nextName()) {
                "iso_code" -> code = reader.nextString()
                "name" -> name = reader.nextString()
                else -> reader.skipValue()
            }
            reader.endObject()
            require(code?.matches(Regex("[A-Z]{3}")) == true && !name.isNullOrBlank())
            result += CurrencyInfo(requireNotNull(code), requireNotNull(name))
            require(result.size <= 1000)
        }
        reader.endArray()
        require(result.isNotEmpty() && result.distinctBy { it.code }.size == result.size)
        result.sortedBy { it.code }
    }

    override fun rates(): Map<String, Rate> = request("rates?base=USD") { reader ->
        val result = linkedMapOf<String, Rate>()
        reader.beginArray()
        while (reader.hasNext()) {
            var base: String? = null
            var quote: String? = null
            var date: String? = null
            var value: String? = null
            reader.beginObject()
            while (reader.hasNext()) when (reader.nextName()) {
                "base" -> base = reader.nextString()
                "quote" -> quote = reader.nextString()
                "date" -> date = reader.nextString()
                "rate" -> value = reader.nextString() // Preserve the original decimal, never a Double.
                else -> reader.skipValue()
            }
            reader.endObject()
            require(base == "USD" && quote?.matches(Regex("[A-Z]{3}")) == true)
            val amount = requireNotNull(value).also { require(it.length <= 100) }.toBigDecimal()
            require(amount.signum() > 0 && amount.precision() <= 40 && amount.scale() in -20..30)
            val day = LocalDate.parse(requireNotNull(date))
            require(day >= LocalDate.of(1990, 1, 1) && day <= LocalDate.now(java.time.ZoneOffset.UTC).plusDays(1))
            require(result.put(requireNotNull(quote), Rate(amount, day)) == null)
            require(result.size <= 1000)
        }
        reader.endArray()
        require(result.isNotEmpty())
        val date = result.values.maxOf { it.date }
        result["USD"]?.let { require(it.value.compareTo(java.math.BigDecimal.ONE) == 0) }
        result["USD"] = Rate(java.math.BigDecimal.ONE, date)
        result
    }

    private fun <T> request(path: String, decode: (JsonReader) -> T): T {
        try {
            return requestOnce(path, decode)
        } catch (_: IOException) {
            // One bounded retry for transport errors. Invalid data is never retried.
            Thread.sleep(500)
            return requestOnce(path, decode)
        }
    }

    private fun <T> requestOnce(path: String, decode: (JsonReader) -> T): T {
        val connection = URL("https://api.frankfurter.dev/v2/$path").openConnection() as HttpsURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/json")
        try {
            if (connection.responseCode != 200) throw IOException("Rate service unavailable")
            // Bound the entire response, including unknown fields, before decoding.
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var count = input.read(buffer)
                while (count != -1) {
                    require(output.size() + count <= 1_000_000)
                    output.write(buffer, 0, count)
                    count = input.read(buffer)
                }
                output.toByteArray()
            }
            require(bytes.size <= 1_000_000)
            return JsonReader(InputStreamReader(bytes.inputStream(), Charsets.UTF_8)).use { reader ->
                decode(reader).also { require(reader.peek() == android.util.JsonToken.END_DOCUMENT) }
            }
        } finally { connection.disconnect() }
    }
}
