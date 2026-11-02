package app.safar.bikesim

import android.bluetooth.BluetoothAdapter
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.safar.bikesim.ble.SimPermissions
import app.safar.bikesim.ble.TAG
import app.safar.bikesim.ble.isBluetoothOn
import app.safar.bikesim.ui.ScreenActions
import app.safar.bikesim.ui.SimScreen
import app.safar.bikesim.ui.SimTheme
import app.safar.bikesim.ui.SimViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The one screen of the simulator. Keeps the screen on while the simulator is switched on. */
class MainActivity : ComponentActivity() {
    private val vm: SimViewModel by viewModels()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refreshAccess() }

    private val enableLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refreshAccess() }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refreshAccess()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val actions = ScreenActions(::requestPermissions, ::enableBluetooth, ::copyLog)
        setContent { SimTheme { SimScreen(vm, actions) } }
        keepScreenOnWhileAdvertising()
        if (!SimPermissions.granted(this)) requestPermissions()
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            bluetoothReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        refreshAccess()
    }

    override fun onStop() {
        unregisterReceiver(bluetoothReceiver)
        super.onStop()
    }

    private fun refreshAccess() = vm.onAccess(SimPermissions.granted(this), isBluetoothOn(this))

    private fun requestPermissions() {
        if (SimPermissions.required.isEmpty()) refreshAccess() else permissionLauncher.launch(SimPermissions.required)
    }

    private fun enableBluetooth() {
        try {
            enableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No Bluetooth enable dialog; opening Bluetooth settings", e)
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        } catch (e: SecurityException) {
            Log.w(TAG, "Bluetooth enable request refused; opening Bluetooth settings", e)
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }
    }

    private fun copyLog() {
        val copied = vm.copyLogAsHex()
        Toast.makeText(this, if (copied) "Received frames copied as hex" else "Clipboard not available", Toast.LENGTH_SHORT).show()
    }

    /** FLAG_KEEP_SCREEN_ON while the simulator is on, instead of a foreground service. */
    private fun keepScreenOnWhileAdvertising() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.map { it.wanted }.distinctUntilChanged().collect { on ->
                    if (on) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
            }
        }
    }
}
