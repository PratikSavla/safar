package app.safar.ui.parked

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.util.Log
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import app.safar.data.GpsFix
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOf

private const val TAG = "ParkedSensors"
private const val MIN_TIME_MS = 2_000L
private const val MIN_DISTANCE_M = 1f

/** True when the app may read the phone's location (fine or coarse). */
fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun Location.toFix() = GpsFix(latitude, longitude, time, if (hasAccuracy()) accuracy else null)

/**
 * Phone location while collected, from the platform LocationManager (no Play Services): the newest last-known fix
 * first, then updates. Emits nothing without the permission or a provider; never throws.
 */
@SuppressLint("MissingPermission") // hasLocationPermission is checked first; SecurityException is caught
fun phoneLocation(context: Context): Flow<GpsFix> {
    val lm = context.getSystemService(LocationManager::class.java)
    if (lm == null || !hasLocationPermission(context)) return flowOf()
    return callbackFlow {
        // Compat: on API 26-29 the provider and status callbacks are abstract; a plain SAM lambda crashes there.
        val listener = LocationListenerCompat { trySend(it.toFix()) }
        val providers = locationProviders(lm)
        try {
            providers.mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }?.let { trySend(it.toFix()) }
            providers.forEach { lm.requestLocationUpdates(it, MIN_TIME_MS, MIN_DISTANCE_M, listener, Looper.getMainLooper()) }
        } catch (e: SecurityException) {
            Log.w(TAG, "location permission withdrawn while starting updates", e)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "location provider not available: $providers", e)
        }
        awaitClose { lm.removeUpdates(listener) }
    }.conflate()
}

private fun locationProviders(lm: LocationManager): List<String> {
    val enabled = lm.getProviders(true)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && LocationManager.FUSED_PROVIDER in enabled) {
        return listOf(LocationManager.FUSED_PROVIDER)
    }
    return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { it in enabled }
}

/** True when the phone has a rotation-vector sensor (a compass). */
fun hasCompass(context: Context): Boolean =
    context.getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null

/**
 * Compass heading of the top of the screen, 0..360 degrees from magnetic north, from the rotation-vector sensor.
 * Emits nothing on a phone without one: the page then falls back to the bearing alone.
 */
fun compassHeading(context: Context): Flow<Float> {
    val sm = context.getSystemService(SensorManager::class.java)
    val sensor = sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) ?: return flowOf()
    return callbackFlow {
        val matrix = FloatArray(9)
        val remapped = FloatArray(9)
        val angles = FloatArray(3)
        val display = defaultDisplay(context)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                remapForDisplay(display?.rotation ?: Surface.ROTATION_0, matrix, remapped)
                SensorManager.getOrientation(remapped, angles)
                trySend(((Math.toDegrees(angles[0].toDouble()) + 360) % 360).toFloat())
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        if (!sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)) {
            Log.w(TAG, "rotation vector sensor refused the listener; compass off")
        }
        awaitClose { sm.unregisterListener(listener) }
    }.conflate()
}

/**
 * The built-in display, looked up once per compass flow. DisplayManager works from the application context;
 * Context.getDisplay() there throws on API 30+ (ContextCompat catches and logs it on every call).
 */
private fun defaultDisplay(context: Context): Display? =
    context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)

/** Keeps the heading tied to the top of the screen ([rotation] is a `Surface.ROTATION_*`) in landscape. */
private fun remapForDisplay(rotation: Int, matrix: FloatArray, out: FloatArray) {
    val (x, y) = when (rotation) {
        Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
        Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
        Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
        else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
    }
    SensorManager.remapCoordinateSystem(matrix, x, y, out)
}

/** Magnetic declination at [fix] in degrees (east positive): true heading = magnetic heading + declination. */
fun magneticDeclination(fix: GpsFix): Float = GeomagneticField(fix.lat.toFloat(), fix.lng.toFloat(), 0f, fix.atMillis).declination
