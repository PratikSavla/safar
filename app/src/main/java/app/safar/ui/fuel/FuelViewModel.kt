package app.safar.ui.fuel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.safar.core.BikeState
import app.safar.data.FuelRepository
import app.safar.data.SettingsStore
import app.safar.ui.vm.Notices
import app.safar.ui.vm.launchLogged
import app.safar.ui.vm.readingsOnly
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId

private const val TAG = "FuelViewModel"

/** Fuel page: range, mileage, cost and the fuel-up history, all from data/FuelStats. */
class FuelViewModel(
    fuel: FuelRepository,
    private val settings: SettingsStore,
    bike: StateFlow<BikeState>,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    val state: StateFlow<FuelUiState> = combine(fuel.fuelUps.distinctUntilChanged(), settings.settings, bike.readingsOnly()) { list, s, b ->
        fuelUiState(list, s, b, now(), zone)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FuelUiState())

    val notices = Notices()

    fun setLowFuelAlert(on: Boolean) {
        launchLogged(TAG, "low-fuel alert switch", { notices.post("Could not save the setting") }) {
            settings.update { it.copy(lowFuelAlert = on) }
        }
    }
}
