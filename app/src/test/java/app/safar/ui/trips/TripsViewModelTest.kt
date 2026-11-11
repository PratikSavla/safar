package app.safar.ui.trips

import app.safar.core.BikeState
import app.safar.core.RideState
import app.safar.data.TrackPoint
import app.safar.data.Trip
import app.safar.data.TripRepository
import app.safar.ui.testSaved
import app.safar.ui.testing.FakeTripList
import app.safar.ui.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The journal rebuilds its postcards when trips change, not on every 1 Hz GPS fix of the ride being recorded. */
@OptIn(ExperimentalCoroutinesApi::class)
class TripsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val day = 86_400_000L
    private val trips = FakeTripList(
        listOf(
            Trip(3, "Recording", 9 * day, null, 0.0, 0f, 0f, null, null, null, null),
            Trip(2, "Main Rd", 8 * day, 8 * day + 3_600_000, 42.0, 80f, 40f, 100, 142, 5, 4),
            Trip(1, "NH 48", 7 * day, 7 * day + 1_800_000, 21.0, 70f, 42f, 79, 100, 6, 5),
        ),
    )
    private val ride = MutableStateFlow(RideState(recordingTripId = 3))
    private val bike = MutableStateFlow(BikeState(saved = testSaved))
    private val vm by lazy { TripsViewModel(trips, ride, bike, now = { 10 * day }, zone = ZoneOffset.UTC) }

    @Test
    fun gpsFixesUpdateTheLiveCardAndKeepTheSameRows() = runTest {
        val seen = mutableListOf<TripsUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.toList(seen) }
        runCurrent()
        val first = seen.last()
        assertEquals(listOf(2L, 1L), first.rows.map { it.id })
        assertEquals(listOf("FRI 09 JAN 12:00AM", "THU 08 JAN 12:00AM"), first.rows.map { it.day })
        for (s in 1..3) ride.value = RideState(speedKmh = 20f + s, moving = true, recordingTripId = 3, lastFixAtMillis = s * 1_000L)
        runCurrent()
        val last = seen.last()
        assertEquals(23f, last.live?.speedKmh)
        // Same list instance: the postcards' parameters are unchanged, so they skip recomposition.
        assertSame(first.rows, last.rows)
    }

    @Test
    fun aTwentyFourHourPhoneShowsTwentyFourHourStartTimes() = runTest {
        val vm24 = TripsViewModel(trips, ride, bike, now = { 10 * day }, zone = ZoneOffset.UTC, is24h = true)
        val seen = mutableListOf<TripsUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm24.state.toList(seen) }
        runCurrent()
        assertEquals(listOf("FRI 09 JAN 00:00", "THU 08 JAN 00:00"), seen.last().rows.map { it.day })
    }

    @Test
    fun frameCountsDoNotReEmit() = runTest {
        val seen = mutableListOf<TripsUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.toList(seen) }
        runCurrent()
        val count = seen.size
        bike.value = bike.value.copy(framesReceived = 7)
        ride.value = ride.value.copy()
        runCurrent()
        assertEquals(count, seen.size)
    }

    @Test
    fun `toggleSelect adds and removes ids, clearSelection empties`() = runTest {
        assertEquals(emptySet(), vm.selection.value)
        vm.toggleSelect(1L)
        assertEquals(setOf(1L), vm.selection.value)
        vm.toggleSelect(2L)
        assertEquals(setOf(1L, 2L), vm.selection.value)
        vm.toggleSelect(1L)
        assertEquals(setOf(2L), vm.selection.value)
        vm.clearSelection()
        assertEquals(emptySet(), vm.selection.value)
    }

    @Test
    fun `merge with fewer than two rides shows notice`() = runTest {
        vm.toggleSelect(1L)
        vm.merge()
        runCurrent()
        assertEquals("Select at least 2 rides to merge.", vm.notices.current.value?.second)
    }

    @Test
    fun `merge with recording ride shows notice`() = runTest {
        vm.toggleSelect(3L) // 3 is currently recording in ride StateFlow
        vm.toggleSelect(2L)
        vm.merge()
        runCurrent()
        assertEquals("Can’t merge a ride that’s still recording.", vm.notices.current.value?.second)
    }

    private class MergeFakeTrips(
        initial: List<Trip>,
        private val base: FakeTripList = FakeTripList(initial),
    ) : TripRepository by base {
        var mergedIds: List<Long>? = null
        var mergedTitle: String? = null
        var undoneId: Long? = null

        override fun track(tripId: Long): Flow<List<TrackPoint>> = flowOf(emptyList())

        override suspend fun merge(tripIds: List<Long>, title: String): Long {
            mergedIds = tripIds
            mergedTitle = title
            return 99L
        }

        override suspend fun undoMerge(mergedId: Long, originals: List<Trip>, tracks: Map<Long, List<TrackPoint>>) {
            undoneId = mergedId
        }
    }

    @Test
    fun `merge merges selected rides, enables undo, and undoMerge reverts`() = runTest {
        val repo = MergeFakeTrips(
            listOf(
                Trip(2, "Main Rd", 8 * day, 8 * day + 3_600_000, 42.0, 80f, 40f, 100, 142, 5, 4),
                Trip(1, "NH 48", 7 * day, 7 * day + 1_800_000, 21.0, 70f, 42f, 79, 100, 6, 5),
            ),
        )
        val testVm = TripsViewModel(repo, ride, bike, now = { 10 * day }, zone = ZoneOffset.UTC)
        testVm.toggleSelect(1L)
        testVm.toggleSelect(2L)
        testVm.merge()
        runCurrent()

        assertEquals(listOf(1L, 2L), repo.mergedIds)
        assertEquals("NH 48 + Main Rd", repo.mergedTitle)
        assertEquals(emptySet(), testVm.selection.value)
        assertTrue(testVm.canUndo.value)
        assertEquals("Rides merged", testVm.notices.current.value?.second)

        testVm.undoMerge()
        runCurrent()
        assertEquals(99L, repo.undoneId)
        assertFalse(testVm.canUndo.value)
        assertEquals("Merge undone", testVm.notices.current.value?.second)
    }
}
