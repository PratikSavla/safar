package app.safar.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** The permissions Safar explains on onboarding page 2, in that order. */
enum class PermId { NEARBY, NOTIFICATIONS, NOTIFICATION_ACCESS, LOCATION, PHONE }

/** Live status of one item: [BLOCKED] = denied and Android will not ask again (only Settings can change it). */
enum class PermStatus { NOT_ASKED, ALLOWED, DENIED, BLOCKED }

/**
 * Runtime permission names per item for an SDK level. Bluetooth scanning needs location below Android 12;
 * notifications are always allowed below Android 13 (an empty list).
 */
object SafarPermissions {
    fun runtime(id: PermId, sdk: Int = Build.VERSION.SDK_INT): List<String> = when (id) {
        PermId.NEARBY -> if (sdk >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        PermId.NOTIFICATIONS -> if (sdk >= Build.VERSION_CODES.TIRAMISU) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()
        PermId.NOTIFICATION_ACCESS -> emptyList()
        PermId.LOCATION -> listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        PermId.PHONE -> listOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_CONTACTS)
    }

    fun granted(context: Context, permissions: List<String>): Boolean =
        permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    /** Notification listener access (Settings → Notification access), needed for Maps turns and caller names. */
    fun notificationAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun isGranted(context: Context, id: PermId): Boolean =
        if (id == PermId.NOTIFICATION_ACCESS) notificationAccess(context) else granted(context, runtime(id))
}

/**
 * Status of one item. [asked] = Safar asked during this run; [canAskAgain] = Android would still show
 * its dialog (shouldShowRequestPermissionRationale, or never asked).
 */
fun permStatus(granted: Boolean, asked: Boolean, canAskAgain: Boolean): PermStatus = when {
    granted -> PermStatus.ALLOWED
    !asked -> PermStatus.NOT_ASKED
    canAskAgain -> PermStatus.DENIED
    else -> PermStatus.BLOCKED
}

/** Button text for an item, or null when it is allowed (a check mark shows instead). */
fun permAction(id: PermId, status: PermStatus): String? = when {
    status == PermStatus.ALLOWED -> null
    status == PermStatus.BLOCKED -> "Settings"
    id == PermId.NOTIFICATION_ACCESS -> "Open"
    status == PermStatus.DENIED -> "Retry"
    else -> "Allow"
}

/** Stamp under each item (onboarding.js `ST`). */
fun permLabel(status: PermStatus): String = when (status) {
    PermStatus.NOT_ASKED -> "not asked"
    PermStatus.ALLOWED -> "allowed"
    PermStatus.DENIED -> "denied"
    PermStatus.BLOCKED -> "denied · in settings"
}
