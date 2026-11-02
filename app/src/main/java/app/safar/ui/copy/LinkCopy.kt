package app.safar.ui.copy

import app.safar.core.BikeCatalog
import app.safar.core.BikeState
import app.safar.core.LinkState
import kotlin.math.ceil

/**
 * The connection in the rider's words (docs/14-voice.md, "Status words"). Every screen that names the link
 * state uses these, so Today, Ride, Pair and the widget say the same thing. Never mentions Bluetooth internals;
 * those live in [developerDetail].
 */
object LinkCopy {
    /** Model name of the paired or connecting bike ("V-Strom SX"), or null when unknown. */
    fun modelName(bike: BikeState): String? = bike.saved?.model?.name
        ?: (bike.link as? LinkState.Connecting)?.name?.let { BikeCatalog.forBleName(it).name }

    /** "your V-Strom SX", or "your bike" when the model is unknown. */
    fun yourBike(modelName: String?): String = if (modelName.isNullOrBlank()) "your bike" else "your $modelName"

    /** The one large status line: "Connected", "Connecting to your V-Strom SX", "Bike is off or out of range". */
    fun headline(link: LinkState, modelName: String?, hasSavedBike: Boolean): String = when (link) {
        LinkState.Connected -> "Connected"
        is LinkState.Connecting -> "Connecting to ${yourBike(modelName)}"
        is LinkState.Reconnecting -> "Bike is off or out of range"
        LinkState.Searching -> "Looking for your bike"
        LinkState.BluetoothOff -> "Bluetooth is off"
        LinkState.Idle -> if (hasSavedBike) "Not connected" else "Not set up yet"
    }

    /** [headline] for a whole [BikeState]. */
    fun headline(bike: BikeState): String = headline(bike.link, modelName(bike), bike.saved != null)

    /** One calm sentence under the headline: what is happening, or what the rider can do. */
    fun detail(link: LinkState, modelName: String?, hasSavedBike: Boolean): String = when (link) {
        LinkState.Connected -> "Ready to ride. Turns, calls and the time go to ${Phrases.THE_DISPLAY}."
        is LinkState.Connecting -> "This takes a few seconds."
        is LinkState.Reconnecting -> Phrases.RECONNECT_PROMISE
        LinkState.Searching -> Phrases.IGNITION_HINT
        LinkState.BluetoothOff -> if (hasSavedBike) "Turn it on to connect to ${yourBike(modelName)}." else "Turn it on to set up your bike."
        LinkState.Idle -> if (hasSavedBike) "Turn the ignition on, then tap Connect." else "Pair your bike to see turns, calls and the time on its display."
    }

    /** [detail] for a whole [BikeState]. */
    fun detail(bike: BikeState): String = detail(bike.link, modelName(bike), bike.saved != null)

    /** One or two words for a stamp beside the headline. */
    fun stamp(link: LinkState, hasSavedBike: Boolean): String = when (link) {
        LinkState.Connected -> "connected"
        is LinkState.Connecting -> "connecting"
        is LinkState.Reconnecting -> "waiting"
        LinkState.Searching -> "looking"
        LinkState.BluetoothOff -> "bluetooth off"
        LinkState.Idle -> if (hasSavedBike) "offline" else "new"
    }

    /**
     * The technical line for Developer mode only: GATT step, MTU, frame count, back-off attempt. Null when there
     * is nothing technical to add.
     */
    fun developerDetail(link: LinkState, nowMillis: Long, mtu: Int? = null, framesReceived: Int = 0): String? = when (link) {
        LinkState.Connected -> listOfNotNull(mtu?.let { "MTU $it" }, "notify on", "$framesReceived frames").joinToString(" · ")
        is LinkState.Connecting -> "GATT connect · service discovery · ${link.name}"
        is LinkState.Reconnecting -> {
            val seconds = ceil((link.nextAttemptAtMillis - nowMillis).coerceAtLeast(0) / 1000.0).toInt()
            "back-off · attempt ${link.attempt} · next try in $seconds s"
        }
        LinkState.Searching -> "BLE scan · Suzuki names only"
        LinkState.BluetoothOff, LinkState.Idle -> null
    }
}
