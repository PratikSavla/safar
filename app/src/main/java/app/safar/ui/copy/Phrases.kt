package app.safar.ui.copy

import app.safar.ui.format.ValueSource

/**
 * Shared rider-facing phrases from the voice guide (docs/14-voice.md). Use these instead of retyping them, so
 * every screen says the same thing the same way. Plain, warm, short; about the bike and the ride.
 */
object Phrases {
    /** Replaces "Not in version 1" on switches and rows that are planned. */
    const val COMING_SOON = "Coming soon"

    /** Replaces "No PIN needed". Say it once, on the pairing screen; drop it elsewhere. */
    const val NO_CODE_NEEDED = "Pairs in a tap — no code needed."

    /** The bike's cluster, in a sentence. Never "cluster" in normal mode. */
    const val THE_DISPLAY = "the display"
    const val YOUR_BIKES_DISPLAY = "your bike’s display"

    /** Replaces "Scan" / "Scan for my bike". */
    const val LOOK_FOR_BIKE = "Look for your bike"
    const val LOOK_AGAIN = "Look again"
    const val LOOKING_FOR_BIKE = "Looking for your bike"

    const val IGNITION_HINT = "Turn the ignition on and keep your phone close."
    const val TURN_ON_BLUETOOTH = "Turn on Bluetooth"

    /** Replaces the back-off schedule and attempt count. */
    const val RECONNECT_PROMISE = "We’ll reconnect as soon as it’s back."
    const val BIKE_AWAY = "Bike is off or out of range. $RECONNECT_PROMISE"

    /** Replaces "notification access". */
    const val ALLOW_ALERTS = "Allow Safar to read Google Maps and WhatsApp alerts"

    /** Before the first reading while connected. */
    val WAITING_FOR_BIKE: String = checkNotNull(ValueSource.BIKE.waiting)
    val WAITING_FOR_GPS: String = checkNotNull(ValueSource.PHONE_GPS.waiting)
    val WAITING_FOR_MAPS: String = checkNotNull(ValueSource.GOOGLE_MAPS.waiting)

    /** Toasts for the Developer mode unlock and switch. */
    const val DEVELOPER_ON = "You’re now a developer"
    const val DEVELOPER_OFF = "Developer mode is off"
}
