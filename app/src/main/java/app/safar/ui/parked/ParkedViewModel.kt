package app.safar.ui.parked

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.safar.core.NavigationBridge
import app.safar.data.GpsFix
import app.safar.data.ParkedRepository
import app.safar.data.latLngQuery
import app.safar.ui.vm.Notices
import app.safar.ui.vm.launchLogged
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

private const val TAG = "ParkedViewModel"

/**
 * Parked page: the saved spot, the phone's distance and direction to it, and the walk back in Google Maps.
 * [location] and [heading] are cold flows that run only while the page is on screen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ParkedViewModel(
    private val parked: ParkedRepository,
    private val navigation: () -> NavigationBridge,
    location: () -> Flow<GpsFix>,
    heading: () -> Flow<Float>,
    private val declination: (GpsFix) -> Float = { 0f },
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val allowed = MutableStateFlow(false)

    private val fix: Flow<GpsFix?> = allowed.flatMapLatest { ok -> if (ok) location().onStart<GpsFix?> { emit(null) } else flowOf(null) }

    /** The fix with its magnetic declination, worked out once per fix rather than per compass sample. */
    private val located: Flow<Pair<GpsFix?, Float>> = fix.map { f -> f to (f?.let(declination) ?: 0f) }
    private val compass: Flow<Float?> = heading().onStart<Float?> { emit(null) }

    val state: StateFlow<ParkedUiState> = combine(parked.parked, located, compass, allowed) { spot, (f, decl), h, ok ->
        parkedUiState(spot, f, h, ok, decl)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ParkedUiState())

    val notices = Notices()

    /** Called by the screen whenever the location permission may have changed. */
    fun setLocationAllowed(ok: Boolean) {
        allowed.value = ok
    }

    fun navigateBack() {
        val spot = state.value.spot ?: return
        navigation().openInMaps("Parked bike", latLngQuery(spot.lat, spot.lng))
        notices.post("Opening Google Maps…")
    }

    /** "Moved the bike? Save this spot" with the phone's current fix, if it is fresh enough. */
    fun parkHere() {
        val f = state.value.phoneFix ?: return
        val spot = parkHereSpot(f, now())
        if (spot == null) {
            notices.post("Finding your location. Try again in a moment.")
            return
        }
        launchLogged(TAG, "save of the parked spot", { notices.post("Couldn’t save the spot. Try again.") }) {
            parked.save(spot)
            notices.post("Spot saved")
        }
    }
}
