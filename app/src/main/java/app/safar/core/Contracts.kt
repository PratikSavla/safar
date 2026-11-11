package app.safar.core

import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** BLE link to the cluster: scan, connect, reconnect, cluster frames. Implemented in `ble`. */
interface BikeController {
    val state: StateFlow<BikeState>
    fun startScan()
    fun stopScan()
    fun connect(address: String)
    /** User disconnect: close GATT, stop reconnecting until the rider connects again (docs 2.5). */
    fun disconnect()
    /** Disconnect and delete the saved bike. */
    fun forgetBike()
    /** Debug: send one navigation frame with this icon code 1..45 (assignment task 4). */
    fun sendIconTest(iconCode: Int)
}

/**
 * Event frames for the cluster ('2', '4', '5'). Implemented by the BLE session, which knows the checksum
 * mode and repeats each frame (docs 5.2). Calls are ignored while not connected.
 */
interface ClusterOutbox {
    fun sendCall(name: String, source: AlertSource, answered: Boolean)
    fun sendMissedCalls(name: String, count: Int, source: AlertSource)
    fun sendMessages(name: String, count: Int, source: AlertSource)
    /** The [source] call stopped ringing without an answer or a missed call: stop repeating its ringing frame. */
    fun stopRinging(source: AlertSource) {}
}

/** Google Maps notification bridge (docs 11). Implemented in `phone`. */
interface NavigationBridge {
    val state: StateFlow<NavState>
    /** Opens Google Maps navigation (`google.navigation:q=<query>&mode=l`) and waits for its notification. */
    fun openInMaps(label: String, query: String)
    /** Rider ended navigation in Safar: the session sends one "ended" frame and stops the timer. */
    fun end()
}

/** Calls and WhatsApp alerts (docs 5). Implemented in `phone`; pushes frames through [ClusterOutbox]. */
interface AlertCenter {
    val state: StateFlow<AlertsState>
    fun clearMissedCalls()
    fun clearMessages()
}

/** Battery, charge, signal, internet and GPS state for the status frame. Implemented in `phone`. */
interface PhoneStatusSource {
    val state: StateFlow<PhoneSnapshot>
}

/** Phone GPS speed, trip recording and parked spot. Implemented in `phone`. */
interface RideTracker {
    val state: StateFlow<RideState>
    /** Stops the active trip, if recording. */
    fun stopTrip() {}
    /** Starts a new trip, if not already recording. */
    fun startTrip() {}
}

/** Debug log (docs 12.6). Implemented in `log`. Thread-safe. */
interface DebugLog {
    val records: StateFlow<List<LogRecord>>
    val sessionActive: StateFlow<Boolean>
    fun frame(kind: LogKind, bytes: ByteArray, ok: Boolean, note: String? = null)
    fun event(name: String, detail: Map<String, String> = emptyMap(), error: Boolean = false)
    fun startSession()
    fun stopSession()
    fun clear()
    /** Writes a `suzuki-ble-log/1` JSON Lines file in app cache for the share sheet. */
    suspend fun export(hideNames: Boolean): File
}
