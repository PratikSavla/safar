package app.safar.ui.shell

import androidx.lifecycle.SavedStateHandle
import app.safar.core.RideState
import app.safar.data.Settings
import app.safar.ui.FakeAlerts
import app.safar.ui.FakeBike
import app.safar.ui.FakeNav
import app.safar.ui.FakeRide
import app.safar.ui.FakeSettings
import app.safar.ui.nav.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ShellViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val settings = FakeSettings()
    private val ride = FakeRide()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun shell(saved: SavedStateHandle = SavedStateHandle()) =
        ShellViewModel(saved, settings, FakeBike(), FakeAlerts(), FakeNav(), ride)

    private fun ShellViewModel.routes() = stack.value.entries.map { it.route }

    @Test
    fun startsAtHomeOnceSettingsSayOnboarded() = runTest(dispatcher) {
        val vm = shell()
        settings.settings.value = Settings(onboarded = true)
        runCurrent()
        assertEquals(listOf(Route.Home), vm.routes())
    }

    @Test
    fun startsAtOnboardingWhenSettingsStayDefault() = runTest(dispatcher) {
        val vm = shell()
        advanceTimeBy(800)
        runCurrent()
        assertEquals(listOf(Route.Onboarding), vm.routes())
    }

    @Test
    fun lateSettingsMoveOnboardingToHome() = runTest(dispatcher) {
        val vm = shell()
        advanceTimeBy(800)
        settings.settings.value = Settings(onboarded = true)
        runCurrent()
        assertEquals(listOf(Route.Home), vm.routes())
    }

    @Test
    fun sharedPlaceWaitsForTheStartPage() = runTest(dispatcher) {
        val vm = shell()
        vm.openShared("Main Rd")
        settings.settings.value = Settings(onboarded = true)
        runCurrent()
        assertEquals(listOf(Route.Home, Route.Place(sharedText = "Main Rd")), vm.routes())
    }

    @Test
    fun restoredStackSkipsTheStartDecision() = runTest(dispatcher) {
        val vm = shell(SavedStateHandle(mapOf("shell.stack" to arrayListOf("home", "fuel"))))
        advanceUntilIdle()
        assertEquals(listOf(Route.Home, Route.Fuel), vm.routes())
    }

    @Test
    fun autoRideOpensAndClosesRide() = runTest(dispatcher) {
        settings.settings.value = Settings(onboarded = true, autoRideMode = true)
        val vm = shell()
        runCurrent()
        ride.state.value = RideState(speedKmh = 30f, moving = true)
        runCurrent()
        assertEquals(Route.Ride, vm.stack.value.top?.route)
        assertTrue(vm.autoRide.value)
        ride.state.value = RideState(speedKmh = 0f, moving = false)
        runCurrent()
        assertEquals(listOf(Route.Home), vm.routes())
        assertFalse(vm.autoRide.value)
    }

    @Test
    fun leavingRideByHandStopsAutoClose() = runTest(dispatcher) {
        settings.settings.value = Settings(onboarded = true, autoRideMode = true)
        val vm = shell()
        runCurrent()
        ride.state.value = RideState(speedKmh = 30f, moving = true)
        runCurrent()
        vm.leaveRide(stillMoving = true)
        vm.back()
        assertEquals(listOf(Route.Home), vm.routes())
        assertFalse(vm.autoRide.value)
    }

    @Test
    fun systemBackFromAnAutoRideStopsAutoCloseOfALaterManualRide() = runTest(dispatcher) {
        settings.settings.value = Settings(onboarded = true, autoRideMode = true)
        val vm = shell()
        runCurrent()
        ride.state.value = RideState(speedKmh = 30f, moving = true)
        runCurrent()
        vm.back()
        assertFalse(vm.autoRide.value)
        ride.state.value = RideState(speedKmh = 0f, moving = false)
        runCurrent()
        vm.go(Route.Ride)
        ride.state.value = RideState(speedKmh = 30f, moving = true)
        runCurrent()
        ride.state.value = RideState(speedKmh = 0f, moving = false)
        runCurrent()
        assertEquals(listOf(Route.Home, Route.Ride), vm.routes())
        assertFalse(vm.autoRide.value)
    }

    @Test
    fun aPagePushedOverAnAutoRideClearsTheAutoFlag() = runTest(dispatcher) {
        settings.settings.value = Settings(onboarded = true, autoRideMode = true)
        val vm = shell()
        runCurrent()
        ride.state.value = RideState(speedKmh = 30f, moving = true)
        runCurrent()
        vm.openShared("Main Rd")
        assertFalse(vm.autoRide.value)
    }

    @Test
    fun poppedEntriesLoseTheirViewModelStore() = runTest(dispatcher) {
        settings.settings.value = Settings(onboarded = true)
        val vm = shell()
        runCurrent()
        vm.go(Route.Fuel)
        val id = vm.stack.value.top!!.id // test: top was just pushed
        val store = vm.storeFor(id)
        vm.back()
        val again = vm.storeFor(id)
        assertTrue(store !== again)
    }
}
