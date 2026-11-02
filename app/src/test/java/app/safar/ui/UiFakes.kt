package app.safar.ui

import app.safar.core.AlertCenter
import app.safar.core.AlertsState
import app.safar.core.BikeCatalog
import app.safar.core.BikeController
import app.safar.core.BikeState
import app.safar.core.ClusterReading
import app.safar.core.NavState
import app.safar.core.NavigationBridge
import app.safar.core.RideState
import app.safar.core.RideTracker
import app.safar.core.SavedBike
import app.safar.data.Settings
import app.safar.data.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow

/** Synthetic bike for screen tests (CLAUDE.md test data). */
const val TEST_BIKE = "SBM01A1B2C3D"
const val TEST_ADDRESS = "00:11:22:33:44:55"

val testSaved = SavedBike(TEST_BIKE, TEST_ADDRESS, BikeCatalog.forBleName(TEST_BIKE))

fun testReading(at: Long = 1_000L, bars: Int? = 4, gear: Char? = 'N') = ClusterReading(
    speedKmh = 0, odometerKm = 12_345, tripAKm = 142.6, tripBKm = 1_204.3, fuelBars = bars, gear = gear,
    raw23 = 0, raw25 = 0, receivedAtMillis = at,
)

class FakeSettings(initial: Settings = Settings()) : SettingsStore {
    override val settings = MutableStateFlow(initial)
    var updates = 0
    override suspend fun update(transform: (Settings) -> Settings) {
        updates += 1
        settings.value = transform(settings.value)
    }
}

class FakeBike(initial: BikeState = BikeState()) : BikeController {
    override val state = MutableStateFlow(initial)
    val calls = mutableListOf<String>()
    override fun startScan() { calls += "startScan"; state.value = state.value.copy(scanning = true) }
    override fun stopScan() { calls += "stopScan"; state.value = state.value.copy(scanning = false) }
    override fun connect(address: String) { calls += "connect:$address" }
    override fun disconnect() { calls += "disconnect" }
    override fun forgetBike() { calls += "forget" }
    override fun sendIconTest(iconCode: Int) { calls += "icon:$iconCode" }
}

class FakeRide(initial: RideState = RideState()) : RideTracker {
    override val state = MutableStateFlow(initial)
}

class FakeAlerts(initial: AlertsState = AlertsState()) : AlertCenter {
    override val state = MutableStateFlow(initial)
    override fun clearMissedCalls() = Unit
    override fun clearMessages() = Unit
}

class FakeNav(initial: NavState = NavState()) : NavigationBridge {
    override val state = MutableStateFlow(initial)
    override fun openInMaps(label: String, query: String) = Unit
    override fun end() = Unit
}
