package app.safar.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.safar.core.BikeState
import app.safar.core.RideState
import app.safar.data.TripRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId

/** Journal list: trips newest first, week and 30-day totals, and the ride being recorded. */
class TripsViewModel(
    private val trips: TripRepository,
    ride: StateFlow<RideState>,
    bike: StateFlow<BikeState>,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    /** Rows and totals: rebuilt when the trip list or the bike's name changes, not on every GPS fix. */
    private val entries = combine(trips.trips.distinctUntilChanged(), bike.map { it.saved?.model?.name }.distinctUntilChanged()) { list, name ->
        tripsUiState(list, RideState(), name, now(), zone)
    }

    /** The live card follows the ride at 1 Hz; `copy` keeps the same rows list, so the postcards skip. */
    val state: StateFlow<TripsUiState> = combine(entries, ride.map(::liveRide).distinctUntilChanged()) { base, live -> base.copy(live = live) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripsUiState())

    /**
     * One trip's postcard thumbnail, built on [Dispatchers.Default]. Room re-emits every track on each track_points
     * write; an unchanged track is not filtered again.
     */
    fun thumb(tripId: Long): Flow<TrackThumb> =
        trips.track(tripId).distinctUntilChanged().map { trackThumb(it) }.flowOn(Dispatchers.Default)
}
