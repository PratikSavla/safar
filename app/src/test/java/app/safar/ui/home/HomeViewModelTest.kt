package app.safar.ui.home

import app.safar.core.BikeState
import app.safar.core.LinkState
import app.safar.core.LogKind
import app.safar.core.LogRecord
import app.safar.core.RideState
import app.safar.data.Settings
import app.safar.ui.testReading
import app.safar.ui.testSaved
import app.safar.ui.testing.FakeAlerts
import app.safar.ui.testing.FakeBike
import app.safar.ui.testing.FakeFuelUps
import app.safar.ui.testing.FakeLog
import app.safar.ui.testing.FakeNavigation
import app.safar.ui.testing.FakeParked
import app.safar.ui.testing.FakePlaces
import app.safar.ui.testing.FakeRide
import app.safar.ui.testing.FakeServiceItems
import app.safar.ui.testing.FakeSettings
import app.safar.ui.testing.FakeTripList
import app.safar.ui.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/** Today must only emit when something it shows changes: every emission recomposes the page. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val linked = BikeState(link = LinkState.Connected, saved = testSaved, cluster = testReading(at = 1_000), framesReceived = 1)
    private val bike = FakeBike(linked)
    private val ride = FakeRide()
    private val log = FakeLog()

    // Lazy: viewModelScope must start after the rule has set Dispatchers.Main.
    private val vm by lazy {
        HomeViewModel(
            bike, ride, FakeNavigation(), FakeAlerts(), FakeSettings(Settings(developerMode = true)), FakeFuelUps(), FakeServiceItems(), FakeParked(),
            FakeTripList(), FakePlaces(), log, clock = { 10_000L },
        )
    }

    private fun TestScope.uiEmissions(): MutableList<HomeUi?> {
        val seen = mutableListOf<HomeUi?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.ui.toList(seen) }
        runCurrent()
        return seen
    }

    @Test
    fun anUnchangedRepeatInputEmitsNothingNew() = runTest {
        val seen = uiEmissions()
        assertEquals(1, seen.filterNotNull().size)
        // A frame counter Today does not show, then the very same state again.
        bike.state.value = linked.copy(framesReceived = 2)
        bike.state.value = linked.copy(framesReceived = 2)
        runCurrent()
        assertEquals(1, seen.filterNotNull().size)
        // A new odometer reading does show.
        bike.state.value = linked.copy(cluster = testReading(at = 6_000))
        runCurrent()
        assertEquals(2, seen.filterNotNull().size)
    }

    @Test
    fun gpsFixesReachSpeedNotThePage() = runTest {
        val seen = uiEmissions()
        val speeds = mutableListOf<SpeedUi>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.speed.toList(speeds) }
        for (s in 1..5) ride.state.value = RideState(speedKmh = 30f + s, moving = true, lastFixAtMillis = s * 1_000L)
        runCurrent()
        assertEquals(1, seen.filterNotNull().size)
        assertEquals(SpeedUi(35, live = true, fixAt = null), speeds.last())
        // Same whole km/h while moving: the cell says "live", so a new fix is not a new value.
        val count = speeds.size
        ride.state.value = RideState(speedKmh = 35.2f, moving = true, lastFixAtMillis = 6_000)
        runCurrent()
        assertEquals(count, speeds.size)
    }

    @Test
    fun theLogCountIsSampledOncePerSecond() = runTest {
        val seen = uiEmissions()
        assertEquals("0 in memory", seen.last()?.logTile())
        // Four records a second for two seconds (t = 0, 250 ... 1750 ms): one page per second, each with the
        // latest count at that moment (5 at t = 1000), then the final count once the period is over.
        for (n in 1..8) {
            log.records.value = List(n) { LogRecord(it.toLong(), it * 250L, LogKind.EVENT, name = "connected") }
            advanceTimeBy(250)
        }
        advanceTimeBy(HomeViewModel.LOG_COUNT_PERIOD_MS)
        runCurrent()
        val pages = seen.filterNotNull()
        assertEquals(listOf("0 in memory", "5 in memory", "8 in memory"), pages.map { it.logTile() })
    }

    private fun HomeUi.logTile(): String = garage.first { it.key == "log" }.hint
}
