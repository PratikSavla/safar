package app.safar.ui.art

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * A tiny SVG-like builder for Atlas line art: round caps and joins, one stroke width,
 * drawn in black so the caller tints it (`Image(colorFilter = tint)` or [AtlasIcon]).
 * Path strings are copied from the prototype's SVG (icons.js, art.js).
 */
class LineArtScope internal constructor(
    private val builder: ImageVector.Builder,
    private val stroke: Float,
) {
    /** An SVG path. [filled] also fills it; [stroked] = false draws fill only. */
    fun path(d: String, filled: Boolean = false, stroked: Boolean = true, alpha: Float = 1f, width: Float = stroke) {
        builder.addPath(
            pathData = addPathNodes(d),
            fill = if (filled) SolidColor(Color.Black) else null,
            fillAlpha = alpha,
            stroke = if (stroked) SolidColor(Color.Black) else null,
            strokeAlpha = alpha,
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }

    fun circle(cx: Float, cy: Float, r: Float, filled: Boolean = false, alpha: Float = 1f, width: Float = stroke) =
        path(circleD(cx, cy, r), filled = filled, alpha = alpha, width = width)

    fun rect(x: Float, y: Float, w: Float, h: Float, rx: Float = 0f) = path(rectD(x, y, w, h, rx))

    /** Draws [block] inside a group: mirror flips left/right around the centre, [rotate] in degrees. */
    fun group(mirror: Boolean = false, rotate: Float = 0f, pivotX: Float, pivotY: Float, block: LineArtScope.() -> Unit) {
        builder.addGroup(rotate = rotate, pivotX = pivotX, pivotY = pivotY, scaleX = if (mirror) -1f else 1f)
        block()
        builder.clearGroup()
    }
}

/** Builds a tintable line-art vector on a [w] x [h] viewport shown at [sizeW] x [sizeH] dp. */
fun lineArt(
    name: String,
    w: Float = 24f,
    h: Float = 24f,
    stroke: Float = 1.7f,
    sizeW: Float = w,
    sizeH: Float = h,
    block: LineArtScope.() -> Unit,
): ImageVector {
    val b = ImageVector.Builder(name = name, defaultWidth = sizeW.dp, defaultHeight = sizeH.dp, viewportWidth = w, viewportHeight = h)
    LineArtScope(b, stroke).block()
    return b.build()
}

internal fun circleD(cx: Float, cy: Float, r: Float): String =
    "M${cx - r},${cy}a$r,$r 0 1,0 ${2 * r},0a$r,$r 0 1,0 ${-2 * r},0Z"

internal fun rectD(x: Float, y: Float, w: Float, h: Float, rx: Float): String {
    if (rx <= 0f) return "M$x,${y}h${w}v${h}h${-w}Z"
    val iw = w - 2 * rx
    val ih = h - 2 * rx
    return "M${x + rx},${y}h${iw}a$rx,$rx 0 0 1 $rx,${rx}v${ih}a$rx,$rx 0 0 1 ${-rx},${rx}" +
        "h${-iw}a$rx,$rx 0 0 1 ${-rx},${-rx}v${-ih}a$rx,$rx 0 0 1 $rx,${-rx}Z"
}
