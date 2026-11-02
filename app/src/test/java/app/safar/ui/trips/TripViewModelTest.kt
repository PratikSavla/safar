package app.safar.ui.trips

import app.safar.core.BikeState
import app.safar.core.RideState
import app.safar.data.TrackPoint
import app.safar.data.Trip
import app.safar.data.TripRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TripViewModelTest {
    private class FakeTrips : TripRepository {
        val deleted = mutableListOf<Long>()
        override val trips: Flow<List<Trip>> = flowOf(emptyList())
        override fun trip(id: Long): Flow<Trip?> = flowOf(null)
        override fun track(tripId: Long): Flow<List<TrackPoint>> = flowOf(emptyList())
        override suspend fun start(trip: Trip): Long = error("not used")
        override suspend fun update(trip: Trip) = Unit
        override suspend fun addPoints(points: List<TrackPoint>) = Unit
        override suspend fun delete(id: Long) { deleted += id }
    }

    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the ride being recorded is not deleted, a finished one is`() = runTest(dispatcher) {
        val repo = FakeTrips()
        val ride = MutableStateFlow(RideState(recordingTripId = 7))
        val vm = TripViewModel(7, repo, ride, MutableStateFlow(BikeState()))

        vm.delete()
        advanceUntilIdle()
        assertEquals(emptyList(), repo.deleted)
        assertFalse(vm.deleted.value)
        assertEquals(RECORDING_DELETE, vm.notices.current.value?.second)

        ride.value = RideState(recordingTripId = null)
        vm.delete()
        advanceUntilIdle()
        assertEquals(listOf(7L), repo.deleted)
        assertTrue(vm.deleted.value)
    }
}
