package app.safar.service

/** Bars that count as low: the cluster's last bar (Settings and Fuel: "At 1 bar"). */
const val LOW_FUEL_BARS = 1

/** Bars at or above which a fired reminder is armed again (a refill), so a gauge bouncing 1-2 posts once. */
const val LOW_FUEL_REARM_BARS = 3

/**
 * Decides when the low-fuel reminder fires: when the live fuel bars drop to [LOW_FUEL_BARS] from a higher reading
 * of the same connection. A connection that starts on 1 bar does not fire (the rider already saw it), and no
 * reading (null: disconnected, or the first frame whose fuel is discarded) resets the comparison.
 */
class LowFuelRule {
    private var last: Int? = null
    private var armed = true

    /** Feed every bars value; true when the reminder should be posted now. */
    fun next(bars: Int?, enabled: Boolean): Boolean {
        val previous = last
        last = bars
        if (bars == null) return false
        if (bars >= LOW_FUEL_REARM_BARS) armed = true
        val fire = enabled && armed && bars <= LOW_FUEL_BARS && previous != null && previous > LOW_FUEL_BARS
        if (fire) armed = false
        return fire
    }
}
