package app.safar.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.os.Build
import app.safar.core.DebugLog
import app.safar.protocol.GattUuids

/** Text for common GATT / HCI disconnect statuses, for the log (docs 12.6 "disconnect with reason"). */
fun gattStatusText(status: Int): String = when (status) {
    BluetoothGatt.GATT_SUCCESS -> "success"
    GattClient.STATUS_LOCAL -> "local"
    0x08 -> "supervision timeout"
    0x13 -> "remote user terminated"
    0x16 -> "terminated by local host"
    0x22 -> "LMP response timeout"
    0x3E -> "failed to establish"
    0x85 -> "GATT_ERROR"
    0x93 -> "connection timeout"
    else -> "status $status"
}

/**
 * Forwards [BluetoothGattCallback] events (binder threads) to [client]. API 33 added callbacks that carry the
 * value; on API 33+ the framework calls those, so the deprecated ones only act below API 33.
 */
internal class GattCallbackBridge(
    private val client: GattClient,
    private val log: DebugLog,
) : BluetoothGattCallback() {

    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
            client.linkState.compareAndSet(GattLink.Connecting, GattLink.Connected)
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
            val reason = gattStatusText(status)
            log.event("Disconnected", mapOf("status" to status.toString(), "reason" to reason), error = true)
            client.markLost(status, reason)
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        log.event("Services discovered", mapOf("status" to status.toString(), "count" to "${gatt.services.size}"))
        client.complete(GattClient.OpKind.DISCOVER, null, GattClient.OpResult(status))
    }

    override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
        client.complete(GattClient.OpKind.MTU, null, GattClient.OpResult(status, mtu = mtu))
    }

    override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
        client.complete(GattClient.OpKind.DESCRIPTOR, descriptor.uuid, GattClient.OpResult(status))
    }

    override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
        client.complete(GattClient.OpKind.WRITE, characteristic.uuid, GattClient.OpResult(status))
    }

    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int,
    ) {
        client.complete(GattClient.OpKind.READ, characteristic.uuid, GattClient.OpResult(status, value.copyOf()))
    }

    @Deprecated("Below API 33 only")
    override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
        @Suppress("DEPRECATION")
        val value = characteristic.value?.copyOf()
        client.complete(GattClient.OpKind.READ, characteristic.uuid, GattClient.OpResult(status, value))
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
    ) {
        if (characteristic.uuid == GattUuids.RX_CHARACTERISTIC) client.rx.trySend(value.copyOf())
    }

    @Deprecated("Below API 33 only")
    override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
        if (characteristic.uuid != GattUuids.RX_CHARACTERISTIC) return
        @Suppress("DEPRECATION")
        characteristic.value?.copyOf()?.let { client.rx.trySend(it) }
    }
}
