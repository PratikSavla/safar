package app.safar.ui.format

import app.safar.protocol.DistanceUnit
import app.safar.protocol.formatDistance
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/** A number and its unit, kept apart so the UI can set them in different styles ("850" + "m"). */
data class ValueUnit(val value: String, val unit: String) {
    override fun toString(): String = "$value $unit"
}

/**
 * Pure text formatters for Atlas. Every time-based function takes `now` (and a zone) so
 * tests and previews are deterministic. Output is locale-stable on purpose: the app is
 * English-only and the cluster speaks ASCII.
 */
object Formatters {
    private const val MINUTE = 60L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR
    private const val WEEK_DAYS = 7

    private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.UK)
    private val clock24 = DateTimeFormatter.ofPattern("HH:mm", Locale.UK)
    private val clock12 = DateTimeFormatter.ofPattern("h:mm a", Locale.UK)
    private val clock12Compact = DateTimeFormatter.ofPattern("h:mma", Locale.UK)

    /**
     * Age of a reading: "now", "3 s ago", "2 min ago", "1 h ago", "yesterday", "3 days ago", then "12 Mar".
     * Timestamps in the future (clock skew) read as "now".
     */
    fun age(atMillis: Long, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val s = ((nowMillis - atMillis) / 1000).coerceAtLeast(0)
        return when {
            s < 1 -> "now"
            s < MINUTE -> "$s s ago"
            s < HOUR -> "${s / MINUTE} min ago"
            s < DAY -> "${s / HOUR} h ago"
            else -> dayAge(atMillis, nowMillis, zone)
        }
    }

    private fun dayAge(atMillis: Long, nowMillis: Long, zone: ZoneId): String {
        val then = Instant.ofEpochMilli(atMillis).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val days = today.toEpochDay() - then.toEpochDay()
        return when {
            days <= 1 -> "yesterday"
            days < WEEK_DAYS -> "$days days ago"
            else -> shortDate(then)
        }
    }

    /** "12 Mar". */
    fun shortDate(date: LocalDate): String = shortDate.format(date)

    /** Odometer, whole km with thousands separators: 12345 -> "12,345". */
    fun odometer(km: Int): String = groupDigits(km.toLong(), indian = false)

    /** Kilometres with one decimal and thousands separators: 1234.56 -> "1,234.6". */
    fun km1(km: Double): String {
        val tenths = (km * 10).roundToLong()
        val sign = if (tenths < 0) "-" else ""
        val whole = abs(tenths) / 10
        return "$sign${groupDigits(whole, indian = false)}.${abs(tenths) % 10}"
    }

    /**
     * Distance for navigation, as the cluster gets it: the digits of `:protocol` [formatDistance] without the
     * leading zeros. Under 1 km in metres rounded to 10 m ("850 m"), then km with one decimal rounded like JS
     * `toFixed(1)` ("1.1 km" for 1150 m), then whole km from 100 km ("123 km").
     */
    fun navDistance(meters: Int): ValueUnit {
        val d = formatDistance(meters.coerceAtLeast(0))
        val digits = d.text.trimStart('0').let { if (it.isEmpty() || it.startsWith('.')) "0$it" else it }
        return ValueUnit(digits, if (d.unit == DistanceUnit.METRES) "m" else "km")
    }

    /** Clock time: "14:05", or "2:05 PM" when [is24h] is false. */
    fun clock(atMillis: Long, zone: ZoneId = ZoneId.systemDefault(), is24h: Boolean = true): String {
        val t = Instant.ofEpochMilli(atMillis).atZone(zone)
        return (if (is24h) clock24 else clock12).format(t).uppercase(Locale.UK)
    }

    /** Clock time without the space, for a label next to a date: "17:00", or "5:00PM" when [is24h] is false. */
    fun clockCompact(atMillis: Long, zone: ZoneId = ZoneId.systemDefault(), is24h: Boolean = true): String {
        val t = Instant.ofEpochMilli(atMillis).atZone(zone)
        return (if (is24h) clock24 else clock12Compact).format(t).uppercase(Locale.UK)
    }

    /** ETA as the cluster gets it ("0412PM") shown for people: "04:12 PM". Anything else passes through. */
    fun clusterEta(eta: String?): String {
        if (eta == null || eta.length < 6) return eta ?: "…"
        return "${eta.substring(0, 2)}:${eta.substring(2, 4)} ${eta.substring(4)}"
    }

    /**
     * Money with the rider's currency symbol. Rupees use Indian digit grouping (1,23,456),
     * other symbols use groups of three. [decimals] is 0..2.
     */
    fun money(amount: Double, symbol: String = "₹", decimals: Int = 0): String {
        val dp = decimals.coerceIn(0, 2)
        val factor = if (dp == 0) 1L else if (dp == 1) 10L else 100L
        val scaled = (abs(amount) * factor).roundToLong()
        val whole = groupDigits(scaled / factor, indian = symbol == "₹")
        val frac = if (dp == 0) "" else "." + (scaled % factor).toString().padStart(dp, '0')
        val sign = if (amount < 0 && scaled != 0L) "-" else ""
        return "$sign$symbol$whole$frac"
    }

    /** Groups digits with commas: western 1,234,567 or Indian 12,34,567. */
    fun groupDigits(n: Long, indian: Boolean): String {
        val digits = abs(n).toString()
        val sign = if (n < 0) "-" else ""
        if (digits.length <= 3) return sign + digits
        val head = digits.dropLast(3)
        val tail = digits.takeLast(3)
        val size = if (indian) 2 else 3
        val groups = head.reversed().chunked(size).map { it.reversed() }.reversed()
        return sign + groups.joinToString(",") + "," + tail
    }

    /** Estimate as "≈312" (rounded half up), or an em dash when there is no estimate. */
    fun approx(value: Double?): String = if (value == null) "—" else "≈${floor(value + 0.5).toLong()}"
}
