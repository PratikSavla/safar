package app.safar.bikesim.ble

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import app.safar.protocol.DeviceInformationUuids
import app.safar.protocol.GattUuids
import java.util.UUID

/** GATT layout of the simulated cluster: service FFF0 (docs 2.1) and Device Information 0x180A. */
object SimServices {
    /** Synthetic Device Information values. PnP ID: Bluetooth SIG source, vendor 0xFFFF, product 1, version 1.0. */
    val DEVICE_INFO: Map<UUID, ByteArray> = linkedMapOf(
        DeviceInformationUuids.MANUFACTURER_NAME to "Safar BikeSim".toByteArray(),
        DeviceInformationUuids.MODEL_NUMBER to "SIM-01".toByteArray(),
        DeviceInformationUuids.SERIAL_NUMBER to "SIM0000001".toByteArray(),
        DeviceInformationUuids.FIRMWARE_REVISION to "1.0.0".toByteArray(),
        DeviceInformationUuids.HARDWARE_REVISION to "A".toByteArray(),
        DeviceInformationUuids.SOFTWARE_REVISION to "1.0".toByteArray(),
        DeviceInformationUuids.PNP_ID to byteArrayOf(0x01, 0xFF.toByte(), 0xFF.toByte(), 0x01, 0x00, 0x00, 0x01),
    )

    /** FFF1 write with response (app -> bike), FFF2 notify with its CCCD (bike -> app). */
    fun cluster(): BluetoothGattService {
        val service = BluetoothGattService(GattUuids.SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val tx = BluetoothGattCharacteristic(
            GattUuids.TX_CHARACTERISTIC,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE,
        )
        val rx = BluetoothGattCharacteristic(
            GattUuids.RX_CHARACTERISTIC,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            0,
        )
        val cccd = BluetoothGattDescriptor(
            GattUuids.CCCD,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE,
        )
        rx.addDescriptor(cccd)
        service.addCharacteristic(tx)
        service.addCharacteristic(rx)
        return service
    }

    /** Device Information 0x180A with every characteristic in [DEVICE_INFO], read only. */
    fun deviceInformation(): BluetoothGattService {
        val service = BluetoothGattService(DeviceInformationUuids.SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        for (uuid in DEVICE_INFO.keys) {
            service.addCharacteristic(
                BluetoothGattCharacteristic(
                    uuid,
                    BluetoothGattCharacteristic.PROPERTY_READ,
                    BluetoothGattCharacteristic.PERMISSION_READ,
                ),
            )
        }
        return service
    }
}
