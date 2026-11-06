package app.safar.ui.trips

import kotlin.math.hypot

// Places the START and END labels on the trip map so they never sit on top of each other. A ride that ends where
// it began gets one "START · END" label, with its two markers set side by side. Pure Kotlin, in box pixels.

/** A label's width and height. */
data class LabelSize(val w: Float, val h: Float)

/** A label's box: top-left corner [x], [y] and its size. */
data class LabelBox(val x: Float, val y: Float, val w: Float, val h: Float) {
    fun overlaps(o: LabelBox): Boolean = x < o.x + o.w && o.x < x + w && y < o.y + o.h && o.y < y + h

    fun contains(p: XY): Boolean = p.x > x && p.x < x + w && p.y > y && p.y < y + h

    /** True when the line from [a] to [b] passes through the box. */
    fun crossedBy(a: XY, b: XY): Boolean {
        if (contains(a) || contains(b)) return true
        val corners = listOf(XY(x, y), XY(x + w, y), XY(x + w, y + h), XY(x, y + h))
        return corners.indices.any { i -> segmentsCross(a, b, corners[i], corners[(i + 1) % 4]) }
    }
}

private fun cross(o: XY, a: XY, b: XY): Float = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)

private fun segmentsCross(p1: XY, p2: XY, q1: XY, q2: XY): Boolean {
    val d1 = cross(q1, q2, p1)
    val d2 = cross(q1, q2, p2)
    val d3 = cross(p1, p2, q1)
    val d4 = cross(p1, p2, q2)
    return ((d1 > 0) != (d2 > 0)) && ((d3 > 0) != (d4 > 0))
}

/** The three label sizes and the spacing rules, all in box pixels. */
data class EndLabelSpec(
    val start: LabelSize,
    val end: LabelSize,
    val merged: LabelSize,
    /** Space between a marker and its label. */
    val gap: Float,
    /** Ends closer than this share one label. */
    val mergeWithin: Float,
    /** Half the size of a marker, kept clear of the other label. */
    val markerHalf: Float,
    /** How far a label stays from the route line where it can (half the drawn stroke). */
    val routeClearance: Float = 0f,
)

/**
 * Where to draw the markers and labels. When the ends are merged, [start] is the "START · END" label, [end] is
 * null, and the markers are moved apart around the middle so both stay visible.
 */
data class EndLabels(val startMarker: XY, val endMarker: XY, val start: LabelBox, val end: LabelBox?)

/**
 * Places START and END around the first and last of [track] (box pixels) in a [width] x [height] box. Labels keep
 * off each other and the other marker, and off the drawn route where there is a free side.
 */
fun placeEndLabels(track: List<XY>, spec: EndLabelSpec, width: Float, height: Float): EndLabels? {
    val start = track.firstOrNull() ?: return null
    val end = track.last()
    val area = Area(width, height, track, spec.routeClearance)
    if (hypot(end.x - start.x, end.y - start.y) <= spec.mergeWithin) return mergedLabels(start, end, spec, area)
    val startMarkerBox = markerBox(start, spec.markerHalf)
    val endMarkerBox = markerBox(end, spec.markerHalf)
    val startLabel = place(start, spec.start, spec.gap, area, listOf(endMarkerBox))
    val endLabel = place(end, spec.end, spec.gap, area, listOf(startLabel, startMarkerBox))
    return EndLabels(start, end, startLabel, endLabel)
}

/** The drawing box and the route drawn in it. */
private class Area(val width: Float, val height: Float, val track: List<XY>, val clearance: Float) {
    fun holds(b: LabelBox): Boolean = b.x >= 0 && b.y >= 0 && b.x + b.w <= width && b.y + b.h <= height

    /** How many stretches of the route pass through [b], or within [clearance] of it. */
    fun routeHits(b: LabelBox): Int {
        val grown = LabelBox(b.x - clearance, b.y - clearance, b.w + 2 * clearance, b.h + 2 * clearance)
        return track.indices.drop(1).count { grown.crossedBy(track[it - 1], track[it]) }
    }
}

private fun mergedLabels(start: XY, end: XY, spec: EndLabelSpec, area: Area): EndLabels {
    val mid = XY((start.x + end.x) / 2, (start.y + end.y) / 2)
    // Start on the left unless the ride clearly finished to its west.
    val side = if (end.x < start.x) -1f else 1f
    val nudge = spec.markerHalf + spec.gap / 4
    val startMarker = XY(mid.x - side * nudge, mid.y)
    val endMarker = XY(mid.x + side * nudge, mid.y)
    val label = place(mid, spec.merged, spec.gap + nudge, area, emptyList())
    return EndLabels(startMarker, endMarker, label, null)
}

/**
 * Tries right, left, above and below [at], then the four corners, in that order. Of the spots that fit in the box
 * and miss every box in [avoid], takes the one the route crosses least (the earliest on a tie); failing that, any
 * spot that fits; failing that, the right-hand spot pushed inside the box.
 */
private fun place(at: XY, size: LabelSize, clearance: Float, area: Area, avoid: List<LabelBox>): LabelBox {
    val left = at.x - clearance - size.w
    val right = at.x + clearance
    val above = at.y - clearance - size.h
    val below = at.y + clearance
    val spots = listOf(
        XY(right, at.y - size.h / 2), XY(left, at.y - size.h / 2),
        XY(at.x - size.w / 2, above), XY(at.x - size.w / 2, below),
        XY(right, above), XY(right, below), XY(left, above), XY(left, below),
    ).map { LabelBox(it.x, it.y, size.w, size.h) }
    val inside = spots.filter(area::holds)
    val free = inside.filter { spot -> avoid.none { it.overlaps(spot) } }
    return free.minByOrNull(area::routeHits)
        ?: inside.firstOrNull()
        ?: spots.first().let {
            it.copy(x = it.x.coerceIn(0f, maxOf(0f, area.width - it.w)), y = it.y.coerceIn(0f, maxOf(0f, area.height - it.h)))
        }
}

private fun markerBox(at: XY, half: Float) = LabelBox(at.x - half, at.y - half, half * 2, half * 2)
