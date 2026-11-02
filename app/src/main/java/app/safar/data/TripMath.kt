package app.safar.data

import java.time.Instant
import java.time.ZoneId
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// Trip maths on phone GPS fixes. Pure Kotlin: no Android imports.

private const val EARTH_RADIUS_M = 6_371_008.8
private const val MAX_ACCURACY_M = 30f
private const val MAX_SPEED_KMH = 250.0
private const val MOVING_KMH = 3.0

/** One GPS fix. [accuracyM] is the horizontal accuracy radius, null when unknown. */
data class GpsFix(val lat: Double, val lng: Double, val atMillis: Long, val accuracyM: Float? = null)

/** Great-circle distance in metres. */
fun haversineM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2).let { it * it } +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).let { it * it }
    return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(a)))
}

fun haversineM(a: GpsFix, b: GpsFix): Double = haversineM(a.lat, a.lng, b.lat, b.lng)

/**
 * Drops GPS jitter, one fix at a time: fixes with accuracy worse than 30 m, fixes not newer than the last kept
 * one, and fixes whose jump from the last kept fix implies more than 250 km/h. [accept] returns the metres
 * added by a kept fix, or null when the fix was dropped. Not thread-safe.
 */
class TrackFilter {
    var last: GpsFix? = null
        private set
    var distanceM: Double = 0.0
        private set

    fun accept(fix: GpsFix): Double? {
        if (fix.accuracyM != null && fix.accuracyM > MAX_ACCURACY_M) return null
        val prev = last
        if (prev == null) {
            last = fix
            return 0.0
        }
        val dtMillis = fix.atMillis - prev.atMillis
        if (dtMillis <= 0) return null
        val metres = haversineM(prev, fix)
        if (metres / 1000.0 / (dtMillis / 3_600_000.0) > MAX_SPEED_KMH) return null
        last = fix
        distanceM += metres
        return metres
    }
}

/** The fixes [TrackFilter] keeps, in order. */
fun filterTrack(fixes: List<GpsFix>): List<GpsFix> {
    val filter = TrackFilter()
    return fixes.filter { filter.accept(it) != null }
}

/** Track length in km after jitter filtering. */
fun trackDistanceKm(fixes: List<GpsFix>): Double {
    val filter = TrackFilter()
    fixes.forEach { filter.accept(it) }
    return filter.distanceM / 1000.0
}

/** Stored track points carry no accuracy, so only the time and speed checks apply. */
fun TrackPoint.toFix(): GpsFix = GpsFix(lat, lng, atMillis)

/**
 * Average speed while moving, in km/h: distance over time, counting only legs between kept fixes whose speed is
 * at least 3 km/h (stops at lights and fuel stops are left out). 0 when the track never moved.
 */
fun avgMovingSpeedKmh(fixes: List<GpsFix>): Double {
    val kept = filterTrack(fixes)
    var metres = 0.0
    var millis = 0L
    for (i in 1 until kept.size) {
        val d = haversineM(kept[i - 1], kept[i])
        val dt = kept[i].atMillis - kept[i - 1].atMillis
        if (d / 1000.0 / (dt / 3_600_000.0) >= MOVING_KMH) {
            metres += d
            millis += dt
        }
    }
    return if (millis == 0L) 0.0 else metres / 1000.0 / (millis / 3_600_000.0)
}

/** Title by the local hour a ride starts: 5–11 morning, 12–16 afternoon, 17–20 evening, else night. */
fun autoTripTitle(startHour: Int): String = when (startHour) {
    in 5..11 -> "Morning ride"
    in 12..16 -> "Afternoon ride"
    in 17..20 -> "Evening ride"
    else -> "Night ride"
}

fun autoTripTitle(startMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    autoTripTitle(Instant.ofEpochMilli(startMillis).atZone(zone).hour)
