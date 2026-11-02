package app.safar.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.util.Log
import app.safar.core.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/** [BleRadio] on the Android Bluetooth stack: [BluetoothMonitor], [BleScanner] and [GattClient]. */
class AndroidBleRadio(private val context: Context, private val log: DebugLog) : BleRadio {
    override val bluetooth: BluetoothSwitch = BluetoothMonitor(context)

    override fun canConnect(): Boolean = BlePermissions.canConnect(context)

    override fun missingScanPermission(): String? =
        if (BlePermissions.canScan(context)) null else BlePermissions.scanPermissionName()

    override fun scanner(scope: CoroutineScope): BikeScanner = BleScanner(context, log, scope)

    override fun open(address: String): GattPort? {
        val adapter = bluetoothAdapter(context)
        if (adapter == null) {
            log.event("No Bluetooth adapter", error = true)
            return null
        }
        val device = try {
            adapter.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Saved bike address is not a valid MAC", e)
            log.event("Invalid bike address", mapOf("address" to address), error = true)
            return null
        }
        return AndroidGattPort(context, device, GattClient(context, device, log))
    }
}

/** [GattPort] over one [GattClient]; looks the characteristics up in the discovered services. */
internal class AndroidGattPort(
    private val context: Context,
    private val device: BluetoothDevice,
    private val client: GattClient,
) : GattPort {
    override val link: StateFlow<GattLink> get() = client.link
    override val notifications: ReceiveChannel<ByteArray> get() = client.notifications

    @get:SuppressLint("MissingPermission") // canConnect() checked first.
    override val deviceName: String?
        get() {
            if (!BlePermissions.canConnect(context)) return null
            return try {
                device.name
            } catch (e: SecurityException) {
                Log.w(TAG, "Reading the bike name was refused", e)
                null
            }
        }

    override suspend fun connect(timeoutMs: Long): Boolean = client.connect(timeoutMs)

    override suspend fun discoverServices(): Boolean = client.discoverServices()

    override suspend fun requestMtu(mtu: Int): Int? = client.requestMtu(mtu)

    private fun characteristic(service: UUID, characteristic: UUID): BluetoothGattCharacteristic? =
        client.service(service)?.getCharacteristic(characteristic)

    override fun has(service: UUID, characteristic: UUID?): Boolean =
        if (characteristic == null) client.service(service) != null else characteristic(service, characteristic) != null

    override suspend fun enableNotify(service: UUID, characteristic: UUID): Boolean =
        characteristic(service, characteristic)?.let { client.enableNotify(it) } ?: false

    override suspend fun read(service: UUID, characteristic: UUID): ByteArray? =
        characteristic(service, characteristic)?.let { client.read(it) }

    override suspend fun write(service: UUID, characteristic: UUID, frame: ByteArray): WriteOutcome {
        val target = characteristic(service, characteristic) ?: return WriteOutcome.Failed("characteristic not found")
        return client.write(target, frame)
    }

    override fun markLost(status: Int, reason: String) = client.markLost(status, reason)

    override fun close() = client.close()
}
