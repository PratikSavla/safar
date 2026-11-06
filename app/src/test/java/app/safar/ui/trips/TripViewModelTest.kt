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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TripViewModelTest {
    private class FakeTrips(private val saved: Trip? = null) : TripRepository {
        val deleted = mutableListOf<Long>()
        override val trips: Flow<List<Trip>> = flowOf(listOfNotNull(saved))
        override fun trip(id: Long): Flow<Trip?> = flowOf(saved)
        override fun track(tripId: Long): Flow<List<TrackPoint>> = flowOf(emptyList())
        override suspend fun start(trip: Trip): Long = error("not used")
        override suspend fun update(trip: Trip) = Unit
        override suspend fun addPoints(points: List<TrackPoint>) = Unit
        override suspend fun replaceTrack(tripId: Long, points: List<TrackPoint>) = Unit
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

    @Test
    fun `the page's day label carries the start time in the phone's clock format`() = runTest(dispatcher) {
        val start = LocalDateTime.of(2026, 11, 6, 17, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val trip = Trip(7, "Evening ride", start, start + 3_600_000, 42.0, 80f, 40f, 100, 142, 5, 4)
        val ride = MutableStateFlow(RideState())
        val bike = MutableStateFlow(BikeState())

        fun dayOn(is24h: Boolean) = TripViewModel(7, FakeTrips(trip), ride, bike, zone = ZoneOffset.UTC, is24h = is24h)

        val twelve = dayOn(is24h = false).state.first { it is TripDetailState.Ready } as TripDetailState.Ready
        assertEquals("FRI 06 NOV 5:00PM", twelve.trip.day)
        val twentyFour = dayOn(is24h = true).state.first { it is TripDetailState.Ready } as TripDetailState.Ready
        assertEquals("FRI 06 NOV 17:00", twentyFour.trip.day)
    }
}
