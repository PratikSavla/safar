package app.safar.ui.trips

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.safar.ui.art.splinePath
import app.safar.ui.theme.Atlas
import app.safar.ui.theme.AtlasColors

/**
 * A GPS track drawn in ink on squared paper (art.js `trackSvg`), no map tiles. [big] adds START / END labels
 * and tick marks for the trip page. [trackPad] is the margin around the track ([projectTrack] `pad`).
 */
@Composable
fun TrackMap(
    lat: List<Double>,
    lng: List<Double>,
    modifier: Modifier = Modifier,
    big: Boolean = false,
    gridStep: Dp = if (big) 30.dp else 16.dp,
    description: String = "Map of the route",
) {
    val c = Atlas.colors
    val measurer = rememberTextMeasurer()
    val labelStyle = Atlas.type.src.copy(color = c.ink3, fontSize = Atlas.type.src.fontSize * 0.82f)
    Canvas(modifier.semantics { contentDescription = description }) {
        drawGrid(c.rule, gridStep.toPx())
        val track = projectTrack(lat, lng, size.width, size.height, trackPad(big).toPx())
        if (track.points.isEmpty()) return@Canvas
        val pts = track.points.map { Offset(it.x, it.y) }
        drawInk(pts, c, big)
        if (big) {
            drawTicks(pts, c)
            drawLabelledEnds(measurer, track.points, labelStyle, c)
        } else {
            drawEnds(pts.first(), pts.last(), c, big = false)
        }
    }
}

/** The merged label for a ride that ends where it began. */
private const val START_AND_END = "START · END"

/** Margin between the box edge and the track. */
fun trackPad(big: Boolean): Dp = if (big) 34.dp else 10.dp

private fun DrawScope.drawGrid(color: Color, step: Float) {
    val w = 1.dp.toPx()
    var x = step
    while (x < size.width) {
        drawLine(color, Offset(x, 0f), Offset(x, size.height), w)
        x += step
    }
    var y = step
    while (y < size.height) {
        drawLine(color, Offset(0f, y), Offset(size.width, y), w)
        y += step
    }
}

private fun DrawScope.drawInk(pts: List<Offset>, c: AtlasColors, big: Boolean) {
    if (pts.size < 2) return
    val path = splinePath(pts, closed = false)
    drawPath(path, c.card, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    val ink = (if (big) 3f else 2.2f).dp.toPx()
    drawPath(path, c.ink, style = Stroke(ink, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawTicks(pts: List<Offset>, c: AtlasColors) {
    val every = maxOf(10, pts.size / 12)
    pts.forEachIndexed { i, p ->
        if (i % every != every / 2) return@forEachIndexed
        drawCircle(c.card, 2.2.dp.toPx(), p)
        drawCircle(c.ink2, 2.2.dp.toPx(), p, style = Stroke(1.2.dp.toPx()))
    }
}

private fun DrawScope.drawEnds(start: Offset, end: Offset, c: AtlasColors, big: Boolean) {
    drawCircle(c.moss, (if (big) 6.dp else 3.5.dp).toPx(), start)
    val half = (if (big) 5 else 3).dp.toPx()
    rotate(45f, pivot = end) {
        drawRect(c.rust, Offset(end.x - half, end.y - half), Size(half * 2, half * 2))
    }
}

/** Markers and START / END labels placed by [placeEndLabels], so the two labels never overlap. */
private fun DrawScope.drawLabelledEnds(measurer: TextMeasurer, track: List<XY>, style: TextStyle, c: AtlasColors) {
    val startText = measurer.measure("START", style)
    val endText = measurer.measure("END", style)
    val mergedText = measurer.measure(START_AND_END, style)
    val spec = EndLabelSpec(
        start = startText.size.let { LabelSize(it.width.toFloat(), it.height.toFloat()) },
        end = endText.size.let { LabelSize(it.width.toFloat(), it.height.toFloat()) },
        merged = mergedText.size.let { LabelSize(it.width.toFloat(), it.height.toFloat()) },
        gap = 10.dp.toPx(),
        mergeWithin = 16.dp.toPx(),
        markerHalf = 7.dp.toPx(),
        routeClearance = 3.5.dp.toPx(),
    )
    val ends = placeEndLabels(track, spec, size.width, size.height) ?: return
    drawEnds(Offset(ends.startMarker.x, ends.startMarker.y), Offset(ends.endMarker.x, ends.endMarker.y), c, big = true)
    if (ends.end == null) {
        drawText(mergedText, topLeft = Offset(ends.start.x, ends.start.y))
    } else {
        drawText(startText, topLeft = Offset(ends.start.x, ends.start.y))
        drawText(endText, topLeft = Offset(ends.end.x, ends.end.y))
    }
}
