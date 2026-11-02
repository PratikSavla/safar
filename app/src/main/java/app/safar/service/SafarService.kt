package app.safar.service

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import app.safar.core.AppContainer
import app.safar.core.NavState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

internal const val TAG = "SafarService"

/**
 * Foreground service (docs 12.3) that keeps the process alive while the BLE layer works on the link. It owns
 * nothing: it shows link state, odometer and next turn from [AppContainer] and offers "Disconnect".
 * [ServiceController] starts and stops it.
 */
class SafarService : LifecycleService() {
    private var observing = false

    override fun onCreate() {
        super.onCreate()
        ServiceNotifications.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (!goForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        inForeground = true
        if (intent?.action == ACTION_DISCONNECT) {
            AppContainer.log.event("Disconnect from notification")
            AppContainer.bike.disconnect()
        }
        if (!observing) {
            observing = true
            lifecycleScope.launch { observe() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        inForeground = false
        super.onDestroy()
    }

    private suspend fun observe() {
        combine(AppContainer.bike.state, navigationState()) { bike, nav -> notificationContent(bike, nav) }
            .distinctUntilChanged()
            .collect { ServiceNotifications.update(this, ServiceNotifications.build(this, it)) }
    }

    private fun navigationState(): Flow<NavState> = try {
        AppContainer.navigation.state
    } catch (e: UninitializedPropertyAccessException) {
        Log.w(TAG, "Navigation layer not installed; the notification shows no next turn", e)
        MutableStateFlow(NavState())
    }

    /** startForeground with the types this start may use (API 34+ checks permissions per type). */
    @SuppressLint("InlinedApi") // The type mask is 0 below API 29; ServiceCompat ignores it there.
    private fun goForeground(): Boolean {
        val types = allowedServiceTypes(this)
        if (types == null) {
            Log.w(TAG, "No foreground service type allowed (Bluetooth or location permission missing)")
            AppContainer.log.event("Service not started: permission missing", error = true)
            return false
        }
        val content = notificationContent(AppContainer.bike.state.value, NavState())
        val notification = ServiceNotifications.build(this, content)
        if (startForegroundAs(notification, types)) return true
        // Location is "while in use" on API 34+: a start from the background can only use connectedDevice.
        val deviceOnly = types and ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION.inv()
        return deviceOnly != types && deviceOnly != 0 && startForegroundAs(notification, deviceOnly)
    }

    private fun startForegroundAs(notification: android.app.Notification, types: Int): Boolean = try {
        ServiceCompat.startForeground(this, ServiceNotifications.NOTIFICATION_ID, notification, types)
        true
    } catch (e: SecurityException) {
        Log.w(TAG, "startForeground with types $types refused", e)
        false
    } catch (e: IllegalStateException) {
        // ForegroundServiceStartNotAllowedException (API 31+) and invalid type errors.
        Log.e(TAG, "startForeground with types $types not allowed", e)
        AppContainer.log.event("Service start not allowed", mapOf("error" to (e.message ?: "")), error = true)
        false
    }

    companion object {
        const val ACTION_DISCONNECT = "app.safar.service.DISCONNECT"

        /** True while this process runs the service in the foreground (also after a START_STICKY restart). */
        @Volatile
        var inForeground = false
            private set
    }
}
