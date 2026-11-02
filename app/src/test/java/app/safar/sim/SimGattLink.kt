package app.safar.sim

import app.safar.ble.GattClient
import app.safar.ble.GattLink
import app.safar.ble.GattPort
import app.safar.ble.WriteOutcome
import app.safar.ble.gattStatusText
import app.safar.core.DebugLog
import app.safar.protocol.GattUuids
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * One app-side GATT link to a [FakeBikePeripheral]: what GattClient and its callback bridge do on a phone,
 * including the "Disconnected" log event the bridge writes when the bike drops the link. As in GattClient, a
 * write waits for the bike's ack, and [markLost] fails the waiting write at once with status -1.
 */
class SimGattLink(private val bike: FakeBikePeripheral, private val log: DebugLog) : GattPort {
    private val state = MutableStateFlow<GattLink>(GattLink.Connecting)
    override val link: StateFlow<GattLink> = state.asStateFlow()

    private val rx = Channel<ByteArray>(NOTIFY_BUFFER, BufferOverflow.DROP_OLDEST)
    override val notifications: ReceiveChannel<ByteArray> get() = rx

    override val deviceName: String get() = bike.name

    var closed = false
        private set

    private val isUp: Boolean get() = state.value == GattLink.Connected

    /** The write waiting for its ack, like GattClient's pending operation. */
    private var pendingWrite: CompletableDeferred<WriteOutcome>? = null

    override suspend fun connect(timeoutMs: Long): Boolean {
        bike.onConnectRequest()
        if (!bike.poweredOn) {
            delay(timeoutMs)
            markLost(GattClient.STATUS_LOCAL, "connect timeout after ${timeoutMs / 1000} s")
            return false
        }
        delay(bike.connectDelayMs)
        if (state.value != GattLink.Connecting) return false
        state.value = GattLink.Connected
        bike.attach(this)
        return true
    }

    override suspend fun discoverServices(): Boolean {
        delay(bike.opDelayMs)
        return isUp
    }

    override suspend fun requestMtu(mtu: Int): Int? {
        delay(bike.opDelayMs)
        return if (isUp) minOf(mtu, bike.mtu) else null
    }

    override fun has(service: UUID, characteristic: UUID?): Boolean = isUp && bike.has(service, characteristic)

    override suspend fun enableNotify(service: UUID, characteristic: UUID): Boolean {
        delay(bike.opDelayMs)
        if (!isUp || service != GattUuids.SERVICE || characteristic != GattUuids.RX_CHARACTERISTIC) return false
        bike.onNotifyEnabled(this)
        return true
    }

    override suspend fun read(service: UUID, characteristic: UUID): ByteArray? {
        delay(bike.opDelayMs)
        return if (isUp && bike.has(service, characteristic)) bike.deviceInfo[characteristic]?.copyOf() else null
    }

    override suspend fun write(service: UUID, characteristic: UUID, frame: ByteArray): WriteOutcome {
        if (!isUp || service != GattUuids.SERVICE) return WriteOutcome.Failed("not connected")
        val result = CompletableDeferred<WriteOutcome>()
        pendingWrite = result
        try {
            bike.onWrite(this, characteristic, frame) { result.complete(WriteOutcome.Ok) }
            return result.await()
        } finally {
            pendingWrite = null
        }
    }

    /** As GattClient.markLost: the waiting write gets STATUS_LOCAL, whatever [status] the link ended with. */
    override fun markLost(status: Int, reason: String) {
        if (state.value is GattLink.Disconnected) return
        state.value = GattLink.Disconnected(status, reason)
        bike.detach(this)
        pendingWrite?.complete(WriteOutcome.Failed("status ${GattClient.STATUS_LOCAL}"))
    }

    override fun close() {
        markLost(GattClient.STATUS_LOCAL, "closed by app")
        rx.close()
        closed = true
    }

    internal fun deliver(frame: ByteArray) {
        if (isUp) rx.trySend(frame.copyOf())
    }

    /** onConnectionStateChange(status, STATE_DISCONNECTED) as the callback bridge handles it. */
    internal fun lostByBike(status: Int) {
        val reason = gattStatusText(status)
        log.event("Disconnected", mapOf("status" to status.toString(), "reason" to reason), error = true)
        markLost(status, reason)
    }

    private companion object {
        const val NOTIFY_BUFFER = 64
    }
}
