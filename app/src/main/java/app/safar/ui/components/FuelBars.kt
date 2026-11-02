package app.safar.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.safar.ui.theme.Atlas

/** Bar height as a fraction of the gauge height: a rising ruler (ui.js `fuelGauge`). */
internal fun fuelBarFraction(index: Int, max: Int): Float = 0.45f + 0.55f * (index + 1) / max

/**
 * Cluster fuel gauge drawn as rising bars. Ink normally, amber at 1 bar, rust outlines
 * blinking at 0. [max] is 5 or 6 depending on the bike.
 */
@Composable
fun FuelBars(bars: Int, max: Int, modifier: Modifier = Modifier, height: Dp = 34.dp, barWidth: Dp = 10.dp, gap: Dp = 4.dp) {
    val c = Atlas.colors
    val count = max.coerceAtLeast(1)
    val filled = bars.coerceIn(0, count)
    // Empty tank: the rust outlines blink. Only then is there a loop, read while drawing in the gauge's own layer.
    val blink = if (filled == 0) rememberLoopPhase(1000, "fuelBlink") else null
    val width = barWidth * count + gap * (count - 1)
    val layer = if (blink != null) Modifier.graphicsLayer() else Modifier
    Canvas(modifier.size(width, height).then(layer).clearAndSetSemantics { contentDescription = "Fuel $filled of $count bars" }) {
        val bw = barWidth.toPx()
        val g = gap.toPx()
        val radius = CornerRadius(1.5.dp.toPx())
        val line = 1.5.dp.toPx()
        for (i in 0 until count) {
            val h = size.height * fuelBarFraction(i, count)
            val topLeft = Offset(i * (bw + g), size.height - h)
            when {
                filled == 0 -> if (blink == null || blinkShown(blink.value)) {
                    drawRoundRect(c.rust, topLeft + Offset(line / 2, line / 2), Size(bw - line, h - line), radius, style = Stroke(line))
                }
                i < filled -> drawRoundRect(if (filled == 1) c.amber else c.ink, topLeft, Size(bw, h), radius)
                else -> drawRoundRect(c.rule2, topLeft + Offset(line / 2, line / 2), Size(bw - line, h - line), radius, style = Stroke(line))
            }
        }
    }
}

@Preview
@Composable
private fun FuelBarsPreview() = PreviewSurface {
    Row {
        FuelBars(4, 6)
        FuelBars(1, 5)
        FuelBars(0, 6)
    }
}
