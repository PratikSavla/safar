package app.safar.bikesim.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.util.Log
import app.safar.protocol.GattUuids

/**
 * The GATT server of the simulated cluster. One central at a time: the last one that connected gets the
 * notifications. Every Bluetooth call checks the runtime permission and catches [SecurityException].
 */
@SuppressLint("MissingPermission") // SimPermissions.granted() is checked in open() and every call is guarded.
class SimGattServer(private val context: Context, private val events: PeripheralEvents) {
    @Volatile private var server: BluetoothGattServer? = null

    @Volatile internal var central: BluetoothDevice? = null

    @Volatile internal var notifyEnabled: Boolean = false

    private val pending = ArrayDeque<BluetoothGattService>()
    private val lock = Any()

    /** Opens the server and adds FFF0 and 0x180A, one after the other. */
    fun open(): Boolean {
        if (server != null) return true
        if (!SimPermissions.granted(context)) return fail("Bluetooth permission missing")
        val manager = bluetoothManager(context) ?: return fail("no Bluetooth on this device")
        val opened = guard("openGattServer") { manager.openGattServer(context, SimGattCallback(this, events)) }
            ?: return fail("GATT server could not be opened")
        server = opened
        synchronized(lock) {
            pending.clear()
            pending.addLast(SimServices.cluster())
            pending.addLast(SimServices.deviceInformation())
        }
        addNextService()
        return true
    }

    fun close() {
        val s = server ?: return
        server = null
        central = null
        notifyEnabled = false
        guard("close") { s.clearServices(); s.close() }
        events.onEvent("GATT server closed")
    }

    /** Sends [frame] on FFF2. False, with an event, when nobody listens or the stack refuses it. */
    fun notify(frame: ByteArray): Boolean {
        val s = server ?: return fail("GATT server is not open")
        val device = central ?: return fail("no central connected")
        if (!notifyEnabled) return fail("the central has not enabled notify")
        val rx = s.getService(GattUuids.SERVICE)?.getCharacteristic(GattUuids.RX_CHARACTERISTIC)
            ?: return fail("FFF2 is not registered yet")
        val sent = guard("notifyCharacteristicChanged") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                s.notifyCharacteristicChanged(device, rx, false, frame) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                rx.value = frame
                @Suppress("DEPRECATION")
                s.notifyCharacteristicChanged(device, rx, false)
            }
        }
        return if (sent == true) true else fail("notification was not sent")
    }

    /**
     * Asks the stack to drop the link to the central, as when the bike goes out of range, and returns its address.
     * cancelConnection only releases links this app holds, and an incoming link has no app holder, so connect()
     * first registers the server as a holder. The drop itself is reported by the STATE_DISCONNECTED callback.
     */
    fun disconnect(): String? {
        val s = server ?: return null.also { fail("GATT server is not open") }
        val device = central ?: return null.also { fail("no central connected") }
        if (guard("connect") { s.connect(device, false) } != true) Log.w(TAG, "connect() before cancelConnection refused")
        guard("cancelConnection") { s.cancelConnection(device) } ?: return null
        events.onEvent("Disconnect of ${device.address} requested")
        return device.address
    }

    internal fun addNextService() {
        val next = synchronized(lock) { pending.removeFirstOrNull() } ?: return
        val s = server ?: return
        val accepted = guard("addService") { s.addService(next) }
        if (accepted != true) fail("service ${next.uuid.toString().substring(4, 8)} was not added")
    }

    internal fun respond(device: BluetoothDevice, requestId: Int, status: Int, offset: Int, value: ByteArray?) {
        val s = server ?: return
        guard("sendResponse") { s.sendResponse(device, requestId, status, offset, value) }
    }

    private fun fail(reason: String): Boolean {
        events.onEvent(reason, error = true)
        return false
    }

    /** Runs a Bluetooth call; a refused permission is logged and reported, and null is returned. */
    private fun <T> guard(call: String, block: () -> T): T? = try {
        block()
    } catch (e: SecurityException) {
        Log.w(TAG, "GATT server call $call refused", e)
        events.onEvent("$call refused: ${e.message ?: "permission"}", error = true)
        null
    } catch (e: IllegalStateException) {
        Log.w(TAG, "GATT server call $call failed", e)
        events.onEvent("$call failed: ${e.message ?: "Bluetooth not ready"}", error = true)
        null
    }
}
