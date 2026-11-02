package app.safar.phone

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import app.safar.protocol.GOOGLE_MAPS_PACKAGE

/** Setup checks for the onboarding and settings screens (docs 11.3, 12.4 R12). */
object PhoneAccess {
    /** True when Google Maps is installed (needs the `<queries>` entry in the manifest). */
    fun isMapsInstalled(context: Context): Boolean = isInstalled(context, GOOGLE_MAPS_PACKAGE)

    /** The default phone app's package (its missed-call notification resets the count), or null when unknown. */
    fun defaultDialer(context: Context): String? = try {
        context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
    } catch (e: SecurityException) {
        Log.w(TAG, "Default phone app not readable", e)
        null
    }

    /** True when WhatsApp or WhatsApp Business is installed. */
    fun isWhatsAppInstalled(context: Context): Boolean = WHATSAPP_PACKAGES.any { isInstalled(context, it) }

    /** True when the rider gave Safar notification access (Maps navigation and WhatsApp alerts). */
    fun hasNotificationAccess(context: Context): Boolean =
        context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    /** Opens the system screen where the rider grants notification access. */
    fun notificationAccessIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Opens Google Maps in the Play Store app, or on the web when there is no store app. */
    fun openMapsInPlayStore(context: Context) {
        val store = Intent(Intent.ACTION_VIEW, "market://details?id=$GOOGLE_MAPS_PACKAGE".toUri())
        val web = Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$GOOGLE_MAPS_PACKAGE".toUri())
        for (intent in listOf(store, web)) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "No app for ${intent.data}", e)
            }
        }
    }

    private fun isInstalled(context: Context, pkg: String): Boolean = try {
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION") // The flags overload is API 33+.
            pm.getPackageInfo(pkg, 0)
        }
        true
    } catch (e: PackageManager.NameNotFoundException) {
        Log.i(TAG, "$pkg is not installed (${e.message})")
        false
    }
}
