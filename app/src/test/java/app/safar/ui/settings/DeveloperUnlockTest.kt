package app.safar.ui.settings

import app.safar.data.SavedBikeRecord
import app.safar.data.Settings
import app.safar.ui.testing.FakeBike
import app.safar.ui.testing.FakeRide
import app.safar.ui.testing.FakeSavedBike
import app.safar.ui.testing.FakeSettings
import app.safar.ui.testing.MainDispatcherRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DeveloperUnlockTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val store = FakeSettings(Settings(riderName = "Rider"))
    private val vm by lazy {
        SettingsViewModel(store, FakeRide(), FakeBike(), FakeSavedBike(SavedBikeRecord("SBS21A1B2C3D", "00:00:00:00:00:01")), CoroutineScope(main.dispatcher))
    }

    @Test
    fun sevenQuickTapsCompleteARun() {
        val taps = VersionTaps()
        val results = (0 until 7).map { taps.tap(1_000L + it * 300) }
        assertEquals(List(6) { false } + true, results)
        // The count starts again after a completed run.
        assertFalse(taps.tap(4_000))
    }

    @Test
    fun aLongPauseStartsTheCountAgain() {
        val taps = VersionTaps(needed = 3, maxGapMs = 1_500)
        assertFalse(taps.tap(0))
        assertFalse(taps.tap(1_000))
        assertFalse(taps.tap(2_501))
        assertFalse(taps.tap(3_000))
        assertTrue(taps.tap(4_000))
    }

    @Test
    fun versionTapsTurnDeveloperModeOnThenOff() = runTest {
        repeat(6) { assertNull(vm.tapVersion(10_000L + it * 200)) }
        assertEquals(true, vm.tapVersion(11_400))
        advanceUntilIdle()
        assertTrue(store.settings.value.developerMode)

        repeat(6) { assertNull(vm.tapVersion(20_000L + it * 200)) }
        assertEquals(false, vm.tapVersion(21_400))
        advanceUntilIdle()
        assertFalse(store.settings.value.developerMode)
    }

    @Test
    fun theSwitchSavesDeveloperMode() = runTest {
        vm.setDeveloperMode(true)
        advanceUntilIdle()
        assertTrue(store.settings.value.developerMode)
        assertEquals("Rider", store.settings.value.riderName)
    }
}
