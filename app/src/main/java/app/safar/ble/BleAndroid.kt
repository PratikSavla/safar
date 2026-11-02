package app.safar.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

internal const val TAG = "SafarBle"

/** Runtime permission checks before every BLE call (docs 2.2 step 1). Never throws. */
object BlePermissions {
    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun canScan(context: Context): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        granted(context, Manifest.permission.BLUETOOTH_SCAN)
    } else {
        granted(context, Manifest.permission.ACCESS_FINE_LOCATION)
    }

    /** API 31+: BLUETOOTH_CONNECT. Below, the install-time BLUETOOTH permission is enough. */
    fun canConnect(context: Context): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        granted(context, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        true
    }

    /** Name of the permission that blocks scanning, for the log. */
    fun scanPermissionName(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "BLUETOOTH_SCAN" else "ACCESS_FINE_LOCATION"
}

internal fun bluetoothAdapter(context: Context): BluetoothAdapter? =
    context.getSystemService(BluetoothManager::class.java)?.adapter

/** Bluetooth adapter on/off. [isOn] is false on a phone without Bluetooth. */
class BluetoothMonitor(private val context: Context) {
    private var receiver: BroadcastReceiver? = null

    val isOn: Boolean get() = bluetoothAdapter(context)?.isEnabled == true

    /** [onChange] runs on the main thread: true for STATE_ON, false for STATE_TURNING_OFF / STATE_OFF. */
    fun register(onChange: (Boolean) -> Unit) {
        if (receiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                    BluetoothAdapter.STATE_ON -> onChange(true)
                    BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_OFF -> onChange(false)
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            r,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiver = r
    }
}
