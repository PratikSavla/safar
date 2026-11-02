package app.safar.ui.home

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
import app.safar.ui.service.odoNow
import app.safar.ui.shell.ShellDeps
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Everything Today shows. [entryNo] numbers the journal page ("Entry № 12"). */
data class HomeUi(
    val riderName: String,
    val modelName: String?,
    val conn: ConnUi,
    val autoRideOn: Boolean,
    val ledger: LedgerUi?,
    val speed: SpeedUi,
    val counts: CountsUi,
    val nav: NavStripUi?,
    val garage: List<GarageTile>,
    val entryNo: Int,
)

/** Live link and phone state. */
data class HomeLive(val bike: BikeState, val ride: RideState, val nav: NavState, val alerts: AlertsState, val settings: Settings)

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
        nav, alertCount, bike.link, live.settings.debugLog, logCount,
    )
    return HomeUi(
        riderName = live.settings.riderName.trim(),
        modelName = modelName(bike),
        conn = connUi(bike),
        autoRideOn = live.settings.autoRideMode,
        ledger = ledgerUi(bike, fuel),
        speed = speedUi(live.ride),
        counts = CountsUi(live.alerts.missed, live.alerts.messages),
        nav = nav,
        garage = garageTiles(garage),
        entryNo = stored.trips.size + 1,
    )
}

class HomeViewModel(
    private val bike: BikeController,
    ride: RideTracker,
    navigation: NavigationBridge,
    alerts: AlertCenter,
    settings: SettingsStore,
    fuel: FuelRepository,
    service: ServiceRepository,
    parked: ParkedRepository,
    trips: TripRepository,
    places: PlaceRepository,
    log: DebugLog,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val live = combine(bike.state, ride.state, navigation.state, alerts.state, settings.settings, ::HomeLive)
    private val stored = combine(fuel.fuelUps, service.items, parked.parked, trips.trips, places.places.map { it.size }, ::HomeStored)

    val ui: StateFlow<HomeUi?> = combine(live, stored, log.records.map { it.size }) { l, s, n -> homeUi(l, s, n, clock()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** Direct connect to the saved cluster (docs 2.4). */
    fun connectSaved() {
        bike.state.value.saved?.let { bike.connect(it.address) }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun fromContainer() = HomeViewModel(
            ShellDeps.bike, ShellDeps.ride, ShellDeps.navigation, ShellDeps.alerts, AppContainer.settings,
            AppContainer.fuel, AppContainer.service, AppContainer.parked, AppContainer.trips, AppContainer.places, ShellDeps.log,
        )
    }
}
