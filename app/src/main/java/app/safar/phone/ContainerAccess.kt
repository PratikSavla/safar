package app.safar.phone

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import app.safar.core.AppContainer
import app.safar.core.ClusterOutbox
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean

internal const val TAG = "SafarPhone"

/*
 * Other layers install their AppContainer singletons in their own modules. The phone layer reads them lazily,
 * at the moment it needs them, so install order only matters for the first event.
 */

/** Routes [PhoneEventLog] events to `AppContainer.log` once the log layer is installed. */
internal object ContainerLog : PhoneEventLog {
    private val warned = AtomicBoolean(false)

    override fun event(name: String, detail: Map<String, String>, error: Boolean) {
        val log = try {
            AppContainer.log
        } catch (e: UninitializedPropertyAccessException) {
            if (warned.compareAndSet(false, true)) Log.w(TAG, "Debug log not installed; dropping \"$name\" and later events", e)
            return
        }
        log.event(name, detail, error)
    }
}

private val outboxWarned = AtomicBoolean(false)

/** `AppContainer.outbox`, or null (logged once) while the BLE layer is not installed. */
internal fun outboxOrNull(): ClusterOutbox? = try {
    AppContainer.outbox
} catch (e: UninitializedPropertyAccessException) {
    if (outboxWarned.compareAndSet(false, true)) Log.w(TAG, "Cluster outbox not installed; alerts are not sent", e)
    null
}

/** Waits until another layer has assigned [read]'s singleton, polling every 500 ms. */
internal suspend fun <T> awaitInstalled(what: String, read: () -> T): T {
    var logged = false
    while (true) {
        try {
            return read()
        } catch (e: UninitializedPropertyAccessException) {
            if (!logged) Log.w(TAG, "$what not installed yet; waiting", e)
            logged = true
        }
        delay(500)
    }
}

internal fun Context.granted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
