package app.safar.ui.art

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** One closed contour ring. Every 5th ring is an index contour (drawn darker). */
data class ContourRing(val points: List<Offset>, val index: Boolean)

/**
 * Topographic contour rings around two summits, ported from art.js `contours()`.
 * Coordinates live in a [width] x [height] box; the caller scales it to cover the canvas.
 */
object Contours {
    private const val POINTS_PER_RING = 36
    private const val MOD = 2147483647L

    fun rings(width: Float = 540f, height: Float = 1040f, seed: Int = 3, rings: Int = 15, step: Float = 30f): List<ContourRing> {
        val rnd = lehmer(seed * 977L + 13)
        val peaks = listOf(
            Triple(Offset(width * 0.72f, height * 0.28f), rings, 1f),
            Triple(Offset(width * 0.2f, height * 0.78f), (rings * 0.7f).roundToInt(), 0.85f),
        )
        return peaks.flatMap { (center, count, scale) ->
            val phase = DoubleArray(4) { rnd() * 6.3 }
            (1..count).map { k -> ContourRing(ring(center, k, step * scale, phase), index = k % 5 == 0) }
        }
    }

    private fun ring(center: Offset, k: Int, step: Float, ph: DoubleArray): List<Offset> =
        List(POINTS_PER_RING) { j ->
            val t = j.toDouble() / POINTS_PER_RING * PI * 2
            val wob = 1 + 0.2 * sin(2 * t + ph[0] + k * 0.09) + 0.11 * sin(3 * t + ph[1] - k * 0.13) +
                0.05 * sin(5 * t + ph[2] + k * 0.3) + 0.03 * sin(7 * t + ph[3])
            val rad = k * step * wob
            Offset((center.x + cos(t) * rad * 1.15).toFloat(), (center.y + sin(t) * rad).toFloat())
        }

    /** Park-Miller generator, the same sequence as art.js `rng()`. */
    internal fun lehmer(seed: Long): () -> Double {
        var s = seed % MOD
        if (s == 0L) s = 7
        return {
            s = (s * 16807) % MOD
            s.toDouble() / MOD
        }
    }
}

/** Catmull-Rom spline through [pts] as cubic Béziers (art.js `spline()`). */
fun splinePath(pts: List<Offset>, closed: Boolean, path: Path = Path()): Path {
    val n = pts.size
    if (n < 2) return path
    fun at(i: Int): Offset = if (closed) pts[((i % n) + n) % n] else pts[max(0, min(n - 1, i))]
    path.moveTo(pts[0].x, pts[0].y)
    val last = if (closed) n else n - 1
    for (i in 0 until last) {
        val p0 = at(i - 1)
        val p1 = at(i)
        val p2 = at(i + 1)
        val p3 = at(i + 2)
        path.cubicTo(
            p1.x + (p2.x - p0.x) / 6, p1.y + (p2.y - p0.y) / 6,
            p2.x - (p3.x - p1.x) / 6, p2.y - (p3.y - p1.y) / 6,
            p2.x, p2.y,
        )
    }
    if (closed) path.close()
    return path
}
