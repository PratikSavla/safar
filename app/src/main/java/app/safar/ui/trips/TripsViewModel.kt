package app.safar.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.safar.core.BikeState
import app.safar.core.RideState
import app.safar.data.TrackPoint
import app.safar.data.Trip
import app.safar.data.TripRepository
import app.safar.ui.vm.Notices
import app.safar.ui.vm.launchLogged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId

/** Journal list: trips newest first, week and 30-day totals, selection and merge. */
class TripsViewModel(
    private val trips: TripRepository,
    ride: StateFlow<RideState>,
    bike: StateFlow<BikeState>,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
    /** The phone's clock setting: start times read "17:00" instead of "5:00PM". */
    private val is24h: Boolean = false,
) : ViewModel() {
    private val ride = ride

    /** Rows and totals: rebuilt when the trip list or the bike's name changes, not on every GPS fix. */
    private val entries = combine(trips.trips.distinctUntilChanged(), bike.map { it.saved?.model?.name }.distinctUntilChanged()) { list, name ->
        tripsUiState(list, RideState(), name, now(), zone, is24h)
    }

    /** The live card follows the ride at 1 Hz; `copy` keeps the same rows list, so the postcards skip. */
    val state: StateFlow<TripsUiState> = combine(entries, ride.map(::liveRide).distinctUntilChanged()) { base, live -> base.copy(live = live) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripsUiState())

    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    val selection: StateFlow<Set<Long>> = _selection.asStateFlow()

    fun toggleSelect(id: Long) {
        _selection.update { if (id in it) it - id else it + id }
    }

    fun clearSelection() { _selection.value = emptySet() }

    val notices = Notices()

    private data class UndoMergeState(val mergedId: Long, val originals: List<Trip>, val tracks: Map<Long, List<TrackPoint>>)
    private var undoState: UndoMergeState? = null
    private var undoJob: Job? = null

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private fun scheduleUndoExpiry() {
        undoJob?.cancel()
        undoJob = viewModelScope.launch {
            delay(10_000)
            undoState = null
            _canUndo.value = false
        }
    }

    fun merge() {
        val ids = _selection.value.toList()
        if (ids.size < 2) { notices.post("Select at least 2 rides to merge."); return }
        if (ride.value.recordingTripId in ids) { notices.post("Can’t merge a ride that’s still recording."); return }
        launchLogged("TripsViewModel", "merge trips $ids", { notices.post("Couldn’t merge rides. Try again.") }) {
            val allTrips = trips.trips.first()
            val originals = allTrips.filter { it.id in ids }.sortedBy { it.startMillis }
            if (originals.size < 2) { notices.post("Couldn’t merge rides. Try again."); return@launchLogged }
            val tracks = ids.associateWith { trips.track(it).first() }
            val title = if (ids.size == 2) "${originals.first().title} + ${originals.last().title}".take(40)
                        else "${ids.size} rides merged"
            val newId = trips.merge(ids, title)
            if (newId <= 0) { notices.post("Couldn’t merge rides. Try again."); return@launchLogged }
            undoState = UndoMergeState(newId, originals, tracks)
            _canUndo.value = true
            clearSelection()
            notices.post("Rides merged")
            scheduleUndoExpiry()
        }
    }

    fun undoMerge() {
        val u = undoState ?: return
        undoJob?.cancel()
        undoState = null
        _canUndo.value = false
        launchLogged("TripsViewModel", "undo merge ${u.mergedId}", { notices.post("Couldn’t undo merge. Try again.") }) {
            trips.undoMerge(u.mergedId, u.originals, u.tracks)
            notices.post("Merge undone")
        }
    }

    /**
     * One trip's postcard thumbnail, built on [Dispatchers.Default]. Room re-emits every track on each track_points
     * write; an unchanged track is not filtered again.
     */
    fun thumb(tripId: Long): Flow<TrackThumb> =
        trips.track(tripId).distinctUntilChanged().map { trackThumb(it) }.flowOn(Dispatchers.Default)
}
