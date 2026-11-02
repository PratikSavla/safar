package app.safar.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.safar.ui.art.Contours
import app.safar.ui.art.splinePath
import app.safar.ui.theme.Atlas
import kotlin.math.max

/** Where the contour layer sits for a screen (base.css `[data-screen] .topo-layer`). Offsets in dp. */
@Immutable
data class TopoTransform(
    val dx: Float = 0f,
    val dy: Float = 0f,
    val rotation: Float = 0f,
    val scale: Float = 1f,
    val alpha: Float = 1f,
) {
    companion object {
        val Home = TopoTransform(40f, -120f, -8f)
        val Route = TopoTransform(-120f, 40f, 14f, 1.1f)
        val Journal = TopoTransform(90f, 160f, 24f)
        val Alerts = TopoTransform(-60f, -200f, -30f)
        val Quiet = TopoTransform(150f, 300f, alpha = 0.45f)
        val Ride = TopoTransform(scale = 1.35f, alpha = 0.5f)
        val Onboarding = TopoTransform(-20f, 60f, scale = 1.15f)
        val Hidden = TopoTransform(alpha = 0f)
    }
}

private const val VIEW_W = 540f
private const val VIEW_H = 1040f
private val PageEase = CubicBezierEasing(0.22f, 0.8f, 0.24f, 1f)

private class TopoPaths(val plain: List<Path>, val index: List<Path>) {
    val all: List<Path> = plain + index
}

/**
 * Procedural topographic contour lines behind a page (art.js `contours()`). Subtle by design:
 * the colours come from `contour` / `contourIdx`. The layer glides when [transform] changes.
 *
 * About 25 page-sized spline strokes (twice in dark mode, for the glow) are costly to replay, so they are
 * drawn into an offscreen layer that HWUI keeps as a texture: it is re-rendered only when the size, the
 * colours or the transform change (a page glide), and every other frame, scrolling included, composites
 * one bitmap. Strokes are built once per size; the animated transform is read only while drawing.
 */
@Composable
fun TopoBackground(modifier: Modifier = Modifier, transform: TopoTransform = TopoTransform(), seed: Int = 3) {
    val paths = remember(seed) {
        val rings = Contours.rings(VIEW_W, VIEW_H, seed)
        TopoPaths(
            plain = rings.filterNot { it.index }.map { splinePath(it.points, closed = true) },
            index = rings.filter { it.index }.map { splinePath(it.points, closed = true) },
        )
    }
    val spec = tween<Float>(1100, easing = PageEase)
    val dx = animateFloatAsState(transform.dx, spec, label = "topoDx")
    val dy = animateFloatAsState(transform.dy, spec, label = "topoDy")
    val rot = animateFloatAsState(transform.rotation, spec, label = "topoRot")
    val scale = animateFloatAsState(transform.scale, spec, label = "topoScale")
    val alpha = animateFloatAsState(transform.alpha, tween(600), label = "topoAlpha")
    val colors = Atlas.colors
    val contour = colors.contour
    val contourIdx = colors.contourIdx
    val glow = colors.contourGlow
    Spacer(
        modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithCache {
                val bleed = 60.dp.toPx()
                val fit = max((size.width + bleed * 2) / VIEW_W, (size.height + bleed * 2) / VIEW_H) // xMidYMid slice
                val unit = 1.dp.toPx() / fit
                val plainStroke = Stroke(unit)
                val indexStroke = Stroke(unit * 1.4f)
                val glowStroke = Stroke(unit * 4f)
                onDrawBehind {
                    val a = alpha.value
                    if (a <= 0f) return@onDrawBehind
                    val shiftX = dx.value.dp.toPx()
                    val shiftY = dy.value.dp.toPx()
                    withTransform({
                        translate(shiftX, shiftY)
                        rotate(rot.value, Offset(size.width / 2, size.height / 2))
                        scale(scale.value, scale.value, Offset(size.width / 2, size.height / 2))
                        translate((size.width - VIEW_W * fit) / 2, (size.height - VIEW_H * fit) / 2)
                        scale(fit, fit, Offset.Zero)
                    }) {
                        if (glow.alpha > 0f) drawRings(paths.all, glow, glowStroke, a)
                        drawRings(paths.plain, contour, plainStroke, a)
                        drawRings(paths.index, contourIdx, indexStroke, a)
                    }
                }
            },
    )
}

private fun DrawScope.drawRings(paths: List<Path>, color: Color, stroke: Stroke, alpha: Float) {
    paths.forEach { drawPath(it, color, alpha = alpha, style = stroke) }
}
