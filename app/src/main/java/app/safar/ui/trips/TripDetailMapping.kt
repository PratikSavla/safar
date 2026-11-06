package app.safar.ui.trips

import app.safar.data.TrackPoint
import app.safar.data.Trip
import app.safar.data.avgMovingSpeedKmh
import app.safar.data.filterTrack
import app.safar.data.toFix
import app.safar.data.withTrackStats
import java.time.ZoneId
import kotlin.math.roundToInt

// Pure state mapping for one journal entry (trips.js `trip`). No Android imports.

/** Fuel bars at connect and disconnect, with the line the page prints under them. */
data class TripFuelUi(val start: Int, val end: Int, val max: Int, val aside: String, val note: String)

data class TripDetailUi(
    val id: Long,
    val number: Int,
    val day: String,
    val title: String,
    val km: Double,
    /** Developer mode only: "bike odometer" when both odometer readings exist, else "phone GPS". */
    val distanceSource: String,
    val minutes: Long,
    val avgKmh: Float,
    val topKmh: Float,
    val odoStart: Int?,
    val odoEnd: Int?,
    val fuel: TripFuelUi?,
    /** Jitter-filtered fixes as (lat, lng), thinned for drawing. */
    val trackLat: List<Double>,
    val trackLng: List<Double>,
    val fixes: Int,
    val recording: Boolean,
)

sealed interface TripDetailState {
    data object Loading : TripDetailState
    data object Missing : TripDetailState
    data class Ready(val trip: TripDetailUi) : TripDetailState
}

private const val MAX_DRAWN_POINTS = 600

/** Aside and note for the fuel row (trips.js): bars used, km per bar, or a fill-up on the way. */
fun tripFuel(start: Int?, end: Int?, max: Int, km: Double): TripFuelUi? {
    if (start == null || end == null) return null
    val used = start - end
    val aside = when {
        used > 0 -> "$used bar${if (used > 1) "s" else ""} used"
        end > start -> "filled up"
        else -> "no change"
    }
    val note = when {
        end > start -> "You filled up on the way"
        used > 0 -> "About ${(km / used).roundToInt()} km per bar"
        else -> "Less than one bar used"
    }
    return TripFuelUi(start, end, max, aside, note)
}

/** Stored average, else the moving average from the track. */
fun tripAverage(trip: Trip, track: List<TrackPoint>): Float =
    if (trip.avgSpeedKmh > 0f) trip.avgSpeedKmh else avgMovingSpeedKmh(track.map { it.toFix() }).toFloat()

/** [trips] newest first (for the entry number), [track] in time order, [fuelBarsMax] from the paired model. */
fun tripDetail(
    trip: Trip,
    trips: List<Trip>,
    track: List<TrackPoint>,
    fuelBarsMax: Int,
    recordingTripId: Long?,
    nowMillis: Long,
    zone: ZoneId,
    is24h: Boolean,
): TripDetailUi {
    // An open trip's row holds zeros until it ends: count it from the track saved so far.
    val trip = if (trip.endMillis == null) trip.withTrackStats(track) else trip
    val index = trips.indexOfFirst { it.id == trip.id }
    val kept = thin(filterTrack(track.map { it.toFix() }), MAX_DRAWN_POINTS)
    return TripDetailUi(
        id = trip.id,
        number = if (index < 0) trips.size + 1 else trips.size - index,
        day = dayTimeLabel(trip.startMillis, zone, is24h),
        title = trip.title,
        km = trip.distanceKm,
        distanceSource = if (trip.odoStartKm != null && trip.odoEndKm != null) "bike odometer" else "phone GPS",
        minutes = tripMinutes(trip, nowMillis),
        avgKmh = tripAverage(trip, track),
        topKmh = trip.topSpeedKmh,
        odoStart = trip.odoStartKm,
        odoEnd = trip.odoEndKm,
        fuel = tripFuel(trip.fuelBarsStart, trip.fuelBarsEnd, fuelBarsMax, trip.distanceKm),
        trackLat = kept.map { it.lat },
        trackLng = kept.map { it.lng },
        fixes = track.size,
        recording = recordingTripId == trip.id,
    )
}

/** A rename is saved trimmed, at most [max] characters; blank keeps the old title. */
fun cleanTitle(input: String, old: String, max: Int = 40): String = input.trim().take(max).ifEmpty { old }
