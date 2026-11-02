package app.safar.phone

import app.safar.core.ClusterReading
import app.safar.data.GpsFix
import app.safar.data.TrackPoint
import app.safar.data.Trip
import app.safar.data.TripRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TripRecorderTest {
    private class FakeTrips : TripRepository {
        val saved = mutableMapOf<Long, Trip>()
        val batches = mutableListOf<List<TrackPoint>>()
        val deleted = mutableListOf<Long>()
        override val trips: Flow<List<Trip>> = emptyFlow()
        override fun trip(id: Long): Flow<Trip?> = emptyFlow()
        override fun track(tripId: Long): Flow<List<TrackPoint>> = emptyFlow()
        override suspend fun start(trip: Trip): Long = (saved.size + 1L).also { saved[it] = trip.copy(id = it) }
        override suspend fun update(trip: Trip) { saved[trip.id] = trip }
        override suspend fun addPoints(points: List<TrackPoint>) { batches += points }
        override suspend fun delete(id: Long) { deleted += id; saved.remove(id) }
    }

    private fun reading(odo: Int, bars: Int?) = ClusterReading(0, odo, 0.0, 0.0, bars, 'N', 0, 29, 0)

    /** 0.001 degree of latitude is about 111 m; one fix every 10 s is about 40 km/h. */
    private fun fix(i: Int) = GpsFix(12.0 + i * 0.001, 77.0, i * 10_000L, 5f)

    @Test
    fun recordsTrackInBatchesAndSavesTotals() = runTest {
        val repo = FakeTrips()
        val recorder = TripRecorder({ repo }, ZoneOffset.UTC)
        val start = 8 * 3_600_000L // 08:00 UTC
        val id = recorder.start(start, reading(12_261, 4))
        assertEquals("Morning ride", repo.saved.getValue(id).title)
        repeat(25) { recorder.onFix(fix(it), 40f + it) }
        assertEquals(listOf(TRACK_BATCH), repo.batches.map { it.size }, "one full batch so far")
        val done = assertNotNull(recorder.end(start + 300_000, reading(12_264, 3)))
        assertEquals(listOf(20, 5), repo.batches.map { it.size })
        assertEquals(2.67, done.distanceKm, 0.01)
        assertEquals(64f, done.topSpeedKmh)
        assertEquals(40.0, done.avgSpeedKmh.toDouble(), 0.5)
        assertEquals(12_261, done.odoStartKm)
        assertEquals(12_264, done.odoEndKm)
        assertEquals(4, done.fuelBarsStart)
        assertEquals(3, done.fuelBarsEnd)
        assertNull(recorder.tripId)
    }

    @Test
    fun shortTripIsDeleted() = runTest {
        val repo = FakeTrips()
        val recorder = TripRecorder({ repo }, ZoneOffset.UTC)
        val id = recorder.start(0, null)
        recorder.onFix(fix(0), 10f)
        recorder.onFix(fix(1), 10f)
        assertNull(recorder.end(60_000, null))
        assertEquals(listOf(id), repo.deleted)
        assertTrue(repo.batches.isEmpty(), "no points written for a discarded trip")
    }

    @Test
    fun movementStartTakesOdometerFromFirstFrame() = runTest {
        val repo = FakeTrips()
        val recorder = TripRecorder({ repo }, ZoneOffset.UTC)
        recorder.start(0, null)
        recorder.onReading(reading(500, null))
        recorder.onReading(reading(501, 5))
        val done = assertNotNull(recorder.end(60_000, reading(503, 5)))
        assertEquals(500, done.odoStartKm)
        assertEquals(5, done.fuelBarsStart)
        assertEquals(3.0, done.distanceKm, 0.0001, "odometer distance when GPS saw nothing")
    }

    @Test
    fun fallbackSpeedFromTwoFixes() {
        assertEquals(40.0f, fallbackSpeedKmh(fix(0), fix(1))!!, 0.1f) // non-null: 10 s apart
        assertNull(fallbackSpeedKmh(null, fix(1)))
        assertNull(fallbackSpeedKmh(fix(0), fix(5)), "50 s gap")
    }
}
