package app.safar.ble

import android.annotation.SuppressLint
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import app.safar.core.DebugLog
import app.safar.core.ScannedBike
import app.safar.protocol.ConnectTiming
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * BLE scan (docs 2.2 step 2): no filter, [ConnectTiming.SCAN_DURATION_MS], clusters only, one row per address
 * with live RSSI. Every Android call is guarded by a permission check and a SecurityException handler.
 */
@SuppressLint("MissingPermission") // BlePermissions.canScan / canConnect is checked before each call.
class BleScanner(
    private val context: Context,
    private val log: DebugLog,
    private val scope: CoroutineScope,
) : BikeScanner {
    private val lock = Any()
    private val list = ScanList()
    private var stopJob: Job? = null

    private val _results = MutableStateFlow<List<ScannedBike>>(emptyList())
    override val results: StateFlow<List<ScannedBike>> = _results.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    override val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = handle(result)

        override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach(::handle)

        override fun onScanFailed(errorCode: Int) {
            log.event("Scan failed", mapOf("code" to errorCode.toString()), error = true)
            _scanning.value = false
        }
    }

    override fun nameOf(address: String): String? = synchronized(lock) { list.nameOf(address) }

    /** Starts a new 10 s scan and clears the list. Returns false (and logs why) when it cannot scan. */
    override fun start(): Boolean {
        if (!BlePermissions.canScan(context)) {
            log.event("Scan needs permission", mapOf("permission" to BlePermissions.scanPermissionName()), error = true)
            return false
        }
        val scanner = bluetoothAdapter(context)?.takeIf { it.isEnabled }?.bluetoothLeScanner
        if (scanner == null) {
            log.event("Scan not possible: Bluetooth off", error = true)
            return false
        }
        stop()
        synchronized(lock) { list.clear() }
        _results.value = emptyList()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        try {
            scanner.startScan(null, settings, callback)
        } catch (e: SecurityException) {
            Log.w(TAG, "startScan refused", e)
            log.event("Scan refused", mapOf("reason" to (e.message ?: "SecurityException")), error = true)
            return false
        }
        _scanning.value = true
        log.event("Scan started", mapOf("durationMs" to ConnectTiming.SCAN_DURATION_MS.toString(), "filter" to "none"))
        stopJob = scope.launch {
            delay(ConnectTiming.SCAN_DURATION_MS)
            stop()
        }
        return true
    }

    override fun stop() {
        stopJob?.cancel()
        stopJob = null
        if (!_scanning.value) return
        _scanning.value = false
        try {
            if (BlePermissions.canScan(context)) bluetoothAdapter(context)?.bluetoothLeScanner?.stopScan(callback)
        } catch (e: SecurityException) {
            Log.w(TAG, "stopScan refused", e)
        } catch (e: IllegalStateException) {
            // Thrown when Bluetooth turned off meanwhile; the scan is already gone.
            Log.w(TAG, "stopScan with Bluetooth off", e)
        }
        log.event("Scan stopped", mapOf("found" to synchronized(lock) { list.items.size }.toString()))
    }

    private fun handle(result: ScanResult) {
        val address = result.device.address
        val name = result.scanRecord?.deviceName ?: deviceName(result)
        val (change, items) = synchronized(lock) { list.add(name, address, result.rssi) to list.items }
        if (change == ScanList.Change.NONE) return
        _results.value = items
        if (change == ScanList.Change.NEW) {
            val found = items.first { it.address == address }
            log.event("Found bike", mapOf("bike" to found.name, "address" to address, "rssi" to "${result.rssi}"))
        }
    }

    private fun deviceName(result: ScanResult): String? {
        if (!BlePermissions.canConnect(context)) return null
        return try {
            result.device.name
        } catch (e: SecurityException) {
            Log.w(TAG, "Reading a scanned device name was refused", e)
            null
        }
    }
}
