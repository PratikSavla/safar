package app.safar.ble

import app.safar.core.ScannedBike
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/**
 * What [BikeManager] needs from the phone's Bluetooth: adapter state, a scanner and GATT links.
 * [AndroidBleRadio] is the app's implementation; JVM tests pass a simulated bike instead.
 */
interface BleRadio {
    val bluetooth: BluetoothSwitch

    /** True when the app may connect (BLUETOOTH_CONNECT granted on API 31+). */
    fun canConnect(): Boolean

    /** Null when the app may scan, else the missing permission (BLUETOOTH_SCAN, or location below API 31). */
    fun missingScanPermission(): String?

    fun scanner(scope: CoroutineScope): BikeScanner

    /** A new, unconnected link to [address], or null when the address cannot be used (logged). */
    fun open(address: String): GattPort?
}

/** Bluetooth adapter on/off. */
interface BluetoothSwitch {
    val isOn: Boolean

    /** [onChange] gets true when the adapter turns on, false when it turns off. */
    fun register(onChange: (Boolean) -> Unit)
}

/** The cluster scan (docs 2.2 step 2). */
interface BikeScanner {
    val results: StateFlow<List<ScannedBike>>
    val scanning: StateFlow<Boolean>
    fun nameOf(address: String): String?

    /** Starts a new scan. Returns false (and logs why) when it cannot scan. */
    fun start(): Boolean
    fun stop()
}

/**
 * One GATT link with suspend operations, addressed by service and characteristic UUID. Operations run one at
 * a time and return null / false / a failed outcome when the link is gone. RX notifications arrive in
 * [notifications].
 */
interface GattPort {
    val link: StateFlow<GattLink>
    val notifications: ReceiveChannel<ByteArray>

    /** Advertised name of the device, or null when unknown. */
    val deviceName: String?

    suspend fun connect(timeoutMs: Long): Boolean
    suspend fun discoverServices(): Boolean

    /** Returns the MTU the device accepted, or null on failure. */
    suspend fun requestMtu(mtu: Int): Int?

    /** Whether discovery found [characteristic] in [service]; a null [characteristic] asks for the service. */
    fun has(service: UUID, characteristic: UUID? = null): Boolean

    /** Notification on [characteristic] with a CCCD write of 01 00. */
    suspend fun enableNotify(service: UUID, characteristic: UUID): Boolean
    suspend fun read(service: UUID, characteristic: UUID): ByteArray?

    /** One write with response; suspends until the device acknowledged it. */
    suspend fun write(service: UUID, characteristic: UUID, frame: ByteArray): WriteOutcome

    /** Marks the link lost and fails the waiting operation. */
    fun markLost(status: Int, reason: String)

    /** Disconnects and releases the link. Safe to call more than once. */
    fun close()
}
