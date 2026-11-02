package app.safar.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

private const val TAG = "SafarAccess"

/** What Safar may use on this phone. Re-read every time a screen resumes (the rider may come back from Settings). */
data class AccessStatus(val notificationAccess: Boolean = false, val mapsInstalled: Boolean = false)

/**
 * Notification access and Google Maps checks plus the hand-offs to Android Settings and Maps.
 * Every `open*` returns false (and logs why) when no app could take the intent.
 */
object SystemAccess {
    const val MAPS_PACKAGE = "com.google.android.apps.maps"

    fun status(context: Context): AccessStatus = AccessStatus(notificationAccess(context), mapsInstalled(context))

    fun notificationAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /** Needs the `<queries>` entry for [MAPS_PACKAGE] on API 30+. */
    fun mapsInstalled(context: Context): Boolean = context.packageManager.getLaunchIntentForPackage(MAPS_PACKAGE) != null

    fun openNotificationAccess(context: Context): Boolean =
        start(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS), "notification access settings") ||
            start(context, appDetails(context), "app details settings")

    /**
     * The phone's Settings app, where the rider finds Backup. Android has no public screen for backup itself, so
     * this opens the top level.
     */
    fun openPhoneSettings(context: Context): Boolean = start(context, Intent(Settings.ACTION_SETTINGS), "phone settings")

    /** Google Maps search: the rider picks a place and taps Start; Safar joins when the turn notification appears. */
    fun openMapsSearch(context: Context): Boolean {
        val search = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")).setPackage(MAPS_PACKAGE)
        return start(context, search, "Maps search") || openMapsApp(context)
    }

    /** Opens Google Maps itself, for "share a place back to Safar". */
    fun openMapsApp(context: Context): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(MAPS_PACKAGE)
        if (launch == null) {
            Log.w(TAG, "Google Maps is not installed or not visible to Safar")
            return false
        }
        return start(context, launch, "Google Maps")
    }

    private fun appDetails(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

    private fun start(context: Context, intent: Intent, what: String): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "No activity for $what", e)
        false
    } catch (e: SecurityException) {
        Log.w(TAG, "Not allowed to open $what", e)
        false
    }
}

/** [AccessStatus] that refreshes on every resume. */
@Composable
fun rememberAccessStatus(): State<AccessStatus> {
    val context = LocalContext.current
    val status = remember { mutableStateOf(AccessStatus()) }
    LifecycleResumeEffect(context) {
        status.value = SystemAccess.status(context)
        onPauseOrDispose { }
    }
    return status
}
