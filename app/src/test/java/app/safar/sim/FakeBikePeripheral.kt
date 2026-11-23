package app.safar.sim

import app.safar.ble.GattCharInfo
import app.safar.ble.GattServiceInfo
import app.safar.protocol.BIKE_MTU
import app.safar.protocol.ChecksumMode
import app.safar.protocol.DeviceInformationUuids
import app.safar.protocol.GattProperty
import app.safar.protocol.GattUuids
import app.safar.protocol.Timing
import app.safar.protocol.checksumModeFor
import app.safar.protocol.uuid16
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/** One 30-byte frame the app wrote, with the virtual time of the write and of its acknowledgement. */
class WrittenFrame(val atMillis: Long, val bytes: ByteArray) {
    val type: Char get() = (bytes[1].toInt() and 0xFF).toChar()

    /** Null while the write waits for its ack. */
    var ackedAtMillis: Long? = null
        internal set
}

/**
 * A Suzuki cluster at the GATT level. It accepts connections while powered on, has Generic Access, Generic
 * Attribute, Device Information 0x180A and the frame service (by default FFF0 with FFF1 write and FFF2 notify,
 * as the V-Strom SX; [clusterService], [txUuid], [rxUuid] and [txProperties] change it), answers an MTU request
 * with [mtu] (or refuses it, [refusesMtu]), acknowledges each write after
 * [ackDelayMs] and records it, and notifies a cluster frame '7' [firstClusterDelayMs] after the CCCD write and
 * then every [Timing.CLUSTER_PERIOD_MS]. Times come from [clock] (the test scheduler). Not thread-safe: the
 * tests run everything on one test dispatcher.
 */
