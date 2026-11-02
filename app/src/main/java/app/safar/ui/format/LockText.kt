package app.safar.ui.format

import kotlin.math.roundToInt

/** UX rule 3: no typing while the phone moves. The reason the UI shows next to a locked field. */
object LockText {
    /** "Locked while moving (42 km/h). Stop to edit." Null when [moving] is false. */
    fun whileMoving(moving: Boolean, speedKmh: Float?): String? {
        if (!moving) return null
        val speed = speedKmh?.let { " (${it.roundToInt()} km/h)" }.orEmpty()
        return "Locked while moving$speed. Stop to edit."
    }
}
