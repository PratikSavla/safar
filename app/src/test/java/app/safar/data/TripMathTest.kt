package app.safar.data

import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TripMathTest {
    /** 0.001 degree of latitude is about 111.2 m. */
    private fun fix(i: Int, sec: Long, acc: Float? = 5f) = GpsFix(12.0 + i * 0.001, 77.0, sec * 1000, acc)

    @Test
    fun haversineKnownDistances() {
        assertEquals(111_195.0, haversineM(0.0, 0.0, 1.0, 0.0), 1.0)
        assertEquals(111_195.0, haversineM(0.0, 0.0, 0.0, 1.0), 1.0)
        assertEquals(0.0, haversineM(12.97, 77.59, 12.97, 77.59))
        // Bengaluru-ish to Mysuru-ish synthetic points: about 127 km.
        assertEquals(127.0, haversineM(12.97, 77.59, 12.30, 76.64) / 1000, 1.5)
    }

    @Test
    fun trackDistanceSumsLegs() {
        val fixes = (0..10).map { fix(it, it * 10L) }
        assertEquals(1.112, trackDistanceKm(fixes), 0.002)
    }

    @Test
    fun dropsInaccurateFixes() {
        val noisy = GpsFix(12.05, 77.05, 15_000, accuracyM = 45f)
        val fixes = listOf(fix(0, 0), fix(1, 10), noisy, fix(2, 20))
        assertEquals(0.2224, trackDistanceKm(fixes), 0.001)
        assertEquals(3, filterTrack(fixes).size)
    }

    @Test
    fun dropsImpossibleJumpsAndStaleFixes() {
        // 0.05 degrees (about 5.6 km) in 10 s implies about 2,000 km/h.
        val jump = GpsFix(12.051, 77.0, 15_000, 5f)
        val stale = fix(2, 10)
        val fixes = listOf(fix(0, 0), fix(1, 10), jump, stale, fix(2, 20))
        assertEquals(0.2224, trackDistanceKm(fixes), 0.001)
    }

    @Test
    fun filterReportsAcceptedMetres() {
        val f = TrackFilter()
        assertEquals(0.0, f.accept(fix(0, 0)))
        assertEquals(111.2, f.accept(fix(1, 10))!!, 0.1) // !! safe: a valid fix is always accepted here
        assertNull(f.accept(fix(2, 10)))
        assertNull(f.accept(fix(3, 30, acc = 31f)))
        assertEquals(fix(1, 10), f.last)
    }

    @Test
    fun avgMovingSpeedSkipsStops() {
        // 111.2 m per 10 s = 40.0 km/h, then a 60 s stop with 1 m drift.
        val moving = (0..5).map { fix(it, it * 10L) }
        val stopped = GpsFix(12.005 + 0.000009, 77.0, 110_000, 5f)
        val more = (6..8).map { fix(it, 110L + (it - 5) * 10L) }
        val speed = avgMovingSpeedKmh(moving + stopped + more)
        assertEquals(40.0, speed, 0.3)
        assertEquals(0.0, avgMovingSpeedKmh(listOf(fix(0, 0))))
    }

    @Test
    fun trackPointConvertsWithoutAccuracy() {
        val p = TrackPoint(tripId = 1, atMillis = 5_000, lat = 12.97, lng = 77.59, speedKmh = 30f)
        assertEquals(GpsFix(12.97, 77.59, 5_000, null), p.toFix())
    }

    @Test
    fun titlesByStartHour() {
        assertEquals("Night ride", autoTripTitle(4))
        assertEquals("Morning ride", autoTripTitle(5))
        assertEquals("Morning ride", autoTripTitle(11))
        assertEquals("Afternoon ride", autoTripTitle(12))
        assertEquals("Evening ride", autoTripTitle(17))
        assertEquals("Evening ride", autoTripTitle(20))
        assertEquals("Night ride", autoTripTitle(21))
        val zone = ZoneId.of("Asia/Kolkata")
        val start = ZonedDateTime.of(2026, 11, 2, 18, 15, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("Evening ride", autoTripTitle(start, zone))
    }
}
