package app.safar.ui.fuel

import app.safar.core.BikeState
import app.safar.core.RideState
import app.safar.data.FuelRepository
import app.safar.data.FuelUp
import app.safar.data.Settings
import app.safar.data.SettingsStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FuelUpViewModelTest {
    /** Holds every add until [gate] completes, like a slow database write. */
    private class SlowFuel : FuelRepository {
        val gate = CompletableDeferred<Unit>()
        val added = mutableListOf<FuelUp>()
        override val fuelUps: Flow<List<FuelUp>> = MutableStateFlow(emptyList())
        override suspend fun add(fuelUp: FuelUp): Long {
            added += fuelUp
            gate.await()
            return 1
        }
        override suspend fun update(fuelUp: FuelUp) = Unit
        override suspend fun delete(id: Long) = Unit
    }

    private class FixedSettings : SettingsStore {
        override val settings: StateFlow<Settings> = MutableStateFlow(Settings())
        override suspend fun update(transform: (Settings) -> Settings) = Unit
    }

    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a double tap on save writes the receipt once and disables save while it is written`() = runTest(dispatcher) {
        val fuel = SlowFuel()
        val vm = FuelUpViewModel(null, fuel, FixedSettings(), MutableStateFlow(BikeState()), MutableStateFlow(RideState()), now = { 5_000 })
        val job = launch { vm.state.collect { } }
        vm.state.first { !it.loading }
        vm.setLitres("10")
        vm.setTotal("1000")
        vm.setOdo("12000")
        assertTrue(vm.state.value.canSave)

        vm.save()
        vm.save()
        advanceUntilIdle()
        assertEquals(1, fuel.added.size)
        assertTrue(vm.state.value.saving)
        assertFalse(vm.state.value.canSave)
        assertNull(vm.done.value)

        fuel.gate.complete(Unit)
        advanceUntilIdle()
        assertEquals("Fuel-up saved. Range updated.", vm.done.value)
        assertEquals(12000, fuel.added.single().odometerKm)
        job.cancel()
    }

    @Test
    fun `the form remembers it was opened from the fuel page only once`() {
        FuelNotices.markOpenedFromFuel()
        assertTrue(FuelNotices.takeOpenedFromFuel())
        assertFalse(FuelNotices.takeOpenedFromFuel())
    }
}
