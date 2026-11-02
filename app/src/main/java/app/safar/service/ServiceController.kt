package app.safar.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import app.safar.ble.BlePermissions
import app.safar.core.BikeState
import app.safar.core.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Starts [SafarService] while [shouldRunService] holds (a saved bike to keep, or a connect in progress) and
 * stops it after a user disconnect or forget.
 */
object ServiceController {
    fun start(context: Context, bike: StateFlow<BikeState>, log: DebugLog, scope: CoroutineScope) {
        val app = context.applicationContext
        scope.launch {
            bike.map(::shouldRunService).distinctUntilChanged().collect { run ->
                if (run) startService(app, log) else stopService(app, log)
            }
        }
    }

    private fun startService(context: Context, log: DebugLog) {
        if (allowedServiceTypes(context) == null) {
            // Starting it anyway would crash: the service could not call startForeground() in time.
            Log.w(TAG, "Not starting the foreground service: no allowed type")
            log.event("Foreground service needs Bluetooth or location permission", error = true)
            return
        }
        try {
            ContextCompat.startForegroundService(context, Intent(context, SafarService::class.java))
            log.event("Foreground service started")
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException: the app is in the background on API 31+.
            Log.w(TAG, "Foreground service start not allowed now", e)
            log.event("Foreground service start not allowed", mapOf("error" to (e.message ?: "")), error = true)
        } catch (e: SecurityException) {
            Log.w(TAG, "Foreground service start refused", e)
            log.event("Foreground service start refused", mapOf("error" to (e.message ?: "")), error = true)
        }
    }

    private fun stopService(context: Context, log: DebugLog) {
        if (context.stopService(Intent(context, SafarService::class.java))) log.event("Foreground service stopped")
    }
}

/** Foreground service types allowed right now, from the granted permissions; null when none is. */
internal fun allowedServiceTypes(context: Context): Int? {
    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    val location = granted(Manifest.permission.ACCESS_FINE_LOCATION) ||
        granted(Manifest.permission.ACCESS_COARSE_LOCATION)
    return foregroundServiceTypes(Build.VERSION.SDK_INT, BlePermissions.canConnect(context), location)
}
