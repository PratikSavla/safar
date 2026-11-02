package app.safar.ui.navigate

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat

/** The phone's last known position for "Where I am now". No new fix is requested: no battery, no wait. */
object LastLocation {
    private const val TAG = "SafarPlace"

    val PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    fun permitted(context: Context): Boolean =
        PERMISSIONS.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    /** Newest last-known fix over the enabled providers, or null without permission or without any fix. */
    @SuppressLint("MissingPermission") // checked by permitted() just above
    fun read(context: Context): Location? {
        if (!permitted(context)) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        return try {
            lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
        } catch (e: SecurityException) {
            Log.w(TAG, "Location permission was revoked while reading the last fix", e)
            null
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "A location provider went away while reading the last fix", e)
            null
        }
    }
}
