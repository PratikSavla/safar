package app.safar.ui.navigate

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.safar.core.BikeController
import app.safar.core.NavState
import app.safar.core.NavigationBridge
import app.safar.data.ParkedRepository
import app.safar.data.ParkedSpot
import app.safar.data.Place
import app.safar.data.PlaceRepository
import app.safar.ui.vm.linked
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "SafarRoute"

/** Everything the Route page draws. */
@Immutable
data class NavigateState(
    val phase: RoutePhase = RoutePhase.PICK,
    val nav: NavState = NavState(),
    val mirror: NavMirror? = null,
    val bikeConnected: Boolean = false,
    val destinations: List<Destination> = emptyList(),
    val selected: Destination? = null,
    val editing: Boolean = false,
)

/** Rider choices that live only on this page. */
private data class RouteUi(val selectedKey: String? = null, val editing: Boolean = false, val awaitingMaps: Boolean = false)

class NavigateViewModel(
    private val navigation: NavigationBridge,
    private val places: PlaceRepository,
    parked: ParkedRepository,
    bike: BikeController,
) : ViewModel() {
    private val ui = MutableStateFlow(RouteUi())
    private val events = Channel<String>(Channel.BUFFERED)

    /** One-off messages for the toast. */
    val messages: Flow<String> = events.receiveAsFlow()

    private val placeList = places.places.distinctUntilChanged().catch { e ->
        Log.e(TAG, "Could not read saved places", e)
        emit(emptyList<Place>())
    }
    private val parkedSpot = parked.parked.distinctUntilChanged().catch { e ->
        Log.e(TAG, "Could not read the parked spot", e)
        emit(null as ParkedSpot?)
    }

    val state: StateFlow<NavigateState> = combine(navigation.state, placeList, parkedSpot, bike.state.linked(), ui) { nav, list, spot, linked, u ->
        val dests = destinations(list, spot)
        NavigateState(
            phase = routePhase(nav, u.awaitingMaps),
            nav = nav,
            mirror = nav.snapshot?.let(::mirrorOf),
            bikeConnected = linked,
            destinations = dests,
            selected = resolveSelection(dests, u.selectedKey),
            editing = u.editing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NavigateState())

    init {
        // Maps posted its notification: the search hand-off is over.
        viewModelScope.launch { navigation.state.collect { if (it.active) ui.update { u -> u.copy(awaitingMaps = false) } } }
    }

    fun pick(key: String) = ui.update { it.copy(selectedKey = key) }

    fun toggleEditing() = ui.update { it.copy(editing = !it.editing, awaitingMaps = false) }

    fun start() {
        val dest = state.value.selected ?: return
        try {
            navigation.openInMaps(dest.startLabel, dest.query)
            events.trySend("Opening Google Maps…")
        } catch (e: RuntimeException) {
            Log.e(TAG, "Could not open Google Maps for ${dest.key}", e)
            events.trySend("Couldn’t open Google Maps")
        }
    }

    fun end() = try {
        navigation.end()
    } catch (e: RuntimeException) {
        Log.e(TAG, "Could not end navigation", e)
    }

    /** Called after Maps search opened; [opened] false when no Maps app took it. */
    fun searchOpened(opened: Boolean) {
        if (opened) {
            ui.update { it.copy(awaitingMaps = true, editing = false) }
            events.trySend("Opening Google Maps search…")
        } else {
            events.trySend("Google Maps isn’t installed")
        }
    }

    fun cancelSearch() = ui.update { it.copy(awaitingMaps = false) }

    fun move(placeId: Long, direction: Int) {
        viewModelScope.launch {
            try {
                places.move(placeId, direction)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not move place $placeId by $direction", e)
                events.trySend("Couldn’t move that place. Try again.")
            }
        }
    }

    fun stopEditing() = ui.update { it.copy(editing = false) }
}
