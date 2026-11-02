package app.safar.ui.bike

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.safar.core.BikeController
import app.safar.data.SavedBikeStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

private const val TAG = "SafarBike"

/** [loaded] is false until the first value arrives, so the page does not flash "No bike". */
data class BikeScreenState(val loaded: Boolean = false, val info: BikeInfo? = null)

class BikeViewModel(private val bike: BikeController, savedBike: SavedBikeStore) : ViewModel() {
    val state: StateFlow<BikeScreenState> = combine(bike.state, savedBike.saved) { b, saved -> BikeScreenState(true, bikeInfo(b, saved)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BikeScreenState())

    /** Disconnects and deletes the saved bike. Returns false when the BLE layer refused. */
    fun forget(): Boolean = try {
        bike.forgetBike()
        true
    } catch (e: RuntimeException) {
        Log.e(TAG, "Could not forget the bike", e)
        false
    }
}
