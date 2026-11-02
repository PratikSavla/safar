package app.safar.ui.parked

import app.safar.data.GpsFix
import app.safar.data.ParkedSpot
import app.safar.data.haversineM
import app.safar.data.latLngQuery
import app.safar.ui.format.Formatters
import app.safar.ui.format.FreshnessText
import java.time.ZoneId
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

// Pure maths and state mapping for the parked page (ux/variant-b/js/screens/parked.js). No Android imports.

private val DIRS = listOf("north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west")
private const val HERE_MIN_M = 10f

/** Where the phone is relative to the parked bike. Null fields = no phone fix yet. */
data class ParkedUiState(
    val loading: Boolean = true,
    val spot: ParkedSpot? = null,
    val place: String = "",
    val distanceM: Int? = null,
    val bearingDeg: Int? = null,
    /** Needle angle on screen: the bearing minus the phone heading, or the plain bearing without a compass. */
    val pointerDeg: Float? = null,
    /** Rose rotation on screen (minus the heading), 0 without a compass. */
    val roseDeg: Float = 0f,
    val hasCompass: Boolean = false,
    val here: Boolean = false,
    val phoneFix: GpsFix? = null,
    val locationAllowed: Boolean = false,
)

/** Initial great-circle bearing from the first point to the second, 0..360 degrees from north. */
fun bearingDeg(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val p1 = Math.toRadians(lat1)
    val p2 = Math.toRadians(lat2)
    val dl = Math.toRadians(lng2 - lng1)
    val y = sin(dl) * cos(p2)
    val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
    return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
}

/** "north-east" for 45 degrees (parked.js `dirWord`). */
fun dirWord(deg: Double): String = DIRS[((((deg % 360) + 360) % 360) / 45).roundToInt() % 8]

/** Needle angle: towards the bike relative to where the top of the phone points. */
fun pointerAngle(bearing: Double, heading: Float?): Float = (((bearing - (heading ?: 0f)) % 360 + 360) % 360).toFloat()

/** The spot's label, else plain words (the coordinates help nobody on screen; the Maps link carries them). */
fun placeName(spot: ParkedSpot): String = spotLabel(spot) ?: "Your parked spot"

private fun spotLabel(spot: ParkedSpot): String? = spot.label?.takeIf { it.isNotBlank() }

/** Developer mode only: "18.50000, 73.80000". */
fun coordinates(spot: ParkedSpot): String = String.format(Locale.US, "%.5f, %.5f", spot.lat, spot.lng)

/** "Parked 12 min ago", "Parked today, 14:05", "Parked yesterday, 18:40": never seconds. */
fun parkedAgo(atMillis: Long, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    "Parked ${FreshnessText.calmAge(atMillis, nowMillis, zone)}"

/** "to the north-east": the direction in words, under the distance. */
fun directionText(bearing: Int): String = "to the ${dirWord(bearing.toDouble())}"

/** What TalkBack reads for the compass. */
fun compassDescription(state: ParkedUiState): String {
    val distance = state.distanceM
    val bearing = state.bearingDeg
    return when {
        distance == null || bearing == null -> "Compass. Waiting for your phone’s location."
        state.here -> "You’re at your bike."
        else -> "Your bike is ${distanceText(distance)} away, ${directionText(bearing)}."
    }
}

/** How good the spot is and how to read the arrow. */
fun accuracyText(accuracyM: Float, hasCompass: Boolean): String {
    val hold = if (hasCompass) {
        "Hold your phone flat and the arrow points to your bike."
    } else {
        "Your phone has no compass, so north is at the top and the arrow shows the way from there."
    }
    return "The spot is accurate to about ${accuracyM.roundToInt()} m. $hold"
}

/** "85 m" (to 5 m) under a kilometre, then "1.2 km". */
fun distanceText(metres: Int): String =
    if (metres < 1000) "${(metres / 5.0).roundToInt() * 5} m" else "${Formatters.km1(metres / 1000.0)} km"

/** Text for the share sheet: plain words and a Google Maps link anyone can open. */
fun shareText(spot: ParkedSpot): String {
    val q = latLngQuery(spot.lat, spot.lng)
    val where = spotLabel(spot)?.let { "My bike is parked here: $it" } ?: "My bike is parked here"
    return "$where\nhttps://www.google.com/maps/search/?api=1&query=$q"
}

/**
 * [heading] is the compass reading from magnetic north; [declination] (east positive, from the phone fix) turns it
 * into a true-north heading, the reference [bearingDeg] uses.
 */
fun parkedUiState(spot: ParkedSpot?, fix: GpsFix?, heading: Float?, locationAllowed: Boolean, declination: Float = 0f): ParkedUiState {
    if (spot == null) return ParkedUiState(loading = false, phoneFix = fix, locationAllowed = locationAllowed)
    val trueHeading = heading?.let { ((it + declination) % 360 + 360) % 360 }
    val base = ParkedUiState(
        loading = false, spot = spot, place = placeName(spot), hasCompass = trueHeading != null,
        roseDeg = -(trueHeading ?: 0f), phoneFix = fix, locationAllowed = locationAllowed,
    )
    if (fix == null) return base
    val metres = haversineM(fix.lat, fix.lng, spot.lat, spot.lng)
    val bearing = bearingDeg(fix.lat, fix.lng, spot.lat, spot.lng)
    val near = max(HERE_MIN_M, max(spot.accuracyM, fix.accuracyM ?: 0f))
    return base.copy(
        distanceM = metres.roundToInt(),
        bearingDeg = bearing.roundToInt() % 360,
        pointerDeg = pointerAngle(bearing, trueHeading),
        here = metres <= near,
    )
}

/** The oldest phone fix "park here" accepts; the same limit the automatic save at disconnect uses. */
const val PARK_HERE_MAX_AGE_MS = 10 * 60_000L

/**
 * The fix to save as the parked spot, or null when [fix] is missing or older than [PARK_HERE_MAX_AGE_MS] (a stale
 * last-known location from somewhere else). The spot keeps the fix's own time, so its age reads true.
 */
fun parkHereSpot(fix: GpsFix?, nowMillis: Long): ParkedSpot? {
    val f = fix?.takeIf { nowMillis - it.atMillis <= PARK_HERE_MAX_AGE_MS } ?: return null
    return ParkedSpot(f.lat, f.lng, f.accuracyM ?: 0f, minOf(f.atMillis, nowMillis), label = null)
}

/** The rotation to animate to: [target] reached from [current] the short way round (no 350 -> 10 spin). */
fun shortestRotation(current: Float, target: Float): Float {
    val delta = ((target - current) % 360 + 540) % 360 - 180
    return current + delta
}
