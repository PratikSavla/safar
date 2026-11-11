package app.safar.data

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TripMergerTest {
    private fun sampleTrip(
        id: Long,
        startMillis: Long,
        endMillis: Long?,
        distanceKm: Double = 10.0,
        topSpeedKmh: Float = 60f,
        avgSpeedKmh: Float = 40f,
        odoStartKm: Int? = 100,
        odoEndKm: Int? = 110,
        fuelBarsStart: Int? = 6,
        fuelBarsEnd: Int? = 5,
    ) = Trip(
        id = id,
        title = "Trip $id",
        startMillis = startMillis,
        endMillis = endMillis,
        distanceKm = distanceKm,
        topSpeedKmh = topSpeedKmh,
        avgSpeedKmh = avgSpeedKmh,
        odoStartKm = odoStartKm,
        odoEndKm = odoEndKm,
        fuelBarsStart = fuelBarsStart,
        fuelBarsEnd = fuelBarsEnd,
    )

    private fun pt(tripId: Long, atMillis: Long, lat: Double = 12.0, lng: Double = 77.0, speed: Float = 30f) =
        TrackPoint(tripId = tripId, atMillis = atMillis, lat = lat, lng = lng, speedKmh = speed)

    @Test
    fun requiresAtLeastTwoTrips() {
        val trip = sampleTrip(1, 1000, 2000)
        assertFailsWith<IllegalArgumentException> {
            mergeTrips(listOf(trip), emptyMap(), "Merged")
        }
    }

    @Test
    fun twoTripsMergeBasicStatsAndTracks() {
        val t1 = sampleTrip(1, 1000, 2000, distanceKm = 10.0, topSpeedKmh = 50f, avgSpeedKmh = 40f, odoStartKm = 100, odoEndKm = 110)
        val t2 = sampleTrip(2, 3000, 5000, distanceKm = 15.0, topSpeedKmh = 70f, avgSpeedKmh = 55f, odoStartKm = 110, odoEndKm = 125)

        val pts1 = listOf(pt(1, 1200, 12.1, 77.1), pt(1, 1800, 12.2, 77.2))
        val pts2 = listOf(pt(2, 3200, 12.3, 77.3), pt(2, 4800, 12.4, 77.4))
        val tracks = mapOf(1L to pts1, 2L to pts2)

        val result = mergeTrips(listOf(t2, t1), tracks, "Combined Ride")
        val merged = result.trip

        assertEquals(0L, merged.id)
        assertEquals("Combined Ride", merged.title)
        assertEquals(1000L, merged.startMillis)
        assertEquals(5000L, merged.endMillis)
        assertEquals(25.0, merged.distanceKm, 0.001)
        assertEquals(70f, merged.topSpeedKmh)
        assertEquals(50f, merged.avgSpeedKmh, 0.01f) // (40*1000 + 55*2000) / 3000 = 50.0
        assertEquals(100, merged.odoStartKm)
        assertEquals(125, merged.odoEndKm)
        assertNull(merged.note)

        assertEquals(4, result.track.size)
        assertEquals(listOf(1200L, 1800L, 3200L, 4800L), result.track.map { it.atMillis })

        assertEquals(1, result.stops.size)
        val stop = result.stops.first()
        assertEquals(0, stop.index)
        assertEquals(12.2, stop.lat, 0.001)
        assertEquals(77.2, stop.lng, 0.001)
        assertEquals(1800L, stop.atMillis)
        assertEquals(0L, stop.tripId)
    }

    @Test
    fun threeTripsProduceTwoStops() {
        val t1 = sampleTrip(1, 1000, 2000)
        val t2 = sampleTrip(2, 2500, 3500)
        val t3 = sampleTrip(3, 4000, 5000)

        val tracks = mapOf(
            1L to listOf(pt(1, 1900, 12.1, 77.1)),
            2L to listOf(pt(2, 3400, 12.2, 77.2)),
            3L to listOf(pt(3, 4900, 12.3, 77.3)),
        )

        val result = mergeTrips(listOf(t1, t2, t3), tracks, "Three legs")
        assertEquals(2, result.stops.size)
        assertEquals(0, result.stops[0].index)
        assertEquals(1900L, result.stops[0].atMillis)
        assertEquals(1, result.stops[1].index)
        assertEquals(3400L, result.stops[1].atMillis)
    }

    @Test
    fun tripWithoutGpsTrackGracefullySkipsStop() {
        val t1 = sampleTrip(1, 1000, 2000)
        val t2 = sampleTrip(2, 3000, 4000)
        // t1 has no track points
        val tracks = mapOf(2L to listOf(pt(2, 3500, 12.2, 77.2)))

        val result = mergeTrips(listOf(t1, t2), tracks, "Missing track")
        assertTrue(result.stops.isEmpty())
        assertEquals(1, result.track.size)
    }

    @Test
    fun zeroDurationFallsBackToZeroAvgSpeed() {
        val t1 = sampleTrip(1, 1000, 1000, avgSpeedKmh = 30f)
        val t2 = sampleTrip(2, 1000, 1000, avgSpeedKmh = 40f)

        val result = mergeTrips(listOf(t1, t2), emptyMap(), "Zero duration")
        assertEquals(0f, result.trip.avgSpeedKmh)
    }
}
