package app.safar.ui.components

import android.graphics.BlurMaskFilter
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * A soft paper shadow, as CSS `box-shadow: 0 [offsetY] [blur] [spread] [color]`, plus an optional hard
 * [edgeColor] line [edgeOffsetY] below the surface (the first layer of Atlas `--shadow`).
 */
@Immutable
data class PaperShadow(
    val blur: Dp,
    val color: Color,
    val spread: Dp = 0.dp,
    val offsetY: Dp = 0.dp,
    val edgeColor: Color = Color.Unspecified,
    val edgeOffsetY: Dp = 1.dp,
)

/**
 * Draws [shadow] behind the content in the outline of [shape] (rounded rectangles; other outlines use their
 * bounds). Looks like `dropShadow`, but the blur is rendered once per corner radius, blur and density into a
 * small nine-patch shared by every surface, and stretched to each size: no full-size blur on the UI thread for
 * each new card size, one small texture instead of one per card, and the plan is kept across recompositions.
 */
fun Modifier.paperShadow(shape: Shape, shadow: PaperShadow): Modifier = this then PaperShadowElement(shape, shadow)

private data class PaperShadowElement(val shape: Shape, val shadow: PaperShadow) : ModifierNodeElement<PaperShadowNode>() {
    override fun create() = PaperShadowNode(shape, shadow)

    override fun update(node: PaperShadowNode) = node.update(shape, shadow)

    override fun InspectorInfo.inspectableProperties() {
        name = "paperShadow"
        properties["shape"] = shape
        properties["shadow"] = shadow
    }
}

private class PaperShadowNode(private var shape: Shape, private var shadow: PaperShadow) : Modifier.Node(), DrawModifierNode {
    private var plan: ShadowPlan? = null
    private var edge: Outline? = null
    private var tint: ColorFilter? = null
    private var builtFor: Triple<Size, Float, LayoutDirection>? = null

    fun update(shape: Shape, shadow: PaperShadow) {
        if (shape == this.shape && shadow == this.shadow) return
        this.shape = shape
        this.shadow = shadow
        builtFor = null
        invalidateDraw()
    }

    override fun ContentDrawScope.draw() {
        val key = Triple(size, density, layoutDirection)
        if (builtFor != key) {
            build()
            builtFor = key
        }
        plan?.let { p -> tint?.let { p.draw(this, it) } }
        val outline = edge
        if (outline != null) translate(0f, shadow.edgeOffsetY.toPx()) { drawOutline(outline, shadow.edgeColor) }
        drawContent()
    }

    private fun DrawScope.build() {
        val outline = shape.createOutline(size, layoutDirection, this)
        edge = if (shadow.edgeColor.isSpecified) outline else null
        tint = ColorFilter.tint(shadow.color)
        val corner = (outline as? Outline.Rounded)?.roundRect?.topLeftCornerRadius?.x ?: 0f
        plan = ShadowPlan.of(size, corner, shadow.blur.toPx().roundToInt(), shadow.spread.toPx(), shadow.offsetY.toPx())
    }
}

/** One span of a nine-patch axis: [srcLen] source pixels from [src] drawn over [dstLen] pixels from [dst]. */
internal data class Span(val src: Int, val srcLen: Int, val dst: Int, val dstLen: Int)

/** How far a blur of radius [blur] reaches into a straight edge (3 sigma; Skia uses sigma = 0.57735 r + 0.5). */
internal fun blurReach(blur: Int): Int = ceil(blur * BLUR_REACH + 1.5f).toInt()

/**
 * One axis of the shadow: [shapeLen] is the shadow shape's length (surface plus twice the spread). A long axis
 * is drawn from a source shape just long enough for its middle to be a straight edge, as corner, a stretched
 * one-pixel middle and corner; a short one is drawn as it is. Returns the source shape length and the spans.
 */
internal fun shadowSpans(shapeLen: Int, corner: Int, blur: Int, dstStart: Int): Pair<Int, List<Span>> {
    val total = shapeLen + 2 * blur
    val reach = blurReach(blur)
    val minLen = 2 * (corner + reach) + 1
    if (shapeLen <= minLen) return shapeLen to listOf(Span(0, total, dstStart, total))
    val side = blur + corner + reach
    return minLen to listOf(
        Span(0, side, dstStart, side),
        Span(side, 1, dstStart + side, total - 2 * side),
        Span(side + 1, side, dstStart + total - side, side),
    )
}

private const val BLUR_REACH = 1.75f

/** The source bitmap and the spans that lay it out under one surface. */
private class ShadowPlan(private val image: ImageBitmap, private val xs: List<Span>, private val ys: List<Span>) {
    fun draw(scope: DrawScope, tint: ColorFilter) {
        for (y in ys) {
            for (x in xs) {
                scope.drawImage(
                    image, IntOffset(x.src, y.src), IntSize(x.srcLen, y.srcLen), IntOffset(x.dst, y.dst), IntSize(x.dstLen, y.dstLen),
                    colorFilter = tint,
                )
            }
        }
    }

    companion object {
        /** Null when the shadow shape is empty (a surface smaller than twice a negative spread). */
        fun of(size: Size, corner: Float, blur: Int, spread: Float, offsetY: Float): ShadowPlan? {
            val w = (size.width + 2 * spread).roundToInt()
            val h = (size.height + 2 * spread).roundToInt()
            if (w <= 0 || h <= 0) return null
            val r = corner.roundToInt()
            val (sw, xs) = shadowSpans(w, r, blur, (-spread - blur).roundToInt())
            val (sh, ys) = shadowSpans(h, r, blur, (offsetY - spread - blur).roundToInt())
            return ShadowPlan(ShadowBitmaps.get(sw, sh, corner, blur), xs, ys)
        }
    }
}

/** Blurred rounded rectangles by source size, corner and blur; few, small, and shared by all surfaces. Main thread. */
private object ShadowBitmaps {
    private const val MAX = 24

    private data class Key(val w: Int, val h: Int, val corner: Float, val blur: Int)

    private val cache = object : LinkedHashMap<Key, ImageBitmap>(MAX, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, ImageBitmap>?): Boolean = size > MAX
    }

    fun get(w: Int, h: Int, corner: Float, blur: Int): ImageBitmap = cache.getOrPut(Key(w, h, corner, blur)) {
        val bitmap = ImageBitmap(w + 2 * blur, h + 2 * blur, ImageBitmapConfig.Alpha8)
        val paint = Paint()
        if (blur > 0) paint.asFrameworkPaint().maskFilter = BlurMaskFilter(blur.toFloat(), BlurMaskFilter.Blur.NORMAL)
        val b = blur.toFloat()
        Canvas(bitmap).drawRoundRect(b, b, b + w, b + h, corner, corner, paint)
        bitmap
    }
}
