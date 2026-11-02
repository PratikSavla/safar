package app.safar.ui.ride

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.safar.core.AlertCenter
import app.safar.core.AppContainer
import app.safar.core.BikeController
import app.safar.core.NavigationBridge
import app.safar.core.RideTracker
import app.safar.data.SettingsStore
import app.safar.ui.shell.ShellDeps
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class RideViewModel(
    bike: BikeController,
    ride: RideTracker,
    navigation: NavigationBridge,
    alerts: AlertCenter,
    settings: SettingsStore,
) : ViewModel() {
    val ui: StateFlow<RideUi?> = combine(bike.state, ride.state, navigation.state, alerts.state, settings.settings, ::rideUi)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun fromContainer() = RideViewModel(ShellDeps.bike, ShellDeps.ride, ShellDeps.navigation, ShellDeps.alerts, AppContainer.settings)
    }
}
