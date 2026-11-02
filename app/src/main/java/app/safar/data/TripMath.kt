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

/** One counted step of a track: [metres] covered in [millis]. The first fix of a track is a zero leg. */
data class TrackLeg(val metres: Double, val millis: Long)

private fun kmh(metres: Double, millis: Long): Double = metres / 1000.0 / (millis / 3_600_000.0)

/**
 * Drops GPS jitter, one fix at a time: fixes with accuracy worse than 30 m, fixes not newer than the last kept
 * one, and fixes whose jump from the last kept fix implies more than 250 km/h. When two dropped fixes in a row
 * agree with each other, the last kept fix was the outlier (a stale or cold-start first fix, say): the track
 * re-anchors on them and counts only the leg between them, never the jump. [accept] returns the metres added
 * by a kept fix, or null when the fix was dropped. Not thread-safe.
 */
class TrackFilter {
    var last: GpsFix? = null
        private set
    var distanceM: Double = 0.0
        private set

    /** The newest fix dropped for an impossible jump from [last]. */
    private var suspect: GpsFix? = null

    fun accept(fix: GpsFix): Double? = acceptLeg(fix)?.metres

    /** Like [accept], but returns the counted leg with its duration, or null when the fix was dropped. */
    fun acceptLeg(fix: GpsFix): TrackLeg? {
        if (fix.accuracyM != null && fix.accuracyM > MAX_ACCURACY_M) return null
        val prev = last ?: return keep(fix, TrackLeg(0.0, 0))
        val leg = legOrNull(prev, fix) ?: return null
        if (plausible(leg)) return keep(fix, leg)
        val fromSuspect = suspect?.let { legOrNull(it, fix) }
        if (fromSuspect != null && plausible(fromSuspect)) return keep(fix, fromSuspect)
        suspect = fix
        return null
    }

    private fun legOrNull(from: GpsFix, to: GpsFix): TrackLeg? {
        val dtMillis = to.atMillis - from.atMillis
        return if (dtMillis <= 0) null else TrackLeg(haversineM(from, to), dtMillis)
    }

    private fun plausible(leg: TrackLeg): Boolean = kmh(leg.metres, leg.millis) <= MAX_SPEED_KMH

    private fun keep(fix: GpsFix, leg: TrackLeg): TrackLeg {
        last = fix
        suspect = null
        distanceM += leg.metres
        return leg
    }
}

/** The fixes [TrackFilter] keeps, in order. A re-anchor leaves the abandoned fix in the list. */
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
 * Average speed while moving, in km/h: distance over time, counting only legs [TrackFilter] keeps whose speed is
 * at least 3 km/h (stops at lights and fuel stops are left out). 0 when the track never moved.
 */
fun avgMovingSpeedKmh(fixes: List<GpsFix>): Double {
    val filter = TrackFilter()
    var metres = 0.0
    var millis = 0L
    for (fix in fixes) {
        val leg = filter.acceptLeg(fix) ?: continue
        if (leg.millis > 0 && kmh(leg.metres, leg.millis) >= MOVING_KMH) {
            metres += leg.metres
            millis += leg.millis
        }
    }
    return if (millis == 0L) 0.0 else kmh(metres, millis)
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
