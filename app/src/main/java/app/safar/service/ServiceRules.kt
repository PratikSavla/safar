package app.safar.service

import android.annotation.SuppressLint
import android.content.pm.ServiceInfo
import android.os.Build
import app.safar.core.BikeState
import app.safar.core.LinkState
import app.safar.core.NavState
import app.safar.ui.format.Formatters

/**
 * The service keeps the process alive while the app is working on a link: connecting, connected, backing off
 * between reconnects, or waiting for Bluetooth to come back for a saved bike. A user disconnect or forget
 * leaves the link Idle, which stops it (docs 2.5).
 */
fun shouldRunService(state: BikeState): Boolean = when (state.link) {
    is LinkState.Connecting, LinkState.Connected, is LinkState.Reconnecting -> true
    LinkState.BluetoothOff -> state.saved != null
    LinkState.Idle, LinkState.Searching -> false
}

/**
 * Foreground service types this start may use, or null when none is allowed (API 34+ needs a granted
 * runtime permission per type). Below API 29 types do not exist and 0 is returned.
 */
@SuppressLint("InlinedApi") // Compile-time constants, only returned for sdk >= 29.
fun foregroundServiceTypes(sdk: Int, bluetoothGranted: Boolean, locationGranted: Boolean): Int? {
    if (sdk < Build.VERSION_CODES.Q) return 0
    val device = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
    val location = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
    if (sdk < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return if (locationGranted) device or location else device
    var types = 0
    if (bluetoothGranted) types = types or device
    if (locationGranted) types = types or location
    return types.takeIf { it != 0 }
}

/** Title, text and whether "Disconnect" makes sense, for the ongoing notification. */
data class NotificationContent(val title: String, val text: String, val canDisconnect: Boolean)

fun notificationContent(state: BikeState, nav: NavState): NotificationContent {
    val model = state.saved?.model?.name ?: "bike"
    val title = when (val link = state.link) {
        LinkState.Connected -> "Connected to $model"
        is LinkState.Connecting -> "Connecting to $model"
        is LinkState.Reconnecting -> "Reconnecting to $model · attempt ${link.attempt}"
        LinkState.BluetoothOff -> "Bluetooth is off"
        LinkState.Idle, LinkState.Searching -> "Not connected"
    }
    val parts = mutableListOf<String>()
    val snapshot = nav.snapshot
    if (nav.active && snapshot != null) {
        parts += "Next: ${snapshot.instruction} in ${Formatters.navDistance(snapshot.distanceToTurnM)}"
    }
    val reading = state.cluster ?: state.lastCluster
    if (reading != null) {
        val suffix = if (state.cluster == null) " (last seen)" else ""
        parts += "Odometer ${Formatters.odometer(reading.odometerKm)} km$suffix"
    }
    val text = parts.joinToString(" · ").ifEmpty { "Safar keeps the link to your cluster" }
    return NotificationContent(title, text, canDisconnect = state.link != LinkState.Idle)
}
