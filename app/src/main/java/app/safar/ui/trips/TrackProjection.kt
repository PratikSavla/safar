package app.safar.ui.trips

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

// Projects GPS fixes into a drawing box (art.js `trackPath`), with no map tiles: an equirectangular
// projection around the track's middle latitude, north up, scaled to fit and centred. Pure Kotlin.

private const val METRES_PER_DEGREE = 111_320.0
private val NICE_METRES = intArrayOf(10, 20, 50, 100, 200, 500, 1_000, 2_000, 5_000, 10_000, 20_000, 50_000, 100_000, 200_000)

/** A point in box coordinates (pixels, y down). */
data class XY(val x: Float, val y: Float)

/** [points] in the box; [pxPerMetre] is the drawing scale (0 for an empty track). */
data class ProjectedTrack(val points: List<XY>, val pxPerMetre: Double)

/** A scale bar: [px] long on screen, standing for [metres] on the ground. */
data class ScaleBar(val metres: Int, val px: Float, val label: String)

/** Keeps at most [max] points, evenly spaced, always with the first and the last. */
fun <T> thin(points: List<T>, max: Int): List<T> {
    if (points.size <= max || max < 2) return points
    val step = (points.size - 1).toDouble() / (max - 1)
    return List(max) { i -> points[min(points.size - 1, (i * step + 0.5).toInt())] }
}

/**
 * Fits ([lat], [lng]) pairs into a [width] x [height] box with [pad] on every side. A single point (or a track
 * that never moved) lands in the centre.
 */
fun projectTrack(lat: List<Double>, lng: List<Double>, width: Float, height: Float, pad: Float): ProjectedTrack {
    if (lat.isEmpty() || lat.size != lng.size) return ProjectedTrack(emptyList(), 0.0)
    val minLat = lat.min()
    val maxLat = lat.max()
    val minLng = lng.min()
    val kx = cos(Math.toRadians((minLat + maxLat) / 2)) * METRES_PER_DEGREE
    val xs = lng.map { (it - minLng) * kx }
    val ys = lat.map { (maxLat - it) * METRES_PER_DEGREE }
    val spanX = xs.max()
    val spanY = ys.max()
    val fitX = if (spanX > 0) (width - 2 * pad) / spanX else Double.MAX_VALUE
    val fitY = if (spanY > 0) (height - 2 * pad) / spanY else Double.MAX_VALUE
    val scale = min(fitX, fitY).let { if (it == Double.MAX_VALUE) 0.0 else max(0.0, it) }
    val ox = (width - spanX * scale) / 2
    val oy = (height - spanY * scale) / 2
    val pts = xs.indices.map { i -> XY((ox + xs[i] * scale).toFloat(), (oy + ys[i] * scale).toFloat()) }
    return ProjectedTrack(pts, scale)
}

/** The longest round distance whose bar fits in [maxPx]; null when the scale is unknown or too small. */
fun scaleBar(pxPerMetre: Double, maxPx: Float): ScaleBar? {
    if (pxPerMetre <= 0.0) return null
    val metres = NICE_METRES.lastOrNull { it * pxPerMetre <= maxPx } ?: return null
    val label = if (metres >= 1000) "${metres / 1000} km" else "$metres m"
    return ScaleBar(metres, (metres * pxPerMetre).toFloat(), label)
}
