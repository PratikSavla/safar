package app.safar.bikesim.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import app.safar.protocol.GattUuids
import java.io.ByteArrayOutputStream

/** Answers the central's requests: FFF1 writes, the FFF2 CCCD and Device Information reads. */
internal class SimGattCallback(
    private val owner: SimGattServer,
    private val events: PeripheralEvents,
) : BluetoothGattServerCallback() {
    /** Long (prepared) writes to FFF1, joined on execute. Only needed when the MTU is below a frame. */
    private val prepared = ByteArrayOutputStream()

    override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
        when (newState) {
            BluetoothProfile.STATE_CONNECTED -> owner.onLinkUp(device)
            BluetoothProfile.STATE_DISCONNECTED -> owner.onLinkDown(device, status)
        }
    }

    override fun onServiceAdded(status: Int, service: BluetoothGattService) {
        val id = service.uuid.toString().substring(4, 8).uppercase()
        if (status == BluetoothGatt.GATT_SUCCESS) {
            events.onEvent("Service $id ready")
        } else {
            events.onEvent("Service $id failed with status $status", error = true)
        }
        owner.addNextService()
    }

    override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
        owner.onMtu(device, mtu)
    }

    override fun onCharacteristicReadRequest(
        device: BluetoothDevice,
        requestId: Int,
        offset: Int,
        characteristic: BluetoothGattCharacteristic,
    ) {
        val value = SimServices.DEVICE_INFO[characteristic.uuid]
        when {
            value == null -> owner.respond(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, null)
            offset > value.size -> owner.respond(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, null)
            else -> owner.respond(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value.copyOfRange(offset, value.size))
        }
    }

    override fun onCharacteristicWriteRequest(
        device: BluetoothDevice,
        requestId: Int,
        characteristic: BluetoothGattCharacteristic,
        preparedWrite: Boolean,
        responseNeeded: Boolean,
        offset: Int,
        value: ByteArray,
    ) {
        if (characteristic.uuid != GattUuids.TX_CHARACTERISTIC) {
            if (responseNeeded) owner.respond(device, requestId, BluetoothGatt.GATT_WRITE_NOT_PERMITTED, offset, null)
            return
        }
        if (preparedWrite) {
            synchronized(prepared) { prepared.write(value) }
        } else {
            events.onFrameReceived(value)
        }
        if (responseNeeded) owner.respond(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
    }

    override fun onExecuteWrite(device: BluetoothDevice, requestId: Int, execute: Boolean) {
        val joined = synchronized(prepared) { prepared.toByteArray().also { prepared.reset() } }
        if (execute && joined.isNotEmpty()) events.onFrameReceived(joined)
        owner.respond(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
    }

    override fun onDescriptorReadRequest(
        device: BluetoothDevice,
        requestId: Int,
        offset: Int,
        descriptor: BluetoothGattDescriptor,
    ) {
        val value = if (owner.isSubscribed(device)) {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        } else {
            BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
        }
        owner.respond(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, value)
    }

    override fun onDescriptorWriteRequest(
        device: BluetoothDevice,
        requestId: Int,
        descriptor: BluetoothGattDescriptor,
        preparedWrite: Boolean,
        responseNeeded: Boolean,
        offset: Int,
        value: ByteArray,
    ) {
        if (descriptor.uuid != GattUuids.CCCD) {
            if (responseNeeded) owner.respond(device, requestId, BluetoothGatt.GATT_WRITE_NOT_PERMITTED, offset, null)
            return
        }
        val enabled = value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        owner.onCccd(device, enabled)
        if (responseNeeded) owner.respond(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
    }

    override fun onNotificationSent(device: BluetoothDevice, status: Int) {
        if (status != BluetoothGatt.GATT_SUCCESS) events.onEvent("Notification failed with status $status", error = true)
    }
}
