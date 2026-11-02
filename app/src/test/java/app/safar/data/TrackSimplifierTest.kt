package app.safar.data

import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrackSimplifierTest {
    private val lat0 = 12.97
    private val lng0 = 77.59
    private val metresPerDegLat = 111_195.08
    private val metresPerDegLng = metresPerDegLat * cos(Math.toRadians(lat0))

    /** A point [eastM] / [northM] metres from a fixed origin, [second] seconds into the ride. */
    private fun at(second: Int, eastM: Double, northM: Double, kmh: Float = 40f) =
        TrackPoint(TRIP, second * 1_000L, lat0 + northM / metresPerDegLat, lng0 + eastM / metresPerDegLng, kmh)

    private fun eastOf(p: TrackPoint) = (p.lng - lng0) * metresPerDegLng
    private fun northOf(p: TrackPoint) = (p.lat - lat0) * metresPerDegLat

    /** Metres from [p] to the segment [a]-[b], measured independently of the simplifier's projection. */
    private fun offLine(p: TrackPoint, a: TrackPoint, b: TrackPoint): Double {
        val (ax, ay) = eastOf(a) to northOf(a)
        val (dx, dy) = eastOf(b) - ax to northOf(b) - ay
        val (px, py) = eastOf(p) - ax to northOf(p) - ay
        val lengthSq = dx * dx + dy * dy
        val t = if (lengthSq == 0.0) 0.0 else ((px * dx + py * dy) / lengthSq).coerceIn(0.0, 1.0)
        return hypot(px - t * dx, py - t * dy)
    }

    /** About 11 m/s north for [n] seconds: 40 km/h in a straight line. */
    private fun straight(n: Int, kmh: (Int) -> Float = { 40f }) = List(n) { at(it, 0.0, it * 11.1, kmh(it)) }

    /** A road that weaves 30 m either side of north with a 400 m wavelength, at 11 m/s. */
    private fun winding(n: Int) = List(n) { at(it, 30 * sin(2 * PI * it * 11.1 / 400), it * 11.1, 40f + (it % 7)) }

    @Test
    fun emptyTrackStaysEmpty() {
        assertEquals(emptyList(), simplifyTrack(emptyList()))
    }

    @Test
    fun onePointAndTwoPointsAreKept() {
        val one = listOf(at(0, 0.0, 0.0))
        val two = listOf(at(0, 0.0, 0.0), at(1, 0.0, 11.0))
        assertEquals(one, simplifyTrack(one))
        assertEquals(two, simplifyTrack(two))
    }

    @Test
    fun straightLineCollapsesToItsEnds() {
        val track = straight(600)
        assertEquals(listOf(track.first(), track.last()), simplifyTrack(track))
    }

    @Test
    fun jitterInsideToleranceIsDropped() {
        val track = List(300) { at(it, if (it % 2 == 0) 2.5 else -2.5, it * 11.1) }
        assertEquals(listOf(track.first(), track.last()), simplifyTrack(track))
    }

    @Test
    fun rightAngleTurnKeepsTheCorner() {
        val north = List(100) { at(it, 0.0, it * 10.0) }
        val east = List(100) { at(100 + it, (it + 1) * 10.0, 990.0) }
        val corner = north.last()
        assertEquals(listOf(north.first(), corner, east.last()), simplifyTrack(north + east))
    }

    @Test
    fun outAndBackKeepsTheTurnaround() {
        val out = List(50) { at(it, 0.0, it * 10.0) }
        val back = List(50) { at(50 + it, 0.0, (48 - it) * 10.0) }
        val kept = simplifyTrack(out + back)
        assertEquals(listOf(out.first(), out.last(), back.last()), kept)
    }

    @Test
    fun everyDroppedPointIsWithinToleranceOfTheKeptLine() {
        val track = winding(2_000)
        val kept = simplifyTrack(track)
        assertTrue(kept.size in 3 until track.size / 4, "kept ${kept.size} of ${track.size}")
        val keptTimes = kept.map { it.atMillis }
        for (p in track) {
            val after = keptTimes.indexOfFirst { it >= p.atMillis }
            if (keptTimes[after] == p.atMillis) continue
            val d = offLine(p, kept[after - 1], kept[after])
            assertTrue(d <= TRACK_TOLERANCE_M + 0.05, "point at ${p.atMillis} ms is $d m off the kept line")
        }
    }

    @Test
    fun widerToleranceKeepsFewerPoints() {
        val track = winding(2_000)
        assertTrue(simplifyTrack(track, toleranceM = 20.0).size < simplifyTrack(track, toleranceM = 5.0).size)
    }

    @Test
    fun resultIsATimeOrderedSubsetEvenFromShuffledInput() {
        val track = winding(500)
        val kept = simplifyTrack(track.shuffled(java.util.Random(7)))
        assertEquals(track.first(), kept.first())
        assertEquals(track.last(), kept.last())
        assertTrue(kept.zipWithNext().all { (a, b) -> a.atMillis < b.atMillis }, "timestamps strictly increase")
        assertTrue(track.containsAll(kept))
    }

    @Test
    fun speedChangeOnAStraightIsKept() {
        val track = straight(100) { if (it < 50) 40f else 80f }
        val kept = simplifyTrack(track)
        assertEquals(listOf(track[0], track[50], track[99]), kept)
    }

    @Test
    fun speedOnlyPointsAreAtLeastThreeSecondsApart() {
        val track = straight(120) { if (it % 2 == 0) 40f else 60f }
        val kept = simplifyTrack(track)
        val gaps = kept.dropLast(1).zipWithNext { a, b -> b.atMillis - a.atMillis }
        assertTrue(kept.size in 3 until track.size / 2, "kept ${kept.size} of ${track.size}")
        assertTrue(gaps.all { it >= TRACK_MIN_SPACING_MS }, "gaps $gaps")
    }

    @Test
    fun droppedPointsStayWithinTheSpeedStepOfTheLastKeptPoint() {
        val track = straight(100) { it.toFloat() } // 0 to 99 km/h in 100 s
        val kept = simplifyTrack(track)
        for (p in track) {
            val last = kept.last { it.atMillis <= p.atMillis }
            assertTrue(p.speedKmh - last.speedKmh < TRACK_SPEED_STEP_KMH, "at ${p.atMillis} ms")
        }
        assertEquals(11, kept.size, "one point per 10 km/h plus the last")
    }

    @Test
    fun simplifyingTwiceChangesNothing() {
        val track = winding(1_000) + straight(200) { if (it % 20 < 10) 30f else 55f }.map {
            it.copy(atMillis = it.atMillis + 1_000_000)
        }
        val once = simplifyTrack(track)
        assertEquals(once, simplifyTrack(once))
    }

    @Test
    fun longWindingTrackDoesNotOverflow() {
        val track = winding(50_000)
        val kept = simplifyTrack(track)
        assertTrue(kept.size < track.size / 4, "kept ${kept.size} of ${track.size}")
    }

    private companion object {
        const val TRIP = 7L
    }
}
