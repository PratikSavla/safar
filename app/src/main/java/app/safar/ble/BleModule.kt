package app.safar.ble

import android.content.Context
import android.os.Build
import android.util.Log
import app.safar.BuildConfig
import app.safar.core.AlertsState
import app.safar.core.AppContainer
import app.safar.core.NavState
import app.safar.core.PhoneSnapshot
import app.safar.log.AppLog
import app.safar.log.LogFileStore
import app.safar.protocol.LogHeaderInfo
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.job
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Wires the debug log and the BLE layer into [AppContainer] (log, bike, outbox). Call once from
 * `SafarApp.onCreate`, after `DataModule.install`. Navigation, alerts and phone status are installed later by
 * the phone layer, so they are read when a session starts, not here.
 */
object BleModule {
    private val installed = AtomicBoolean(false)

    fun install(context: Context) {
        if (!installed.compareAndSet(false, true)) return
        val app = context.applicationContext
        val errors = CoroutineExceptionHandler { _, e -> Log.e(TAG, "Uncaught error in a BLE or log coroutine", e) }
        val parent = AppContainer.appScope.coroutineContext
        val scope = CoroutineScope(parent + SupervisorJob(parent.job) + errors)

        val files = LogFileStore(File(app.filesDir, "logs"), File(app.cacheDir, "exports"), header = ::headerInfo)
        val log = AppLog(scope, AppContainer.settings.settings.map { it.debugLog }, files)
        AppContainer.log = log

        val manager = BikeManager(
            context = app,
            log = log,
            savedStore = AppContainer.savedBike,
            serviceItems = { AppContainer.service },
            inputs = ::containerInputs,
            parent = scope,
        )
        AppContainer.bike = manager
        AppContainer.outbox = manager
        manager.start()
        log.event("App started", mapOf("version" to BuildConfig.VERSION_NAME))
    }

    private fun headerInfo(): LogHeaderInfo = LogHeaderInfo(
        app = "Safar",
        appVersion = BuildConfig.VERSION_NAME,
        phone = "${Build.MANUFACTURER} ${Build.MODEL}",
        android = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        bikeName = AppContainer.bike.state.value.saved?.name,
    )

    /** Session inputs from the container; a layer that is not installed yet gives defaults (logged). */
    internal fun containerInputs(): SessionInputs = SessionInputs(
        settings = AppContainer.settings.settings,
        phone = optional("Phone status") { AppContainer.phone.state } ?: MutableStateFlow(PhoneSnapshot()),
        alerts = optional("Alerts") { AppContainer.alerts.state } ?: MutableStateFlow(AlertsState()),
        navigation = optional("Navigation") { AppContainer.navigation.state } ?: MutableStateFlow(NavState()),
    )

    private inline fun <T> optional(what: String, get: () -> StateFlow<T>): StateFlow<T>? = try {
        get()
    } catch (e: UninitializedPropertyAccessException) {
        Log.w(TAG, "$what layer is not installed; the bike session uses defaults", e)
        null
    }
}
