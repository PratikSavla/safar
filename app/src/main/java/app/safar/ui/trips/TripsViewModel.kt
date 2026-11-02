package app.safar.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.safar.core.BikeState
import app.safar.core.RideState
import app.safar.data.TrackPoint
import app.safar.data.TripRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    val state: StateFlow<TripsUiState> = combine(trips.trips, ride, bike) { list, r, b ->
        tripsUiState(list, r, b.saved?.model?.name, now(), zone)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripsUiState())

    /** The GPS track of one trip, for its postcard thumbnail. */
    fun track(tripId: Long): Flow<List<TrackPoint>> = trips.track(tripId)
}
