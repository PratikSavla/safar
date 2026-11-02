package app.safar.ui.parked

import app.safar.core.NavState
import app.safar.core.NavigationBridge
import app.safar.data.GpsFix
import app.safar.data.ParkedRepository
import app.safar.data.ParkedSpot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ParkedViewModelTest {
    private class FakeParked(initial: ParkedSpot?) : ParkedRepository {
        val spot = MutableStateFlow(initial)
        override val parked: Flow<ParkedSpot?> = spot
        override suspend fun save(spot: ParkedSpot) { this.spot.value = spot }
        override suspend fun clear() { spot.value = null }
    }

    private class FakeNav : NavigationBridge {
        val opened = mutableListOf<Pair<String, String>>()
        override val state: StateFlow<NavState> = MutableStateFlow(NavState())
        override fun openInMaps(label: String, query: String) { opened += label to query }
        override fun end() = Unit
    }

    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `location is used only once allowed, maps opens the spot, park here saves the fix`() = runTest(dispatcher) {
        val repo = FakeParked(ParkedSpot(18.5, 73.8, 8f, 1_000, "Main Rd"))
        val nav = FakeNav()
        val fix = GpsFix(18.499, 73.8, 2_000, 4f)
        val vm = ParkedViewModel(repo, { nav }, { flowOf(fix) }, { flowOf() }, now = { 9_000 })
        val job = launch { vm.state.collect { } }
        assertNull(vm.state.first { !it.loading }.distanceM)

        vm.setLocationAllowed(true)
        advanceUntilIdle()
        assertEquals(111, vm.state.value.distanceM)

        vm.navigateBack()
        assertEquals(listOf("Parked bike" to "18.500000,73.800000"), nav.opened)
        assertEquals("Opening Google Maps…", vm.notices.current.value?.second)

        vm.parkHere()
        advanceUntilIdle()
        assertEquals(ParkedSpot(18.499, 73.8, 4f, 9_000, null), repo.spot.value)
        assertEquals("Parking spot saved", vm.notices.current.value?.second)
        job.cancel()
    }
}
