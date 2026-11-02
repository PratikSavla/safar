package app.safar.ui.settings

import app.safar.core.BikeCatalog
import app.safar.core.RideState
import app.safar.data.SavedBikeRecord
import app.safar.data.Settings
import app.safar.data.ThemeMode
import app.safar.ui.testing.FakeBike
import app.safar.ui.testing.FakeRide
import app.safar.ui.testing.FakeSavedBike
import app.safar.ui.testing.FakeSettings
import app.safar.ui.testing.MainDispatcherRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val store = FakeSettings(Settings(riderName = "Rider"))
    private val ride = FakeRide()
    private val flushed = CoroutineScope(main.dispatcher)
    private val vm by lazy { newVm() }

    private fun newVm() = SettingsViewModel(store, ride, FakeBike(), FakeSavedBike(SavedBikeRecord("SBS21A1B2C3D", "00:00:00:00:00:01")), flushed)

    @Test
    fun clusterNameIsCleanedUpperCase() {
        assertEquals("ALEX", clusterName("  Alex "))
        assertEquals("4/19", nameCounter("Alex"))
        assertEquals("", clusterName("   "))
    }

    @Test
    fun tankParsing() {
        assertEquals(TankInput.Valid(12.5), parseTank("12,5", 5.2))
        assertEquals(TankInput.Valid(null), parseTank("5.2", 5.2))
        assertEquals(TankInput.Valid(null), parseTank("", 5.2))
        assertEquals(TankInput.Invalid("Enter the tank size in litres"), parseTank("", null))
        assertEquals(TankInput.Invalid("Use a number, like 12.5"), parseTank("twelve", null))
        assertEquals(TankInput.Invalid("Tank size must be between 1 and 40 L"), parseTank("41", null))
        assertEquals("12", litresText(12.0))
        assertEquals("Model spec 5.2 L (Avenis) · using yours: 6 L", tankSubtitle(BikeCatalog.forBleName("SBS21A1B2C3D"), 6.0))
    }

    @Test
    fun rangeFollowsTheSavedBike() = runTest {
        backgroundScope.launch(main.dispatcher) { vm.state.collect {} }
        assertEquals(5..120, vm.state.value.alertRange)
        assertEquals("scooter", familyWord(vm.state.value.model))
        assertEquals("5.2", vm.state.value.tankText)
        assertEquals(DEFAULT_ALERT_RANGE, alertRangeOf(null))
    }

    @Test
    fun nameIsSavedAfterTypingStops() = runTest {
        backgroundScope.launch(main.dispatcher) { vm.state.collect {} }
        vm.setRiderName("Al")
        vm.setRiderName("Alex")
        assertEquals("Alex", vm.state.value.nameText)
        assertEquals("Rider", store.settings.value.riderName)
        advanceUntilIdle()
        assertEquals("Alex", store.settings.value.riderName)
        assertEquals(1, store.writes)
        vm.setRiderName("   ")
        advanceUntilIdle()
        assertEquals("Alex", store.settings.value.riderName)
        assertEquals("The cluster needs at least one letter or digit", vm.state.value.nameHint)
    }

    @Test
    fun movingLocksTextButNotSwitches() = runTest {
        backgroundScope.launch(main.dispatcher) { vm.state.collect {} }
        ride.state.value = RideState(speedKmh = 30f, moving = true)
        vm.setRiderName("Alex")
        vm.setTank("9")
        advanceUntilIdle()
        assertEquals("Rider", vm.state.value.nameText)
        assertNull(store.settings.value.tankLitres)
        vm.setTheme(ThemeMode.DARK)
        vm.toggle("debug log") { it.copy(debugLog = true) }
        advanceUntilIdle()
        assertEquals(ThemeMode.DARK, store.settings.value.theme)
        assertEquals(true, store.settings.value.debugLog)
    }

    @Test
    fun tankAndSpeedSaveAndReset() = runTest {
        backgroundScope.launch(main.dispatcher) { vm.state.collect {} }
        vm.setTank("6,0")
        vm.setSpeedAlert(500)
        advanceUntilIdle()
        assertEquals(6.0, store.settings.value.tankLitres)
        assertEquals(120, vm.state.value.speedKmh) // clamped to the scooter range
        vm.setTank("abc")
        assertEquals("Use a number, like 12.5", vm.state.value.tankError)
        vm.resetTank()
        advanceUntilIdle()
        assertNull(store.settings.value.tankLitres)
        assertEquals("5.2", vm.state.value.tankText)
    }

    @Test
    fun editsStillWaitingAreSavedWhenTheScreenCloses() = runTest {
        val owner = ViewModelStore()
        val vm = ViewModelProvider.create(owner, viewModelFactory { initializer { newVm() } })[SettingsViewModel::class]
        backgroundScope.launch(main.dispatcher) { vm.state.collect {} }
        vm.setRiderName("Alex")
        vm.setTank("7")
        vm.setSpeedAlert(90)
        assertEquals("Rider", store.settings.value.riderName)
        owner.clear()
        advanceUntilIdle()
        assertEquals("Alex", store.settings.value.riderName)
        assertEquals(7.0, store.settings.value.tankLitres)
        assertEquals(90, store.settings.value.speedAlertKmh)
        assertEquals(1, store.writes)
    }
}
