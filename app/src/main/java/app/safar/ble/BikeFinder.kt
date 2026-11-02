package app.safar.ble

import app.safar.core.DebugLog
import app.safar.protocol.ConnectTiming
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The reconnect fallback scan ([ReconnectPolicy.Action.Find]): looks up to [ConnectTiming.FIND_SCAN_MS] for a
 * cluster advertising a given name, for a bike whose address changed (a rotating private address). It has
 * its own [BikeScanner], so the rider's scan on the Pair screen is never started, stopped or cleared by it.
 * Runs on the caller's serial scope; [cancel] drops a running search without a result.
 */
internal class BikeFinder(
    private val radio: BleRadio,
    private val log: DebugLog,
    private val scope: CoroutineScope,
) {
    private val scanner = radio.scanner(scope)
    private var job: Job? = null
    private var permissionLogged = false

    /** BLE name of [address] if the last search saw it. */
    fun nameOf(address: String): String? = scanner.nameOf(address)

    /** Scans for [name]; [onDone] gets the address it advertised from, or null (not found, cannot scan). */
    fun find(name: String?, onDone: (String?) -> Unit) {
        cancel()
        if (name == null) log.event("Search by name skipped", mapOf("reason" to "bike name unknown"))
        if (name == null || !scanAllowed() || !scanner.start()) {
            job = scope.launch { onDone(null) }
            return
        }
        log.event("Searching for the bike by name", mapOf("bike" to name, "timeoutMs" to "${ConnectTiming.FIND_SCAN_MS}"))
        job = scope.launch {
            val hit = withTimeoutOrNull(ConnectTiming.FIND_SCAN_MS) {
                scanner.results.map { list -> list.firstOrNull { it.name == name } }.filterNotNull().first()
            }
            scanner.stop()
            job = null
            if (hit == null) {
                log.event("Bike not found by name", mapOf("bike" to name))
            } else {
                log.event("Found the bike by name", mapOf("bike" to name, "address" to hit.address, "rssi" to "${hit.rssi}"))
            }
            onDone(hit?.address)
        }
    }

    /** Stops a running search; its [find] callback is not called. */
    fun cancel() {
        job?.cancel()
        job = null
        scanner.stop()
    }

    /** False (logged once) when the scan permission is missing. */
    private fun scanAllowed(): Boolean {
        val missing = radio.missingScanPermission() ?: return true
        if (!permissionLogged) {
            permissionLogged = true
            log.event("Search by name needs permission", mapOf("permission" to missing), error = true)
        }
        return false
    }
}
