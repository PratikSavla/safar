package app.safar.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.safar.core.BikeState
import app.safar.core.RideState
import app.safar.data.Trip
import app.safar.data.TripRepository
import app.safar.ui.vm.Notices
import app.safar.ui.vm.launchLogged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId

private const val TAG = "TripViewModel"
private const val DEFAULT_BARS = 6

/** One journal entry: track, stats, rename and delete. */
class TripViewModel(
    private val tripId: Long,
    private val trips: TripRepository,
    ride: StateFlow<RideState>,
    bike: StateFlow<BikeState>,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    private var current: Trip? = null

    val state: StateFlow<TripDetailState> = combine(trips.trip(tripId), trips.trips, trips.track(tripId), ride, bike) { trip, list, track, r, b ->
        current = trip
        if (trip == null) {
            TripDetailState.Missing
        } else {
            val bars = b.saved?.model?.fuelBars ?: DEFAULT_BARS
            TripDetailState.Ready(tripDetail(trip, list, track, bars, r.recordingTripId, now(), zone))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripDetailState.Loading)

    /** Input lock (UX rule 3) for the rename field. */
    val ride: StateFlow<RideState> = ride

    private val _deleted = MutableStateFlow(false)

    /** True once the trip is gone; the screen then goes back. */
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    /** Toast messages ("Renamed", failures). */
    val notices = Notices()

    fun rename(input: String) {
        val trip = current ?: return
        val title = cleanTitle(input, trip.title)
        if (title == trip.title) return
        launchLogged(TAG, "rename of trip $tripId", { notices.post("Could not rename the ride") }) {
            trips.update(trip.copy(title = title))
            notices.post("Renamed")
        }
    }

    fun delete() {
        launchLogged(TAG, "delete of trip $tripId", { notices.post("Could not delete the ride") }) {
            trips.delete(tripId)
            _deleted.value = true
        }
    }
}
