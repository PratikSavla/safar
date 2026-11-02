package app.safar.service

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import app.safar.ble.BlePermissions
import app.safar.core.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Starts [SafarService] while the BLE layer wants it ([shouldRunService]) and stops it after a user disconnect
 * or forget. [start]'s `wanted` is null until the saved bike is loaded, so a START_STICKY restart is not stopped
 * by the empty start-up state. A start refused in the background (API 31+) is retried when an activity starts.
 */
object ServiceController {
    @Volatile private var shouldRun = false

    fun start(context: Context, wanted: StateFlow<Boolean?>, log: DebugLog, scope: CoroutineScope) {
        val app = context.applicationContext
        scope.launch {
            wanted.filterNotNull().distinctUntilChanged().collect { run ->
                shouldRun = run
                if (run) ensureStarted(app, log) else stopService(app, log)
            }
        }
        val retry = OnActivityStarted { if (shouldRun) ensureStarted(app, log) }
        (app as? Application)?.registerActivityLifecycleCallbacks(retry)
    }

    private fun ensureStarted(context: Context, log: DebugLog) {
        if (!SafarService.inForeground) startService(context, log)
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

/** Runs [action] when any activity starts: the app is in the foreground and may start a foreground service. */
private class OnActivityStarted(private val action: () -> Unit) : Application.ActivityLifecycleCallbacks {
    override fun onActivityStarted(activity: Activity) = action()
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}

/** Foreground service types allowed right now, from the granted permissions; null when none is. */
internal fun allowedServiceTypes(context: Context): Int? {
    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    val location = granted(Manifest.permission.ACCESS_FINE_LOCATION) ||
        granted(Manifest.permission.ACCESS_COARSE_LOCATION)
    return foregroundServiceTypes(Build.VERSION.SDK_INT, BlePermissions.canConnect(context), location)
}
