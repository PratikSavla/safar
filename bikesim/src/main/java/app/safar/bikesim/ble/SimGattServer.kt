package app.safar.bikesim.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.util.Log
import app.safar.protocol.GattUuids
import java.util.concurrent.ConcurrentHashMap

/**
 * The GATT server of the simulated cluster. Frames go to every device that enabled notify on FFF2; the screen shows
 * one central, chosen by [CentralTracker]. Every Bluetooth call checks the runtime permission and catches
 * [SecurityException].
 */
@SuppressLint("MissingPermission") // SimPermissions.granted() is checked in open() and every call is guarded.
class SimGattServer(private val context: Context, private val events: PeripheralEvents) {
    @Volatile private var server: BluetoothGattServer? = null

    private val tracker = CentralTracker()
    private val devices = ConcurrentHashMap<String, BluetoothDevice>()
    private val mtus = ConcurrentHashMap<String, Int>()

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
        tracker.clear()
        devices.clear()
        mtus.clear()
        guard("close") { s.clearServices(); s.close() }
        events.onEvent("GATT server closed")
    }

    /** Sends [frame] on FFF2 to every subscriber. False, with an event, when nobody listens or no send succeeds. */
    fun notify(frame: ByteArray): Boolean {
        val s = server ?: return fail("GATT server is not open")
        val targets = tracker.subscribers().mapNotNull { devices[it] }
        if (targets.isEmpty()) {
            return fail(if (tracker.central == null) "no central connected" else "the central has not enabled notify")
        }
        val rx = s.getService(GattUuids.SERVICE)?.getCharacteristic(GattUuids.RX_CHARACTERISTIC)
            ?: return fail("FFF2 is not registered yet")
        val sent = targets.count { notifyOne(s, rx, it, frame) }
        return if (sent > 0) true else fail("notification was not sent")
    }

    private fun notifyOne(
        s: BluetoothGattServer,
        rx: BluetoothGattCharacteristic,
        device: BluetoothDevice,
        frame: ByteArray,
    ): Boolean {
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
        if (sent != true) Log.w(TAG, "Notification to ${device.address} was not sent")
        return sent == true
    }

    /**
     * Asks the stack to drop the link to the central, as when the bike goes out of range, and returns its address.
     * cancelConnection only releases links this app holds, and an incoming link has no app holder, so connect()
     * first registers the server as a holder. The drop itself is reported by the STATE_DISCONNECTED callback.
     */
    fun disconnect(): String? {
        val s = server ?: return null.also { fail("GATT server is not open") }
        val device = tracker.central?.let { devices[it] } ?: return null.also { fail("no central connected") }
        if (guard("connect") { s.connect(device, false) } != true) Log.w(TAG, "connect() before cancelConnection refused")
        guard("cancelConnection") { s.cancelConnection(device) } ?: return null
        events.onEvent("Disconnect of ${device.address} requested")
        return device.address
    }

    internal fun onLinkUp(device: BluetoothDevice) {
        devices[device.address] = device
        val change = tracker.connected(device.address)
        if (!change.centralChanged) events.onEvent("LE device ${device.address} connected; not the central")
        publish(change)
    }

    internal fun onLinkDown(device: BluetoothDevice, status: Int) {
        val wasCentral = tracker.central == device.address
        devices.remove(device.address)
        mtus.remove(device.address)
        val change = tracker.disconnected(device.address)
        events.onEvent("${if (wasCentral) "Central" else "LE device"} ${device.address} disconnected (status $status)")
        publish(change)
    }

    internal fun onCccd(device: BluetoothDevice, enabled: Boolean) {
        devices[device.address] = device
        publish(tracker.cccd(device.address, enabled))
    }

    internal fun onMtu(device: BluetoothDevice, mtu: Int) {
        mtus[device.address] = mtu
        if (tracker.central == device.address) events.onMtu(mtu)
    }

    internal fun isSubscribed(device: BluetoothDevice): Boolean = tracker.isSubscribed(device.address)

    /** Notify first, so the screen keeps notify on when the central moves to another subscriber. */
    private fun publish(change: CentralTracker.Change) {
        if (change.notifyChanged) events.onNotify(change.notifyOn)
        if (!change.centralChanged) return
        events.onCentral(change.central)
        change.central?.let { mtus[it] }?.let(events::onMtu)
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
