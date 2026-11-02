package app.safar.data

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot

// Shrinks a finished trip's GPS track so years of rides stay under Android's 25 MB Auto Backup quota
// (over it, the whole backup is skipped). Pure Kotlin: no Android imports.

/** A dropped point is at most this far (metres) from the simplified line. */
const val TRACK_TOLERANCE_M = 5.0

/** A point whose speed differs this much (km/h) from the last kept point is kept, so the speed profile survives. */
const val TRACK_SPEED_STEP_KMH = 10f

/** A point kept only for its speed is at least this long after the previous kept point. */
const val TRACK_MIN_SPACING_MS = 3_000L

private const val EARTH_RADIUS_M = 6_371_008.8

/**
 * The points of [points] worth storing, in time order: Ramer-Douglas-Peucker in metres (a local equirectangular
 * projection) with [toleranceM], so straight stretches collapse to their ends and every turn is kept. The first
 * and last points always stay. A dropped point whose speed differs by [speedStepKmh] or more from the last kept
 * point is put back, but only [minSpacingMillis] or more after it, so a noisy speed on a straight road cannot
 * refill the track. The result is a subset of [points]; simplifying it again returns it unchanged.
 */
fun simplifyTrack(
    points: List<TrackPoint>,
    toleranceM: Double = TRACK_TOLERANCE_M,
    speedStepKmh: Float = TRACK_SPEED_STEP_KMH,
    minSpacingMillis: Long = TRACK_MIN_SPACING_MS,
): List<TrackPoint> {
    val sorted = points.sortedBy { it.atMillis }
    if (sorted.size <= 2) return sorted
    val keep = BooleanArray(sorted.size)
    keep[0] = true
    keep[sorted.lastIndex] = true
    markTurns(LocalProjection(sorted), keep, toleranceM)
    markSpeedChanges(sorted, keep, speedStepKmh, minSpacingMillis)
    return sorted.filterIndexed { i, _ -> keep[i] }
}

/** Track points in metres east ([x]) and north ([y]) of the first point. */
private class LocalProjection(points: List<TrackPoint>) {
    val x = DoubleArray(points.size)
    val y = DoubleArray(points.size)

    init {
        val midLat = (points.minOf { it.lat } + points.maxOf { it.lat }) / 2
        val metresPerDegLat = Math.toRadians(1.0) * EARTH_RADIUS_M
        val metresPerDegLng = metresPerDegLat * cos(Math.toRadians(midLat))
        val origin = points.first()
        points.forEachIndexed { i, p ->
            x[i] = wrapDegrees(p.lng - origin.lng) * metresPerDegLng
            y[i] = (p.lat - origin.lat) * metresPerDegLat
        }
    }

    /** Distance in metres from point [p] to the segment from point [a] to point [b]. */
    fun distanceToSegment(p: Int, a: Int, b: Int): Double {
        val dx = x[b] - x[a]
        val dy = y[b] - y[a]
        val lengthSq = dx * dx + dy * dy
        val t = if (lengthSq == 0.0) 0.0 else (((x[p] - x[a]) * dx + (y[p] - y[a]) * dy) / lengthSq).coerceIn(0.0, 1.0)
        return hypot(x[p] - (x[a] + t * dx), y[p] - (y[a] + t * dy))
    }
}

/** Longitude difference folded into -180..180, so a track across the antimeridian stays short. */
private fun wrapDegrees(deg: Double): Double = ((deg + 540.0) % 360.0) - 180.0

/**
 * Ramer-Douglas-Peucker on [keep]: splits at the point farthest from the segment between two kept points while
 * that point is more than [toleranceM] away. Uses its own stack, so a long winding track cannot overflow it.
 * Distances are to the segment, not the infinite line, so the far end of an out-and-back is kept.
 */
private fun markTurns(projection: LocalProjection, keep: BooleanArray, toleranceM: Double) {
    val stack = ArrayDeque<Pair<Int, Int>>()
    stack.addLast(0 to keep.lastIndex)
    while (stack.isNotEmpty()) {
        val (start, end) = stack.removeLast()
        var farthest = -1
        var farthestM = toleranceM
        for (i in start + 1 until end) {
            val d = projection.distanceToSegment(i, start, end)
            if (d > farthestM) {
                farthest = i
                farthestM = d
            }
        }
        if (farthest < 0) continue
        keep[farthest] = true
        stack.addLast(start to farthest)
        stack.addLast(farthest to end)
    }
}

/** Puts back dropped points where the speed moved [stepKmh] from the last kept point, [minSpacingMillis] apart. */
private fun markSpeedChanges(points: List<TrackPoint>, keep: BooleanArray, stepKmh: Float, minSpacingMillis: Long) {
    var last = points.first()
    points.forEachIndexed { i, p ->
        if (!keep[i] && abs(p.speedKmh - last.speedKmh) >= stepKmh && p.atMillis - last.atMillis >= minSpacingMillis) {
            keep[i] = true
        }
        if (keep[i]) last = p
    }
}
