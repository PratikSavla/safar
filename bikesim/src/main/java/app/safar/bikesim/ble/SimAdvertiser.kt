package app.safar.bikesim.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import app.safar.protocol.GattUuids
import kotlinx.coroutines.delay

/**
 * Connectable advertising with service FFF0 and, in the scan response, the device name. Android advertises the
 * adapter name, so [start] renames the adapter to the simulated cluster name and [stop] puts the old name back.
 */
@SuppressLint("MissingPermission") // SimPermissions.granted() is checked in start() and every call is guarded.
class SimAdvertiser(private val context: Context, private val events: PeripheralEvents) {
    @Volatile private var callback: AdvertiseCallback? = null
    @Volatile private var originalName: String? = null

    /** Renames the adapter to [name] and starts advertising. The result arrives in [PeripheralEvents.onAdvertising]. */
    suspend fun start(name: String): Boolean {
        if (callback != null) return true
        if (!SimPermissions.granted(context)) return fail("Bluetooth permission missing")
        val adapter = bluetoothAdapter(context) ?: return fail("no Bluetooth on this device")
        if (!adapter.isEnabled) return fail("Bluetooth is off")
        if (!rename(adapter, name)) return false
        val advertiser = guard("bluetoothLeAdvertiser") { adapter.bluetoothLeAdvertiser }
            ?: return fail("this device cannot advertise (no BLE peripheral mode)")
        val cb = advertiseCallback()
        callback = cb
        val started = guard("startAdvertising") { advertiser.startAdvertising(settings(), data(), scanResponse(), cb) }
        if (started == null) {
            callback = null
            restoreName()
            return false
        }
        return true
    }

    /** Stops advertising and gives the adapter its own name back. */
    fun stop() {
        val cb = callback ?: return restoreName()
        callback = null
        val adapter = bluetoothAdapter(context)
        guard("stopAdvertising") { adapter?.bluetoothLeAdvertiser?.stopAdvertising(cb) }
        events.onAdvertising(false)
        restoreName()
    }

    /** Advertising often stops when a central connects; this starts it again with the same name. */
    suspend fun restart(name: String) {
        val cb = callback ?: return
        callback = null
        guard("stopAdvertising") { bluetoothAdapter(context)?.bluetoothLeAdvertiser?.stopAdvertising(cb) }
        start(name)
    }

    private suspend fun rename(adapter: BluetoothAdapter, name: String): Boolean {
        val current = guard("getName") { adapter.name } ?: ""
        if (current == name) return true
        if (originalName == null) originalName = current
        if (guard("setName") { adapter.setName(name) } != true) return fail("the adapter name could not be set to $name")
        repeat(RENAME_POLLS) {
            if (guard("getName") { adapter.name } == name) {
                events.onEvent("Bluetooth name set to $name")
                return true
            }
            delay(RENAME_POLL_MS)
        }
        events.onEvent("Bluetooth name is not $name yet; advertising anyway", error = true)
        return true
    }

    private fun restoreName() {
        val name = originalName ?: return
        originalName = null
        val adapter = bluetoothAdapter(context) ?: return
        if (guard("setName") { adapter.setName(name) } == true) events.onEvent("Bluetooth name restored to $name")
    }

    private fun settings(): AdvertiseSettings = AdvertiseSettings.Builder()
        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
        .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
        .setConnectable(true)
        .setTimeout(0)
        .build()

    private fun data(): AdvertiseData = AdvertiseData.Builder()
        .addServiceUuid(ParcelUuid(GattUuids.SERVICE))
        .setIncludeDeviceName(false)
        .build()

    private fun scanResponse(): AdvertiseData = AdvertiseData.Builder().setIncludeDeviceName(true).build()

    private fun advertiseCallback() = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            events.onAdvertising(true)
        }

        override fun onStartFailure(errorCode: Int) {
            if (callback === this) callback = null
            Log.w(TAG, "Advertising failed with error $errorCode")
            events.onEvent("Advertising failed: ${advertiseError(errorCode)}", error = true)
            events.onAdvertising(false)
            restoreName()
        }
    }

    private fun fail(reason: String): Boolean {
        events.onEvent(reason, error = true)
        return false
    }

    private fun <T> guard(call: String, block: () -> T): T? = try {
        block()
    } catch (e: SecurityException) {
        Log.w(TAG, "Advertiser call $call refused", e)
        events.onEvent("$call refused: ${e.message ?: "permission"}", error = true)
        null
    } catch (e: IllegalStateException) {
        Log.w(TAG, "Advertiser call $call failed", e)
        events.onEvent("$call failed: ${e.message ?: "Bluetooth not ready"}", error = true)
        null
    }

    private companion object {
        const val RENAME_POLLS = 30
        const val RENAME_POLL_MS = 100L

        fun advertiseError(code: Int): String = when (code) {
            AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> "data too large"
            AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "too many advertisers"
            AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED -> "already started"
            AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> "internal error"
            AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "not supported on this device"
            else -> "error $code"
        }
    }
}