class FakeBikePeripheral(
    val name: String,
    address: String,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
) {
    val mode: ChecksumMode = checksumModeFor(name)

    /** The address it advertises from now; [rotateAddress] changes it like a resolvable private address. */
    var address: String = address
        private set

    /** Addresses it no longer answers on; a connect to one of them times out. */
    val formerAddresses = mutableListOf<String>()
    var poweredOn = true
    var mtu = BIKE_MTU
    var cluster = ClusterSpec()
    var connectDelayMs = 120L
    var opDelayMs = 10L
    var ackDelayMs = 20L
    var firstClusterDelayMs = 300L

    /** The frame service and its characteristics; TX lists first and RX second, as on the V-Strom SX. */
    var clusterService: UUID = GattUuids.SERVICE
    var txUuid: UUID = GattUuids.TX_CHARACTERISTIC
    var rxUuid: UUID = GattUuids.RX_CHARACTERISTIC
    var txProperties: Int = GattProperty.WRITE

    /** Every write is answered with ATT error "Write Not Permitted" and never reaches the cluster. */
    var rejectsWrites = false

    /** The MTU exchange fails (the cluster does not support it). */
    var refusesMtu = false

    /** Every Device Information read fails, as a cluster that does not answer it. */
    var deviceInfoFails = false

    /** Virtual times of every Device Information read the app tried. */
    val deviceInfoReads = mutableListOf<Long>()

    val deviceInfo: Map<UUID, ByteArray> = mapOf(
        DeviceInformationUuids.MANUFACTURER_NAME to "Suzuki\u0000".toByteArray(),
        DeviceInformationUuids.MODEL_NUMBER to name.substring(1, 5).toByteArray(),
        DeviceInformationUuids.SERIAL_NUMBER to "A1B2C3D".toByteArray(),
        DeviceInformationUuids.FIRMWARE_REVISION to "1.0.4".toByteArray(),
        DeviceInformationUuids.SOFTWARE_REVISION to "2.1".toByteArray(),
        DeviceInformationUuids.PNP_ID to byteArrayOf(0x01, 0x0D, 0x00, 0x00, 0x01, 0x01, 0x00),
    )

    /** Every frame written to FFF1, oldest first, recorded when the write arrives. */
    val writes = mutableListOf<WrittenFrame>()

    /** Virtual times of every connection request, including refused ones. */
    val connectRequests = mutableListOf<Long>()

    /** Virtual times of the CCCD writes (01 00) on FFF2. */
    val notifyEnabledAt = mutableListOf<Long>()

    /** Highest number of writes in flight at once; 1 means every write waited for the previous ack. */
    var maxWritesInFlight = 0
        private set

    /** Frames notified to the app, valid or not. */
    val notified = mutableListOf<ByteArray>()

    private var inFlight = 0
    private var current: SimGattLink? = null
    private var clusterJob: Job? = null

    val connected: Boolean get() = current != null

    /** The GATT table in discovery order: 1800, 1801, 180A, then the frame service (the 4th, docs 2.2). */
    fun services(): List<GattServiceInfo> = listOf(
        GattServiceInfo(uuid16(0x1800), listOf(GattCharInfo(uuid16(0x2A00), GattProperty.READ))),
        GattServiceInfo(uuid16(0x1801), emptyList()),
        GattServiceInfo(DeviceInformationUuids.SERVICE, deviceInfo.keys.map { GattCharInfo(it, GattProperty.READ) }),
        GattServiceInfo(clusterService, listOf(GattCharInfo(txUuid, txProperties), GattCharInfo(rxUuid, GattProperty.NOTIFY))),
    )

    fun has(service: UUID, characteristic: UUID?): Boolean = services().any { s ->
        s.uuid == service && (characteristic == null || s.characteristics.any { it.uuid == characteristic })
    }

    /** Whether TX takes a write request (with response); a cluster whose TX is write-without-response only does not. */
    val acceptsWriteRequests: Boolean get() = txProperties and GattProperty.WRITE != 0

    /** The current cluster frame with this bike's checksum mode. */
    fun clusterFrame(): ByteArray = cluster.encode(mode)

    /** Notifies [frame] on FFF2 now, as an extra or corrupt frame. */
    fun inject(frame: ByteArray) {
        val link = current ?: error("inject needs a connected link")
        notify(link, frame)
    }

    /** The link drops from the bike side (default status 8, supervision timeout). */
    fun dropLink(status: Int = SUPERVISION_TIMEOUT) {
        current?.lostByBike(status)
    }

    /** Ignition off: the link drops and new connections time out. */
    fun powerOff() {
        poweredOn = false
        dropLink()
    }

    fun powerOn() {
        poweredOn = true
    }

    /** Advertises from [newAddress] from now on; the old address stops answering. */
    fun rotateAddress(newAddress: String) {
        formerAddresses += address
        address = newAddress
    }

    internal fun onDeviceInfoRead() {
        deviceInfoReads += clock()
    }

    internal fun onConnectRequest() {
        connectRequests += clock()
    }

    internal fun attach(link: SimGattLink) {
        current = link
    }

    internal fun detach(link: SimGattLink) {
        if (current !== link) return
        current = null
        clusterJob?.cancel()
        clusterJob = null
    }

    internal fun onNotifyEnabled(link: SimGattLink) {
        notifyEnabledAt += clock()
        clusterJob?.cancel()
        clusterJob = scope.launch {
            delay(firstClusterDelayMs)
            while (current === link) {
                notify(link, clusterFrame())
                delay(Timing.CLUSTER_PERIOD_MS)
            }
        }
    }

    /** Records the write and calls [onAck] after [ackDelayMs]; no ack once [link] is gone. */
    internal fun onWrite(link: SimGattLink, characteristic: UUID, frame: ByteArray, onAck: () -> Unit) {
        check(current === link) { "write on a link the bike does not hold" }
        require(characteristic == txUuid) { "writes go to TX $txUuid, got $characteristic" }
        inFlight++
        maxWritesInFlight = maxOf(maxWritesInFlight, inFlight)
        val written = WrittenFrame(clock(), frame.copyOf())
        writes += written
        scope.launch {
            try {
                delay(ackDelayMs)
                if (current !== link) return@launch
                written.ackedAtMillis = clock()
                onAck()
            } finally {
                inFlight--
            }
        }
    }

    private fun notify(link: SimGattLink, frame: ByteArray) {
        notified += frame.copyOf()
        link.deliver(frame)
    }

    companion object {
        const val SUPERVISION_TIMEOUT = 0x08
    }
}
