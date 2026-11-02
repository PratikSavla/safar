package app.safar.ui.format

import java.util.Locale
import kotlin.math.roundToInt

/**
 * How the cluster LCD draws navigation values (docs 4.2), for the preview only.
 * The bytes on the wire are built by `:protocol`; this mirrors them for display.
 */
object ClusterText {
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
        val text = if (km < 99.95) {
            String.format(Locale.ROOT, "%.1f", km).padStart(4, '0')
        } else {
            km.roundToInt().coerceAtMost(9999).toString().padStart(4, '0')
        }
        return ValueUnit(text, "K")
    }
}
