package app.safar.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrackThinningTest {
    private class FakeTrips(trips: List<Trip>, points: List<TrackPoint>) : TripRepository {
        val saved = trips.associateBy { it.id }.toMutableMap()
        val points = points.toMutableList()
        val replaced = mutableListOf<Long>()
        var failOn: Long? = null

        override val trips: Flow<List<Trip>> get() = flowOf(saved.values.toList())
        override fun trip(id: Long): Flow<Trip?> = flowOf(saved[id])
        override fun track(tripId: Long): Flow<List<TrackPoint>> = flowOf(points.filter { it.tripId == tripId })
        override suspend fun start(trip: Trip): Long = error("not used")
        override suspend fun update(trip: Trip) = error("not used")
        override suspend fun addPoints(points: List<TrackPoint>) = error("not used")
        override suspend fun delete(id: Long) = error("not used")

        override suspend fun replaceTrack(tripId: Long, points: List<TrackPoint>) {
            if (tripId == failOn) throw IOException("disk full")
            replaced += tripId
            this.points.removeAll { it.tripId == tripId }
            this.points += points
        }
    }

    private class MemoryFlags : OneTimeFlags {
        val set = mutableSetOf<String>()
        override suspend fun isSet(name: String) = name in set
        override suspend fun set(name: String) { set += name }
    }

    private fun trip(id: Long, ended: Boolean) = Trip(
        id = id, title = "Morning ride", startMillis = 0, endMillis = if (ended) 600_000 else null, distanceKm = 6.6,
        topSpeedKmh = 40f, avgSpeedKmh = 40f, odoStartKm = null, odoEndKm = null, fuelBarsStart = null, fuelBarsEnd = null,
    )

    /** [n] points one second apart, about 11 m north each: a straight line. */
    private fun straight(tripId: Long, n: Int) = List(n) { TrackPoint(tripId, it * 1_000L, 12.0 + it * 0.0001, 77.0, 40f) }

    @Test
    fun passThinsFinishedTripsOnceAndLeavesTheOpenOne() = runTest {
        val repo = FakeTrips(
            listOf(trip(1, ended = true), trip(2, ended = true), trip(3, ended = false)),
            straight(1, 600) + straight(2, 2) + straight(3, 300),
        )
        val flags = MemoryFlags()
        assertEquals(598, thinFinishedTracks(repo, flags))
        assertEquals(listOf(1L), repo.replaced, "a 2-point track is not rewritten")
        assertEquals(2, repo.points.count { it.tripId == 1L })
        assertEquals(300, repo.points.count { it.tripId == 3L }, "the open trip is still recording")
        assertTrue(TRACK_THINNING_FLAG in flags.set)
        assertNull(thinFinishedTracks(repo, flags), "second start: already done")
        assertEquals(listOf(1L), repo.replaced)
    }

    @Test
    fun passCutShortRunsAgainOnTheNextStart() = runTest {
        val repo = FakeTrips(listOf(trip(1, ended = true), trip(2, ended = true)), straight(1, 100) + straight(2, 100))
        repo.failOn = 2
        val flags = MemoryFlags()
        assertFailsWith<IOException> { thinFinishedTracks(repo, flags) }
        assertFalse(TRACK_THINNING_FLAG in flags.set)
        repo.failOn = null
        assertEquals(98, thinFinishedTracks(repo, flags), "trip 1 is already thin; only trip 2 loses points")
        assertEquals(2, repo.points.count { it.tripId == 1L })
        assertEquals(2, repo.points.count { it.tripId == 2L })
    }

    @Test
    fun thinTrackReportsCountsAndSkipsTracksWithNothingToDrop() = runTest {
        val repo = FakeTrips(listOf(trip(1, ended = true)), straight(1, 50))
        assertEquals(TrackThinning(before = 50, after = 2), repo.thinTrack(1))
        assertEquals(TrackThinning(before = 2, after = 2), repo.thinTrack(1))
        assertEquals(TrackThinning(before = 0, after = 0), repo.thinTrack(9))
        assertEquals(listOf(1L), repo.replaced)
    }
}
