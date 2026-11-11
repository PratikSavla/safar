package app.safar.ui.trips

import app.safar.core.BikeState
import app.safar.core.RideState
import app.safar.data.TrackPoint
import app.safar.data.Trip
import app.safar.data.TripRepository
import app.safar.data.TripStop
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
        var lastNote: String? = "UNSET"
        var updatedTrip: Trip? = null
        override val trips: Flow<List<Trip>> = flowOf(listOfNotNull(saved))
        override fun trip(id: Long): Flow<Trip?> = flowOf(saved)
        override fun track(tripId: Long): Flow<List<TrackPoint>> = flowOf(emptyList())
        override fun stops(tripId: Long): Flow<List<TripStop>> = flowOf(emptyList())
        override suspend fun start(trip: Trip): Long = error("not used")
        override suspend fun update(trip: Trip) { updatedTrip = trip }
        override suspend fun addPoints(points: List<TrackPoint>) = Unit
        override suspend fun replaceTrack(tripId: Long, points: List<TrackPoint>) = Unit
        override suspend fun delete(id: Long) { deleted += id }
        override suspend fun setNote(id: Long, note: String?) { lastNote = note }
        override suspend fun merge(tripIds: List<Long>, title: String): Long = error("not used")
        override suspend fun undoMerge(mergedId: Long, originals: List<Trip>, tracks: Map<Long, List<TrackPoint>>): Unit = error("not used")
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

    @Test
    fun `saveNote with text saves the note`() = runTest(dispatcher) {
        val trip = Trip(7, "Evening ride", 1_000L, 2_000L, 42.0, 80f, 40f, 100, 142, 5, 4)
        val repo = FakeTrips(trip)
        val vm = TripViewModel(7, repo, MutableStateFlow(RideState()), MutableStateFlow(BikeState()))

        vm.saveNote("  Nice twisty roads  ")
        advanceUntilIdle()
        assertEquals("Nice twisty roads", repo.lastNote)
        assertEquals("Note saved", vm.notices.current.value?.second)
    }

    @Test
    fun `saveNote with blank clears the note`() = runTest(dispatcher) {
        val trip = Trip(7, "Evening ride", 1_000L, 2_000L, 42.0, 80f, 40f, 100, 142, 5, 4)
        val repo = FakeTrips(trip)
        val vm = TripViewModel(7, repo, MutableStateFlow(RideState()), MutableStateFlow(BikeState()))

        vm.saveNote("   ")
        advanceUntilIdle()
        assertEquals(null, repo.lastNote)
        assertEquals("Note removed", vm.notices.current.value?.second)
    }

    @Test
    fun `endTrip finishes a recording ride and sets endMillis`() = runTest(dispatcher) {
        val trip = Trip(7, "Live ride", 1_000L, null, 0.0, 0f, 0f, null, null, null, null)
        val repo = FakeTrips(trip)
        val ride = MutableStateFlow(RideState(recordingTripId = 7))
        val vm = TripViewModel(7, repo, ride, MutableStateFlow(BikeState()), now = { 5_000L })

        vm.endTrip()
        advanceUntilIdle()
        assertEquals(5_000L, repo.updatedTrip?.endMillis)
        assertEquals("Ride ended", vm.notices.current.value?.second)
    }
}
