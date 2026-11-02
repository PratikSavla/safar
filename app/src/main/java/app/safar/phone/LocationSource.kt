package app.safar.phone

import android.Manifest
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import app.safar.data.GpsFix

/** One phone location: the fix and the provider's speed in km/h, if it had one. */
data class GpsSample(val fix: GpsFix, val speedKmh: Float?)

/** How much GPS the ride tracker asks for. */
enum class LocationMode {
    OFF,
    /** Only fixes other apps request (Google Maps, say): no extra battery use while parked. */
    PASSIVE,
    /** GPS every 1 s, 0 m, while connected or moving. */
    GPS,
}

private const val INTERVAL_MS = 1_000L

/**
 * `LocationManager` updates without Play Services. Needs ACCESS_FINE_LOCATION; without it every mode acts as
 * [LocationMode.OFF]. Thread-safe; fixes arrive on the main looper.
 */
internal class LocationSource(context: Context, private val onSample: (GpsSample) -> Unit) {
    private val app = context.applicationContext
    private val manager: LocationManager? = app.getSystemService(LocationManager::class.java)
    private var mode = LocationMode.OFF

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) = onSample(location.toSample())
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit

        // Abstract below API 30: without this override the platform call would fail at run time.
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    val hasPermission: Boolean get() = app.granted(Manifest.permission.ACCESS_FINE_LOCATION)

    @Synchronized
    fun set(wanted: LocationMode) {
        val lm = manager ?: return
        val target = if (hasPermission) wanted else LocationMode.OFF
        if (target == mode) return
        try {
            lm.removeUpdates(listener)
            when (target) {
                LocationMode.OFF -> Unit
                LocationMode.PASSIVE ->
                    lm.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, INTERVAL_MS, 0f, listener, Looper.getMainLooper())
                LocationMode.GPS ->
                    lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, INTERVAL_MS, 0f, listener, Looper.getMainLooper())
            }
            mode = target
        } catch (e: SecurityException) {
            Log.w(TAG, "Location permission revoked; GPS off", e)
            mode = LocationMode.OFF
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Location provider for $target missing; GPS off", e)
            mode = LocationMode.OFF
        }
    }

    /** The phone's last GPS fix, if readable. */
    fun lastKnownGps(): GpsSample? {
        val lm = manager ?: return null
        if (!hasPermission) return null
        return try {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.toSample()
        } catch (e: SecurityException) {
            Log.w(TAG, "Last known location not readable", e)
            null
        }
    }

    private fun Location.toSample(): GpsSample = GpsSample(
        fix = GpsFix(latitude, longitude, time, if (hasAccuracy()) accuracy else null),
        speedKmh = if (hasSpeed()) speed * 3.6f else null,
    )
}
