package app.safar.ui.format

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.roundToInt

/**
 * How the cluster LCD draws navigation values (docs 4.2), for the preview only.
 * The bytes on the wire are built by `:protocol`; this mirrors them for display.
 */
object ClusterText {
    private val HUNDRED_KM = BigDecimal(100)

    /** Status words the cluster shows instead of the distance (docs 4.3), or null for a normal turn. */
    fun statusWord(status: Char): String? = when (status) {
        '0' -> "NO NETWORK"
        '2' -> "RECALC"
        '4' -> "GPS OFF"
        '5' -> "ARRIVED"
        '6' -> "NO INTERNET"
        else -> null
    }

    /**
     * Four characters and a unit letter: under 1 km "0850" + 'M', up to 99.9 km "01.2" + 'K',
     * from 100 km whole km "0123" + 'K'. Metres round to 10 m like the original app.
     */
    fun distance(meters: Int): ValueUnit {
        val r = ((meters.coerceAtLeast(0) / 10.0).roundToInt()) * 10
        if (r < 1000) return ValueUnit(r.toString().padStart(4, '0'), "M")
        val km = r / 1000.0
        // JS toFixed(1) rounds the exact binary value of the double (1.15 is 1.1499.. -> "1.1");
        // BigDecimal(Double) keeps that value, String.format would round "1.15" up instead.
        val tenths = BigDecimal(km).setScale(1, RoundingMode.HALF_UP)
        val text = if (tenths < HUNDRED_KM) {
            tenths.toPlainString().padStart(4, '0')
        } else {
            // The helper would print "100.0" (5 chars) for 99.95-99.99 km; keep the field 4 wide.
            km.roundToInt().coerceAtMost(9999).toString().padStart(4, '0')
        }
        return ValueUnit(text, "K")
    }
}
