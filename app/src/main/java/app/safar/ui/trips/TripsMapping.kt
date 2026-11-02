package app.safar.ui.trips

import app.safar.core.RideState
import app.safar.data.TrackPoint
import app.safar.data.Trip
import app.safar.data.filterTrack
import app.safar.data.toFix
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Pure state mapping for the journal (ux/variant-b/js/screens/trips.js). No Android imports.

private const val MINUTE_MS = 60_000L
private const val DAY_MS = 86_400_000L

/** Points drawn on a postcard thumbnail. */
const val THUMB_POINTS = 120
private val dayFormat = DateTimeFormatter.ofPattern("EEE dd MMM", Locale.UK)

/** Week or 30-day sums: distance, riding time, ride count and the top speed of any ride. */
data class TripTotals(val km: Double, val minutes: Long, val rides: Int, val topKmh: Float)

/** One postcard in the list. [number] counts from the oldest entry (№001). */
data class TripRowUi(
    val id: Long,
    val number: Int,
    val day: String,
    val title: String,
    val km: Double,
    val minutes: Long,
    val topKmh: Float,
)

/** The ride being recorded right now, from phone GPS. [tripId] opens its page. */
data class LiveRideUi(val speedKmh: Float?, val topKmh: Float, val atMillis: Long?, val tripId: Long? = null)

data class TripsUiState(
    val loading: Boolean = true,
    val bikeName: String? = null,
    val week: TripTotals = TripTotals(0.0, 0, 0, 0f),
    val month: TripTotals = TripTotals(0.0, 0, 0, 0f),
    val rows: List<TripRowUi> = emptyList(),
    val live: LiveRideUi? = null,
)

/** Minutes from start to end; a ride still being written counts up to [nowMillis]. */
fun tripMinutes(trip: Trip, nowMillis: Long): Long =
    ((trip.endMillis ?: nowMillis) - trip.startMillis).coerceAtLeast(0) / MINUTE_MS

/** Totals of rides that started in the last [days] days before [nowMillis]. */
fun tripTotals(trips: List<Trip>, nowMillis: Long, days: Int): TripTotals {
    val from = nowMillis - days * DAY_MS
    val list = trips.filter { it.startMillis >= from && it.startMillis <= nowMillis }
    return TripTotals(
        km = list.sumOf { it.distanceKm },
        minutes = list.sumOf { tripMinutes(it, nowMillis) },
        rides = list.size,
        topKmh = list.maxOfOrNull { it.topSpeedKmh } ?: 0f,
    )
}

/** "42 min" under an hour, else "1 h 05" (trips.js `dur`). */
fun durationText(minutes: Long): String =
    if (minutes >= 60) "${minutes / 60} h ${(minutes % 60).toString().padStart(2, '0')}" else "$minutes min"

/** "SAT 12 OCT" (trips.js `day`). */
fun dayLabel(atMillis: Long, zone: ZoneId): String =
    dayFormat.format(Instant.ofEpochMilli(atMillis).atZone(zone)).uppercase(Locale.UK).replace(".", "")

/** "№007". */
fun entryNumber(number: Int): String = "№" + number.toString().padStart(3, '0')

/** [trips] arrive newest first; numbers count up from the oldest. */
fun tripRows(trips: List<Trip>, nowMillis: Long, zone: ZoneId): List<TripRowUi> = trips.mapIndexed { i, t ->
    TripRowUi(t.id, trips.size - i, dayLabel(t.startMillis, zone), t.title, t.distanceKm, tripMinutes(t, nowMillis), t.topSpeedKmh)
}

fun liveRide(ride: RideState): LiveRideUi? =
    ride.recordingTripId?.let { LiveRideUi(ride.speedKmh, ride.topSpeedKmh, ride.lastFixAtMillis, it) }

/**
 * Finished trips only. An open trip's row holds zeros until it ends, so it is left out of the entries and
 * totals; the recording card stands for it. Entry numbers stay the same: the open trip is always the newest.
 */
fun finishedTrips(trips: List<Trip>): List<Trip> = trips.filter { it.endMillis != null }

fun tripsUiState(trips: List<Trip>, ride: RideState, bikeName: String?, nowMillis: Long, zone: ZoneId): TripsUiState {
    val done = finishedTrips(trips)
    return TripsUiState(
        loading = false,
        bikeName = bikeName,
        week = tripTotals(done, nowMillis, days = 7),
        month = tripTotals(done, nowMillis, days = 30),
        rows = tripRows(done, nowMillis, zone),
        live = liveRide(ride),
    )
}

/** A postcard's ink track, filtered and thinned, as parallel latitude and longitude lists. */
data class TrackThumb(val lat: List<Double>, val lng: List<Double>) {
    companion object {
        val Empty = TrackThumb(emptyList(), emptyList())
    }
}

/** GPS jumps filtered out, then at most [max] points kept. Runs over the whole track: call it off the main thread. */
fun trackThumb(track: List<TrackPoint>, max: Int = THUMB_POINTS): TrackThumb {
    val kept = thin(filterTrack(track.map { it.toFix() }), max)
    return TrackThumb(kept.map { it.lat }, kept.map { it.lng })
}
