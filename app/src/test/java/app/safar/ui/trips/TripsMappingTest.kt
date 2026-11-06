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
    fun `durations and totals read in plain words with units`() {
        assertEquals("42 minutes", spokenDuration(42))
        assertEquals("1 hour", spokenDuration(60))
        assertEquals("1 hour 1 minute", spokenDuration(61))
        assertEquals("2 hours 5 minutes", spokenDuration(125))
        assertEquals("1 ride", ridesText(1))
        assertEquals("3 rides", ridesText(3))
        assertEquals("2 rides · 1 h 34 · top 88 km/h", totalsNote(TripTotals(62.3, 94, 2, 88f)))
        assertEquals("No rides", totalsNote(TripTotals(0.0, 0, 0, 0f)))
    }

    @Test
    fun `a postcard and the live ride speak in sentences`() {
        val row = TripRowUi(4, 4, "SUN 01 NOV 6:00PM", "Evening ride", 42.3, 64, 88f, "Sunday 1 November, 6:00 PM")
        assertEquals("Evening ride, Sunday 1 November, 6:00 PM. 42.3 km in 1 hour 4 minutes, top speed 88 km/h.", postcardSpoken(row))
        assertEquals("42 km/h now · top 61 km/h", liveLine(LiveRideUi(42f, 61f, now)))
        assertEquals("top 61 km/h", liveLine(LiveRideUi(null, 61f, null)))
    }

    @Test
    fun `a ride still being written counts up to now`() {
        val open = trip(9, 0, 3.0, 0, 30f, end = false).copy(startMillis = now - 25 * min)
        assertEquals(25, tripMinutes(open, now))
    }

    @Test
    fun `rows keep newest first and number from the oldest`() {
        val rows = tripRows(trips, now, zone, is24h = false)
        assertEquals(listOf(4, 3, 2, 1), rows.map { it.number })
        assertEquals("SUN 01 NOV 6:00PM", rows[0].day)
        assertEquals("Sunday 1 November, 6:00 PM", rows[0].spokenDay)
        assertEquals("№004", entryNumber(rows[0].number))
        val rows24 = tripRows(trips, now, zone, is24h = true)
        assertEquals("SUN 01 NOV 18:00", rows24[0].day)
        assertEquals("Sunday 1 November, 18:00", rows24[0].spokenDay)
    }

    @Test
    fun `a ride's label carries its start time in the phone's clock format`() {
        val friday = LocalDateTime.of(2026, 11, 6, 17, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals("FRI 06 NOV 5:00PM", dayTimeLabel(friday, zone, is24h = false))
        assertEquals("FRI 06 NOV 17:00", dayTimeLabel(friday, zone, is24h = true))
        assertEquals("Friday 6 November, 5:00 PM", spokenDay(friday, zone, is24h = false))
        assertEquals("Friday 6 November, 17:00", spokenDay(friday, zone, is24h = true))
        val row = tripRows(listOf(trips[0].copy(startMillis = friday)), now, zone, is24h = false).single()
        assertTrue("Friday 6 November, 5:00 PM" in postcardSpoken(row))
    }

    @Test
    fun `live ride shows only while recording`() {
        assertNull(liveRide(RideState()))
        val live = liveRide(RideState(speedKmh = 42f, topSpeedKmh = 61f, moving = true, recordingTripId = 9, lastFixAtMillis = now))
        assertEquals(LiveRideUi(42f, 61f, now, tripId = 9), live)
        val state = tripsUiState(trips, RideState(), "V-Strom SX", now, zone, is24h = false)
        assertEquals("V-Strom SX", state.bikeName)
        assertEquals(4, state.rows.size)
        assertTrue(!state.loading)
    }

    @Test
    fun `fuel line reports bars used, km per bar or a refill`() {
        assertEquals(TripFuelUi(5, 3, 6, "2 bars used", "About 64 km per bar"), tripFuel(5, 3, 6, 128.6))
        assertEquals("1 bar used", tripFuel(5, 4, 6, 40.0)?.aside)
        assertEquals(TripFuelUi(2, 6, 6, "filled up", "You filled up on the way"), tripFuel(2, 6, 6, 80.0))
        assertEquals(TripFuelUi(4, 4, 6, "no change", "Less than one bar used"), tripFuel(4, 4, 6, 10.0))
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
        val d = tripDetail(trips[2], trips, track, 6, recordingTripId = null, nowMillis = now, zone = zone, is24h = false)
        assertEquals(2, d.number)
        assertEquals("WED 21 OCT 6:00PM", d.day)
        assertEquals("WED 21 OCT 18:00", tripDetail(trips[2], trips, track, 6, null, now, zone, is24h = true).day)
        assertEquals("bike odometer", d.distanceSource)
        assertEquals(3, d.trackLat.size)
        assertEquals(4, d.fixes)
        val gpsOnly = tripDetail(trips[2].copy(odoEndKm = null), trips, emptyList(), 6, null, now, zone, is24h = false)
        assertEquals("phone GPS", gpsOnly.distanceSource)
    }

    @Test
    fun `an open trip is counted from its track, not from the zeros in its row`() {
        val open = trip(9, 0, 0.0, 0, 0f, end = false).copy(avgSpeedKmh = 0f, odoStartKm = null, odoEndKm = null)
        // About 111 m north every 6 s: 66.7 km/h, 10 legs.
        val track = List(11) { i -> TrackPoint(9, 6_000L * i, 18.5 + i * 0.001, 73.8, 66f + i % 2) }
        val d = tripDetail(open, listOf(open) + trips, track, 6, recordingTripId = 9, nowMillis = now, zone = zone, is24h = false)
        assertEquals(1.11, d.km, 0.01)
        assertEquals(67f, d.topKmh)
        assertEquals(66.7f, d.avgKmh, 0.2f)
        assertEquals(5, d.number)
        assertTrue(d.recording)
    }

    @Test
    fun `an open trip stays out of the entries and totals`() {
        val open = trip(9, 0, 0.0, 0, 0f, end = false).copy(startMillis = now - 10 * min)
        val state = tripsUiState(listOf(open) + trips, RideState(recordingTripId = 9, topSpeedKmh = 67f), null, now, zone, is24h = false)
        assertEquals(listOf(4, 3, 2, 1), state.rows.map { it.number })
        assertEquals(TripTotals(62.3, 94, 2, 88f), state.week)
        assertEquals(9L, state.live?.tripId)
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
