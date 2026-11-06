package app.safar.ui.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.safar.core.AlertCenter
import app.safar.core.AlertsState
import app.safar.core.AppContainer
import app.safar.core.BikeController
import app.safar.core.BikeState
import app.safar.core.DebugLog
import app.safar.core.NavState
import app.safar.core.NavigationBridge
import app.safar.core.RideState
import app.safar.core.RideTracker
import app.safar.data.FuelRepository
import app.safar.data.FuelUp
import app.safar.data.ParkedRepository
import app.safar.data.ParkedSpot
import app.safar.data.PlaceRepository
import app.safar.data.ServiceItem
import app.safar.data.ServiceRepository
import app.safar.data.Settings
import app.safar.data.SettingsStore
import app.safar.data.Trip
import app.safar.data.TripRepository
import app.safar.data.fuelStats
import app.safar.ui.copy.LinkCopy
import app.safar.ui.service.odoNow
import app.safar.ui.shell.ShellDeps
import app.safar.ui.trips.finishedTrips
import app.safar.ui.vm.throttleLatest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Everything Today shows, except phone GPS speed ([HomeViewModel.speed]), which changes every second and is read
 * by its one ledger cell. [rideCount] is the number of finished rides on this phone ("12 rides"), as Trips counts them.
 */
@Immutable
data class HomeUi(
    val riderName: String,
    val modelName: String?,
    val conn: ConnUi,
    val autoRideOn: Boolean,
    val ledger: LedgerUi?,
    val counts: CountsUi,
    val nav: NavStripUi?,
    val garage: List<GarageTile>,
    val rideCount: Int,
    /** Permission names the rider answered "Not now" on Today. */
    val dismissedPermissions: Set<String> = emptySet(),
)

/** Live link and phone state. */
data class HomeLive(val bike: BikeState, val nav: NavState, val alerts: AlertsState, val settings: Settings)

/** What the rider stored on this phone. */
data class HomeStored(
    val fuelUps: List<FuelUp>,
    val services: List<ServiceItem>,
    val parked: ParkedSpot?,
    val trips: List<Trip>,
    val placesCount: Int,
)

internal fun homeUi(live: HomeLive, stored: HomeStored, logCount: Int, nowMillis: Long): HomeUi {
    val bike = live.bike
    val reading = bike.cluster ?: bike.lastCluster
    val fuel = fuelStats(stored.fuelUps, live.settings, bike.saved?.model, reading, nowMillis)
    val nav = navStripUi(live.nav)
    val alertCount = live.alerts.missed.count + live.alerts.messages.count
    val garage = GarageInputs(
        fuel, odoNow(bike, stored.services, stored.fuelUps).km, stored.services, stored.parked, stored.trips, stored.placesCount,
        nav, alertCount, bike.link, live.settings.debugLog, logCount, live.settings.developerMode, nowMillis,
    )
    return HomeUi(
        riderName = live.settings.riderName.trim(),
        modelName = LinkCopy.modelName(bike),
        conn = connUi(bike),
        autoRideOn = live.settings.autoRideMode,
        ledger = ledgerUi(bike, fuel),
        counts = CountsUi(live.alerts.missed, live.alerts.messages),
        nav = nav,
        garage = garageTiles(garage),
        rideCount = finishedTrips(stored.trips).size,
        dismissedPermissions = live.settings.dismissedPermissions,
    )
}

class HomeViewModel(
    private val bike: BikeController,
    ride: RideTracker,
    navigation: NavigationBridge,
    alerts: AlertCenter,
    private val settings: SettingsStore,
    fuel: FuelRepository,
    service: ServiceRepository,
    parked: ParkedRepository,
    trips: TripRepository,
    places: PlaceRepository,
    log: DebugLog,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val live = combine(bike.state, navigation.state, alerts.state, settings.settings, ::HomeLive)
    private val stored = combine(
        fuel.fuelUps.distinctUntilChanged(),
        service.items.distinctUntilChanged(),
        parked.parked.distinctUntilChanged(),
        trips.trips.distinctUntilChanged(),
        places.places.map { it.size }.distinctUntilChanged(),
        ::HomeStored,
    )

    /** "40 in memory" on the Debug log tile: the log grows several times a second, the tile needs it once a second at most. */
    private val logCount = log.records.map { it.size }.distinctUntilChanged().throttleLatest(LOG_COUNT_PERIOD_MS)

    /** Recomputed when an input changes; a result equal to the last one is not emitted (StateFlow equality). */
    val ui: StateFlow<HomeUi?> = combine(live, stored, logCount) { l, s, n -> homeUi(l, s, n, clock()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** Phone GPS speed for the ledger's speed cell, kept out of [ui] so a 1 Hz fix does not rebuild the page. */
    val speed: StateFlow<SpeedUi> = ride.state.map(::speedUi)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), speedUi(RideState()))

    /** Direct connect to the saved cluster (docs 2.4). */
    fun connectSaved() {
        bike.state.value.saved?.let { bike.connect(it.address) }
    }

    /** "Not now" on the permissions card: hide it until something else is off. */
    fun dismissPermissions(names: Set<String>) {
        viewModelScope.launch { settings.update { it.copy(dismissedPermissions = names) } }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        internal const val LOG_COUNT_PERIOD_MS = 1_000L

        fun fromContainer() = HomeViewModel(
            ShellDeps.bike, ShellDeps.ride, ShellDeps.navigation, ShellDeps.alerts, AppContainer.settings,
            AppContainer.fuel, AppContainer.service, AppContainer.parked, AppContainer.trips, AppContainer.places, ShellDeps.log,
        )
    }
}
