package app.safar.ui.trips

import app.safar.core.BikeState
import app.safar.core.RideState
import app.safar.data.Trip
import app.safar.ui.testSaved
import app.safar.ui.testing.FakeTripList
import app.safar.ui.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertSame

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
        for (s in 1..3) ride.value = RideState(speedKmh = 20f + s, moving = true, recordingTripId = 3, lastFixAtMillis = s * 1_000L)
        runCurrent()
        val last = seen.last()
        assertEquals(23f, last.live?.speedKmh)
        // Same list instance: the postcards' parameters are unchanged, so they skip recomposition.
        assertSame(first.rows, last.rows)
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
}
