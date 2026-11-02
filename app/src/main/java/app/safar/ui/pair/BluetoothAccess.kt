package app.safar.ui.pair

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.safar.ui.onboarding.PermId
import app.safar.ui.onboarding.SafarPermissions

private const val TAG = "SafarPair"

/** Whether a scan can start: permission first, then the radio. */
enum class BtAccess { READY, NEEDS_PERMISSION, BLUETOOTH_OFF, UNSUPPORTED }

fun btAccess(context: Context): BtAccess {
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return BtAccess.UNSUPPORTED
    if (!SafarPermissions.isGranted(context, PermId.NEARBY)) return BtAccess.NEEDS_PERMISSION
    return try {
        if (adapter.isEnabled) BtAccess.READY else BtAccess.BLUETOOTH_OFF
    } catch (e: SecurityException) {
        Log.w(TAG, "Reading the Bluetooth state was refused", e)
        BtAccess.NEEDS_PERMISSION
    }
}

/** [BtAccess], refreshed on resume and whenever the radio turns on or off. */
@Composable
fun rememberBtAccess(): State<BtAccess> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(btAccess(context)) }
    LifecycleResumeEffect(context) {
        state.value = btAccess(context)
        onPauseOrDispose { }
    }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                state.value = btAccess(context)
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    return state
}

/** Actions that fix [BtAccess]: ask for Nearby devices, or the system "turn on Bluetooth" dialog. */
class BtFixes(val requestPermission: () -> Unit, val enableBluetooth: () -> Unit)

/**
 * The Nearby devices request and ACTION_REQUEST_ENABLE. On Android 12+ the enable dialog needs
 * BLUETOOTH_CONNECT, so without it the permission is asked first. [onChanged] runs after either returns.
 */
@Composable
fun rememberBtFixes(onChanged: () -> Unit): BtFixes {
    val context = LocalContext.current
    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onChanged() }
    val enable = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { onChanged() }
    return remember(perms, enable) {
        val ask = { perms.launch(SafarPermissions.runtime(PermId.NEARBY).toTypedArray()) }
        BtFixes(
            requestPermission = ask,
            enableBluetooth = {
                if (!SafarPermissions.isGranted(context, PermId.NEARBY)) {
                    ask()
                } else {
                    try {
                        enable.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                    } catch (e: ActivityNotFoundException) {
                        Log.w(TAG, "No activity handles ACTION_REQUEST_ENABLE", e)
                    }
                }
            },
        )
    }
}
