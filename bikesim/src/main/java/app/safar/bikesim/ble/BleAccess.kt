package app.safar.bikesim.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

internal const val TAG = "BikeSim"

/** Runtime permissions the simulator needs. API 26-30 use install-time permissions only. */
object SimPermissions {
    val required: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            emptyArray()
        }

    fun granted(context: Context): Boolean = required.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

internal fun bluetoothManager(context: Context): BluetoothManager? =
    context.getSystemService(BluetoothManager::class.java)

internal fun bluetoothAdapter(context: Context): BluetoothAdapter? = bluetoothManager(context)?.adapter

/** Bluetooth on, false on a device without Bluetooth. */
fun isBluetoothOn(context: Context): Boolean = bluetoothAdapter(context)?.isEnabled == true

/** Everything the peripheral reports. Calls arrive on Binder threads. */
interface PeripheralEvents {
    fun onAdvertising(on: Boolean)
    fun onCentral(address: String?)
    fun onMtu(mtu: Int)
    fun onNotify(enabled: Boolean)
    fun onFrameReceived(bytes: ByteArray)
    fun onEvent(text: String, error: Boolean = false)
}
