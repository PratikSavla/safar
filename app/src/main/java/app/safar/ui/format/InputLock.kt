package app.safar.ui.format

import app.safar.core.RideState
import kotlin.math.roundToInt

/**
 * UX rule 3: no typing while the bike moves. Returns the reason to show under a locked field,
 * or null when the rider may type. [hint] says what to do ("Stop to edit.").
 */
object InputLock {
    fun reason(ride: RideState, hint: String = "Stop to edit."): String? {
        if (!ride.moving) return null
        val speed = ride.speedKmh?.roundToInt()
        val at = if (speed != null) " ($speed km/h)" else ""
        return "Locked while moving$at. $hint"
    }
}
