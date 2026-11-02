package app.safar.ui.trips

import app.safar.core.RideState
import app.safar.data.TrackPoint
import app.safar.data.Trip
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TripsMappingTest {
    private val zone: ZoneId = ZoneOffset.ofHoursMinutes(5, 30)

    /** Monday 2026-11-02 18:00 local. */
    private val now = LocalDateTime.of(2026, 11, 2, 18, 0).atZone(zone).toInstant().toEpochMilli()
    private val day = 86_400_000L
    private val min = 60_000L

    private fun trip(id: Long, daysAgo: Int, km: Double, minutes: Long, top: Float, end: Boolean = true) = Trip(
        id = id, title = "Ride $id", startMillis = now - daysAgo * day, endMillis = if (end) now - daysAgo * day + minutes * min else null,
        distanceKm = km, topSpeedKmh = top, avgSpeedKmh = 40f, odoStartKm = 12_000, odoEndKm = 12_000 + km.toInt(),
        fuelBarsStart = 5, fuelBarsEnd = 4,
    )

    private val trips = listOf(trip(4, 1, 42.3, 64, 88f), trip(3, 6, 20.0, 30, 70f), trip(2, 12, 128.6, 142, 104f), trip(1, 40, 8.0, 20, 50f))

    @Test
    fun `week and month totals sum only rides inside the window`() {
        assertEquals(TripTotals(62.3, 94, 2, 88f), tripTotals(trips, now, 7))
        val month = tripTotals(trips, now, 30)
        assertEquals(3, month.rides)
        assertEquals(190.9, month.km, 1e-9)
        assertEquals(236, month.minutes)
        assertEquals(104f, month.topKmh)
        assertEquals(TripTotals(0.0, 0, 0, 0f), tripTotals(emptyList(), now, 7))
    }

    @Test
    fun `duration reads in minutes then hours with padded minutes`() {
        assertEquals("0 min", durationText(0))
        assertEquals("42 min", durationText(42))
        assertEquals("1 h 00", durationText(60))
        assertEquals("2 h 05", durationText(125))
    }

    @Test
    fun `a ride still being written counts up to now`() {
        val open = trip(9, 0, 3.0, 0, 30f, end = false).copy(startMillis = now - 25 * min)
        assertEquals(25, tripMinutes(open, now))
    }

    @Test
    fun `rows keep newest first and number from the oldest`() {
        val rows = tripRows(trips, now, zone)
        assertEquals(listOf(4, 3, 2, 1), rows.map { it.number })
        assertEquals("SUN 01 NOV", rows[0].day)
        assertEquals("№004", entryNumber(rows[0].number))
    }

    @Test
    fun `live ride shows only while recording`() {
        assertNull(liveRide(RideState()))
        val live = liveRide(RideState(speedKmh = 42f, topSpeedKmh = 61f, moving = true, recordingTripId = 9, lastFixAtMillis = now))
        assertEquals(LiveRideUi(42f, 61f, now), live)
        val state = tripsUiState(trips, RideState(), "V-Strom SX", now, zone)
        assertEquals("V-Strom SX", state.bikeName)
        assertEquals(4, state.rows.size)
        assertTrue(!state.loading)
    }

    @Test
    fun `fuel line reports bars used, km per bar or a refill`() {
        assertEquals(TripFuelUi(5, 3, 6, "2 bars used", "≈ 64 km per bar"), tripFuel(5, 3, 6, 128.6))
        assertEquals("1 bar used", tripFuel(5, 4, 6, 40.0)?.aside)
        assertEquals("refilled on the way", tripFuel(2, 6, 6, 80.0)?.note)
        assertEquals("under one bar", tripFuel(4, 4, 6, 10.0)?.note)
        assertNull(tripFuel(null, 4, 6, 10.0))
    }

    @Test
    fun `detail maps sources, number and a filtered track`() {
        val track = listOf(
            TrackPoint(2, 0, 18.5000, 73.8000, 0f),
            TrackPoint(2, 10_000, 18.5010, 73.8000, 40f),
            TrackPoint(2, 20_000, 19.5000, 74.8000, 40f), // impossible jump, dropped
            TrackPoint(2, 30_000, 18.5020, 73.8000, 40f),
        )
        val d = tripDetail(trips[2], trips, track, 6, recordingTripId = null, nowMillis = now, zone = zone)
        assertEquals(2, d.number)
        assertEquals("bike odometer", d.distanceSource)
        assertEquals(3, d.trackLat.size)
        assertEquals(4, d.fixes)
        val gpsOnly = tripDetail(trips[2].copy(odoEndKm = null), trips, emptyList(), 6, null, now, zone)
        assertEquals("phone GPS", gpsOnly.distanceSource)
    }

    @Test
    fun `rename trims, caps and keeps the old title when blank`() {
        assertEquals("Ride to Main Rd", cleanTitle("  Ride to Main Rd ", "Evening ride"))
        assertEquals("Evening ride", cleanTitle("   ", "Evening ride"))
        assertEquals(40, cleanTitle("x".repeat(60), "Old").length)
    }

    @Test
    fun `a postcard thumbnail keeps at most 120 points of the filtered track, ending at the last fix`() {
        // 500 fixes one second apart, about 11 m north each (40 km/h): nothing for the filter to drop.
        val track = List(500) { i -> TrackPoint(1, 1_000L * i, 18.5 + i * 0.0001, 73.8, 40f) }
        val thumb = trackThumb(track)
        assertEquals(THUMB_POINTS, thumb.lat.size)
        assertEquals(THUMB_POINTS, thumb.lng.size)
        assertEquals(18.5 + 499 * 0.0001, thumb.lat.last(), 1e-9)
        assertTrue(thumb.lat.zipWithNext().all { (a, b) -> b > a })
        assertEquals(TrackThumb.Empty, trackThumb(emptyList()))
    }
}
