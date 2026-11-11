package app.safar.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.safar.core.BikeState
import app.safar.core.RideState
import app.safar.data.TrackPoint
import app.safar.data.Trip
import app.safar.data.TripRepository
import app.safar.data.TripStop
import app.safar.data.withTrackStats
import app.safar.ui.vm.Notices
import app.safar.ui.vm.launchLogged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId

private const val TAG = "TripViewModel"
private const val DEFAULT_BARS = 6
private const val NOTE_MAX = 400

/** Why the ride being recorded cannot be deleted; shown on the page and as a toast. */
internal const val RECORDING_DELETE = "Still recording. You can delete this ride once it ends."

/** One journal entry: track, stats, rename and delete. */
class TripViewModel(
    private val tripId: Long,
    private val trips: TripRepository,
    ride: StateFlow<RideState>,
    bike: StateFlow<BikeState>,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
    /** The phone's clock setting: the start time reads "17:00" instead of "5:00PM". */
    private val is24h: Boolean = false,
    private val onStopTrip: () -> Unit = {},
) : ViewModel() {
    /** Written by the state flow on [Dispatchers.Default], read by [rename] on the main thread. */
    @Volatile
    private var current: Trip? = null

    val state: StateFlow<TripDetailState> = combine(
        trips.trip(tripId),
        trips.trips,
        trips.track(tripId),
        trips.stops(tripId),
        combine(ride, bike, ::Pair),
    ) { trip, list, track, stops, (r, b) ->
        current = trip
        if (trip == null) {
            TripDetailState.Missing
        } else {
            val bars = b.saved?.model?.fuelBars ?: DEFAULT_BARS
            TripDetailState.Ready(tripDetail(trip, list, track, stops, bars, r.recordingTripId, now(), zone, is24h))
        }
    }.flowOn(Dispatchers.Default) // tripDetail filters the whole track
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripDetailState.Loading)

    /** Input lock (UX rule 3) for the rename field. */
    val ride: StateFlow<RideState> = ride

    private val _deleted = MutableStateFlow(false)

    /** True once the trip is gone; the screen then goes back. */
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    /** Toast messages ("Ride renamed", failures). */
    val notices = Notices()

    fun rename(input: String) {
        val trip = current ?: return
        val title = cleanTitle(input, trip.title)
        if (title == trip.title) return
        launchLogged(TAG, "rename of trip $tripId", { notices.post("Couldn’t rename the ride. Try again.") }) {
            trips.update(trip.copy(title = title))
            notices.post("Ride renamed")
        }
    }

    fun saveNote(input: String) {
        val note = input.trim().take(NOTE_MAX).ifEmpty { null }
        launchLogged(TAG, "note on trip $tripId", { notices.post("Couldn’t save the note. Try again.") }) {
            trips.setNote(tripId, note)
            notices.post(if (note == null) "Note removed" else "Note saved")
        }
    }

    /** Ends a recording ride early. */
    fun endTrip() {
        launchLogged(TAG, "end trip $tripId", { notices.post("Couldn’t end the ride. Try again.") }) {
            val trip = current ?: trips.trip(tripId).first() ?: return@launchLogged
            val track = trips.track(tripId).first()
            val counted = trip.withTrackStats(track)
            val endAt = track.maxOfOrNull { it.atMillis } ?: now()
            trips.update(counted.copy(endMillis = endAt))
            onStopTrip()
            notices.post("Ride ended")
        }
    }

    /** Refused while this trip is being recorded: the recorder still writes its track points (UX: stop first). */
    fun delete() {
        if (ride.value.recordingTripId == tripId && current?.endMillis == null) {
            notices.post(RECORDING_DELETE)
            return
        }
        launchLogged(TAG, "delete of trip $tripId", { notices.post("Couldn’t delete the ride. Try again.") }) {
            trips.delete(tripId)
            _deleted.value = true
        }
    }
}
