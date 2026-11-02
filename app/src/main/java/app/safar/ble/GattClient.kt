package app.safar.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.util.Log
import app.safar.core.DebugLog
import app.safar.protocol.GattUuids
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/** Connection state of one [GattClient]. */
sealed interface GattLink {
    data object Connecting : GattLink
    data object Connected : GattLink
    data class Disconnected(val status: Int, val reason: String) : GattLink
}

/**
 * One BluetoothGatt with suspend functions instead of callbacks. GATT allows one operation at a time, so every
 * operation runs under [opLock] and waits for its callback (bridged by [GattCallbackBridge]). Every call checks
 * BLUETOOTH_CONNECT first and handles SecurityException. RX notifications arrive in [notifications].
 */
@SuppressLint("MissingPermission") // BlePermissions.canConnect() is checked in connect() and in every op.
class GattClient(
    private val context: Context,
    private val device: BluetoothDevice,
    private val log: DebugLog,
) {
    internal enum class OpKind { DISCOVER, MTU, DESCRIPTOR, READ, WRITE }

    internal class OpResult(val status: Int, val value: ByteArray? = null, val mtu: Int = 0)

    internal class PendingOp(val kind: OpKind, val uuid: UUID?, val result: CompletableDeferred<OpResult>)

    private val opLock = Mutex()

    @Volatile internal var pending: PendingOp? = null

    /**
     * An operation the stack started whose callback had not come when its caller gave up (a write past the
     * queue's hard limit, say). The stack still holds it, so the next operation waits for that callback first.
     */
    @Volatile private var orphan: PendingOp? = null

    @Volatile private var gatt: BluetoothGatt? = null

    internal val linkState = MutableStateFlow<GattLink>(GattLink.Connecting)
    val link: StateFlow<GattLink> = linkState.asStateFlow()

    internal val rx = Channel<ByteArray>(NOTIFY_BUFFER, BufferOverflow.DROP_OLDEST)
    val notifications: ReceiveChannel<ByteArray> get() = rx

    private val bridge = GattCallbackBridge(this, log)

    /** connectGatt(autoConnect = false, TRANSPORT_LE) and wait for STATE_CONNECTED (docs 2.2 step 4). */
    suspend fun connect(timeoutMs: Long): Boolean {
        if (!BlePermissions.canConnect(context)) {
            markLost(STATUS_LOCAL, "BLUETOOTH_CONNECT permission missing")
            return false
        }
        // The BluetoothGattConnectionSettings overload exists only on API 37+; this one works on API 26..37.
        @Suppress("DEPRECATION")
        val g = guarded("connectGatt") { device.connectGatt(context, false, bridge, BluetoothDevice.TRANSPORT_LE) }
        if (g == null) {
            markLost(STATUS_LOCAL, "connectGatt returned no GATT")
            return false
        }
        gatt = g
        val state = withTimeoutOrNull(timeoutMs) { link.first { it !is GattLink.Connecting } }
        if (state == null) markLost(STATUS_LOCAL, "connect timeout after ${timeoutMs / 1000} s")
        return state is GattLink.Connected
    }

    suspend fun discoverServices(): Boolean =
        op(OpKind.DISCOVER, null) { it.discoverServices() }?.status == BluetoothGatt.GATT_SUCCESS

    /** Returns the MTU the bike accepted, or null on failure. */
    suspend fun requestMtu(mtu: Int): Int? {
        val result = op(OpKind.MTU, null) { it.requestMtu(mtu) } ?: return null
        return if (result.status == BluetoothGatt.GATT_SUCCESS) result.mtu else null
    }

    fun service(uuid: UUID): BluetoothGattService? = gatt?.getService(uuid)

    /** setCharacteristicNotification + CCCD write of ENABLE_NOTIFICATION_VALUE (docs 2.2 step 7). */
    suspend fun enableNotify(characteristic: BluetoothGattCharacteristic): Boolean {
        val g = gatt ?: return false
        val cccd = characteristic.getDescriptor(GattUuids.CCCD) ?: return false
        if (guarded("setCharacteristicNotification") { g.setCharacteristicNotification(characteristic, true) } != true) {
            return false
        }
        val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        val result = op(OpKind.DESCRIPTOR, cccd.uuid) { gt ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gt.writeDescriptor(cccd, value) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                cccd.value = value
                @Suppress("DEPRECATION")
                gt.writeDescriptor(cccd)
            }
        }
        return result?.status == BluetoothGatt.GATT_SUCCESS
    }

    suspend fun read(characteristic: BluetoothGattCharacteristic): ByteArray? {
        val result = op(OpKind.READ, characteristic.uuid) { it.readCharacteristic(characteristic) } ?: return null
        return if (result.status == BluetoothGatt.GATT_SUCCESS) result.value else null
    }

    /**
     * One write with response (WRITE_TYPE_DEFAULT, docs 2.3); suspends until onCharacteristicWrite(). A write
     * the stack refuses to start (busy: API 33+ ERROR_GATT_WRITE_REQUEST_BUSY, older false) is [WriteOutcome.Busy].
     */
    suspend fun write(characteristic: BluetoothGattCharacteristic, frame: ByteArray): WriteOutcome {
        val type = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        var refused = "refused"
        val result = op(OpKind.WRITE, characteristic.uuid) { g ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val code = g.writeCharacteristic(characteristic, frame, type)
                val busy = code == BluetoothStatusCodes.ERROR_GATT_WRITE_REQUEST_BUSY
                refused = if (busy) "request busy" else "not started, code $code"
                code == BluetoothStatusCodes.SUCCESS
            } else {
                characteristic.writeType = type
                @Suppress("DEPRECATION")
                characteristic.value = frame
                @Suppress("DEPRECATION")
                g.writeCharacteristic(characteristic)
            }
        } ?: return WriteOutcome.Failed("not connected")
        return when (result.status) {
            BluetoothGatt.GATT_SUCCESS -> WriteOutcome.Ok
            STATUS_NOT_STARTED -> WriteOutcome.Busy(refused)
            STATUS_NO_CALLBACK -> WriteOutcome.TimedOut
            else -> WriteOutcome.Failed("GATT error status ${result.status}")
        }
    }

    /**
     * Runs one operation and waits for its callback. Null when the link is down; [STATUS_NOT_STARTED] when the
     * stack refused it; [STATUS_NO_CALLBACK] when no callback came (also for the previous [orphan]).
     */
    private suspend fun op(kind: OpKind, uuid: UUID?, start: (BluetoothGatt) -> Boolean): OpResult? =
        opLock.withLock {
            val g = gatt
            if (g == null || link.value != GattLink.Connected || !BlePermissions.canConnect(context)) return null
            if (!awaitOrphan()) return OpResult(STATUS_NO_CALLBACK)
            val deferred = CompletableDeferred<OpResult>()
            val op = PendingOp(kind, uuid, deferred)
            pending = op
            var started = false
            try {
                if (guarded(kind.name) { start(g) } != true) return OpResult(STATUS_NOT_STARTED)
                started = true
                withTimeoutOrNull(OP_TIMEOUT_MS) { deferred.await() } ?: run {
                    Log.w(TAG, "GATT ${kind.name} had no callback in $OP_TIMEOUT_MS ms")
                    OpResult(STATUS_NO_CALLBACK)
                }
            } finally {
                // Started but given up on (timeout or cancelled): keep it pending for its late callback.
                if (started && !deferred.isCompleted) orphan = op else pending = null
            }
        }

    /** Waits up to [OP_TIMEOUT_MS] for the [orphan]'s late callback; false when it still has not come. */
    private suspend fun awaitOrphan(): Boolean {
        val o = orphan ?: return true
        val result = withTimeoutOrNull(OP_TIMEOUT_MS) { o.result.await() } ?: return false
        Log.i(TAG, "Late GATT ${o.kind.name} callback, status ${result.status}")
        orphan = null
        pending = null
        return true
    }

    /** Completes the waiting operation when [kind] (and [uuid], if given) match. Called from GATT callbacks. */
    internal fun complete(kind: OpKind, uuid: UUID?, result: OpResult) {
        val p = pending ?: return
        if (p.kind == kind && (p.uuid == null || uuid == null || p.uuid == uuid)) p.result.complete(result)
    }

    /**
     * Marks the link lost (also used for a stalled write queue); fails the waiting operation. The operation gets
     * [STATUS_LOCAL], not [status]: a disconnect can report GATT_SUCCESS (0), which would read as success.
     */
    fun markLost(status: Int, reason: String) {
        if (linkState.value is GattLink.Disconnected) return
        linkState.value = GattLink.Disconnected(status, reason)
        pending?.result?.complete(OpResult(STATUS_LOCAL))
    }

    /** disconnect() + close() (docs 2.5). Safe to call more than once. */
    fun close() {
        markLost(STATUS_LOCAL, "closed by app")
        val g = gatt ?: return
        gatt = null
        rx.close()
        if (!BlePermissions.canConnect(context)) {
            Log.w(TAG, "Closing GATT without BLUETOOTH_CONNECT; the system releases it with the process")
            return
        }
        guarded("disconnect") { g.disconnect() }
        guarded("close") { g.close() }
    }

    private inline fun <T> guarded(what: String, call: () -> T): T? = try {
        call()
    } catch (e: SecurityException) {
        Log.w(TAG, "GATT $what refused", e)
        log.event("GATT call refused", mapOf("call" to what, "reason" to (e.message ?: "SecurityException")), error = true)
        null
    }

    companion object {
        /** Local reasons (not from the controller) use this status. */
        const val STATUS_LOCAL = -1

        /** The stack refused to start the operation (busy). */
        private const val STATUS_NOT_STARTED = -2

        /** The operation's callback did not come in time. */
        private const val STATUS_NO_CALLBACK = -3
        private const val OP_TIMEOUT_MS = 10_000L
        private const val NOTIFY_BUFFER = 64
    }
}
