package app.safar.phone

import app.safar.data.GpsFix
import app.safar.data.haversineM

// Phone GPS ride rules: moving hysteresis and trip start/end (docs 12.4 E5, E7; UX rule 3). Pure Kotlin.

/** Above this the phone is moving and text input locks (UX rule 3). */
const val MOVING_START_KMH = 10f
/** Below this for [MOVING_STOP_HOLD_MS] the phone counts as stopped again. */
const val MOVING_STOP_KMH = 6f
const val MOVING_STOP_HOLD_MS = 10_000L
/** Speed is unknown this long after the last fix. */
const val FIX_STALE_MS = 5_000L

/** Trips shorter than this are discarded. */
const val MIN_TRIP_KM = 0.3

/**
 * GPS speed with hysteresis: moving starts above 10 km/h and clears after 10 s below 6 km/h. A missing fix
 * counts as 0 km/h for the clear timer. Not thread-safe.
 */
class MotionDetector {
    private var speed: Float? = null
    private var fixAt: Long? = null
    private var slowSince: Long? = null

    var moving: Boolean = false
        private set

    fun onSpeed(kmh: Float, atMillis: Long) {
        speed = kmh
        fixAt = atMillis
        evaluate(atMillis)
    }

    /** Speed in km/h, or null when the last fix is older than [FIX_STALE_MS]. */
    fun speedAt(nowMillis: Long): Float? {
        val at = fixAt ?: return null
        return if (nowMillis - at > FIX_STALE_MS) null else speed
    }

    /** Re-evaluates [moving] at [nowMillis]; call once a second. */
    fun evaluate(nowMillis: Long): Boolean {
        val kmh = speedAt(nowMillis) ?: 0f
        when {
            kmh > MOVING_START_KMH -> {
                moving = true
                slowSince = null
            }
            kmh < MOVING_STOP_KMH -> {
                val since = slowSince ?: nowMillis.also { slowSince = it }
                if (moving && nowMillis - since >= MOVING_STOP_HOLD_MS) moving = false
            }
            else -> slowSince = null
        }
        return moving
    }
}

/** Bike link as the trip rules see it. */
enum class BikeLink {
    CONNECTED,
    /** Link lost: reconnecting, searching, connecting or Bluetooth off. The trip waits out a grace period. */
    LOST,
    /** The rider disconnected (idle): the trip ends at once. */
    OFF,
}

sealed interface TripDecision {
    data object None : TripDecision
    data class Start(val viaBike: Boolean) : TripDecision
    data object End : TripDecision
}

/**
 * When to start and end a trip. Start: the bike connects, or the phone moves above 10 km/h for 20 s.
 * End: a bike trip ends when the rider disconnects, or the link stays lost for [disconnectGraceMs] (a short
 * drop-out does not split the ride). A trip that never saw the bike ends after [idleEndMs] without movement.
 * Tracks its own recording state; call [evaluate] on each tick and act on the decision. Not thread-safe.
 */
class TripRules(
    private val startMovingMs: Long = 20_000L,
    private val disconnectGraceMs: Long = 90_000L,
    private val idleEndMs: Long = 300_000L,
) {
    private var fastSince: Long? = null
    private var lastMovingAt: Long? = null
    private var lostSince: Long? = null
    private var bikeSeen = false

    var recording: Boolean = false
        private set

    fun evaluate(nowMillis: Long, link: BikeLink, speedKmh: Float?): TripDecision {
        trackSpeed(nowMillis, speedKmh)
        if (!recording) return startDecision(nowMillis, link)
        if (link == BikeLink.CONNECTED) {
            bikeSeen = true
            lostSince = null
            return TripDecision.None
        }
        val end = if (bikeSeen) bikeTripOver(nowMillis, link) else nowMillis - (lastMovingAt ?: nowMillis) >= idleEndMs
        if (!end) return TripDecision.None
        recording = false
        bikeSeen = false
        lostSince = null
        fastSince = null
        return TripDecision.End
    }

    private fun trackSpeed(nowMillis: Long, speedKmh: Float?) {
        val kmh = speedKmh ?: 0f
        fastSince = if (kmh > MOVING_START_KMH) fastSince ?: nowMillis else null
        if (kmh >= MOVING_STOP_KMH) lastMovingAt = nowMillis
    }

    private fun startDecision(nowMillis: Long, link: BikeLink): TripDecision {
        val viaBike = link == BikeLink.CONNECTED
        val viaMovement = fastSince?.let { nowMillis - it >= startMovingMs } == true
        if (!viaBike && !viaMovement) return TripDecision.None
        recording = true
        bikeSeen = viaBike
        lostSince = null
        lastMovingAt = nowMillis
        return TripDecision.Start(viaBike)
    }

    private fun bikeTripOver(nowMillis: Long, link: BikeLink): Boolean {
        if (link == BikeLink.OFF) return true
        val since = lostSince ?: nowMillis.also { lostSince = it }
        return nowMillis - since >= disconnectGraceMs
    }
}

/** A finished trip is kept when it covered at least [MIN_TRIP_KM]. */
fun keepTrip(distanceKm: Double): Boolean = distanceKm >= MIN_TRIP_KM

/** GPS distance, or the bike's odometer difference when GPS saw no movement (no fixes indoors, say). */
fun tripDistanceKm(gpsKm: Double, odoStartKm: Int?, odoEndKm: Int?): Double {
    if (gpsKm > 0.0 || odoStartKm == null || odoEndKm == null) return gpsKm
    return (odoEndKm - odoStartKm).coerceAtLeast(0).toDouble()
}

/** Highest GPS speed taken as real. */
const val MAX_GPS_KMH = 250f

/**
 * Speed from two fixes when the provider gave none: distance over time, for fixes 0.5..10 s apart. Null when
 * the gap is out of range or the result is not plausible.
 */
fun fallbackSpeedKmh(previous: GpsFix?, fix: GpsFix): Float? {
    val prev = previous ?: return null
    val dt = fix.atMillis - prev.atMillis
    if (dt !in 500L..10_000L) return null
    val kmh = (haversineM(prev, fix) / dt * 3600.0).toFloat()
    return kmh.takeIf { it <= MAX_GPS_KMH }
}
