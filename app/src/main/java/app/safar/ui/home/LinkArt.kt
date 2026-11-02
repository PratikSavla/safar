package app.safar.ui.home

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import app.safar.core.VehicleFamily
import app.safar.ui.art.AtlasIcons
import app.safar.ui.art.BikeSilhouette
import app.safar.ui.components.AtlasIcon
import app.safar.ui.components.rememberLoopPhase
import app.safar.ui.theme.Atlas

private val PageEase = CubicBezierEasing(0.22f, 0.8f, 0.24f, 1f)

/**
 * Phone ⋯⋯ bike drawing (home.css `.link-art`): moss with a travelling dot when linked, marching dashes
 * while a link is being made, amber while retrying, a faint dotted line when offline. Decorative.
 */
@Composable
fun LinkArt(kind: ConnKind, family: VehicleFamily, modifier: Modifier = Modifier) {
    val offline = kind == ConnKind.IDLE || kind == ConnKind.BLUETOOTH_OFF
    Row(
        modifier.fillMaxWidth().padding(vertical = 10.dp).alpha(if (offline) 0.45f else 1f).clearAndSetSemantics { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AtlasIcon(AtlasIcons.phone, size = 26.dp)
        val line = Modifier.weight(1f).height(9.dp)
        if (offline) OfflineLine(line) else LiveLine(kind, line)
        BikeSilhouette(family, width = 76.dp)
    }
}

/** Offline: a still dotted line, and no animation running at all. */
@Composable
private fun OfflineLine(modifier: Modifier) {
    val ink = Atlas.colors.ink3
    Canvas(modifier) { dots(ink) }
}

/**
 * Linked, linking or retrying: the phase is read only while drawing, inside the line's own layer, so each frame
 * redraws this 9 dp strip and not the Today page it scrolls with.
 */
@Composable
private fun LiveLine(kind: ConnKind, modifier: Modifier) {
    val c = Atlas.colors
    val periodMs = when (kind) {
        ConnKind.CONNECTED -> 2400
        ConnKind.RECONNECTING -> 1400
        else -> 600
    }
    val phase = rememberLoopPhase(periodMs, "linkPhase", easing = if (kind == ConnKind.CONNECTED) PageEase else LinearEasing)
    Canvas(modifier.graphicsLayer()) {
        when (kind) {
            ConnKind.CONNECTED -> {
                dashes(c.moss, 0f)
                val x = -10.dp.toPx() + (size.width + 20.dp.toPx()) * phase.value
                if (x in 0f..size.width) drawCircle(c.moss, 4.5.dp.toPx(), Offset(x, size.height / 2))
            }
            ConnKind.RECONNECTING -> dashes(c.amber, phase.value)
            else -> dashes(c.ink3.copy(alpha = 0.6f), phase.value)
        }
    }
}

/** 6 dp dashes, 6 dp gaps; [phase] 0..1 marches them one period to the right. */
private fun DrawScope.dashes(color: Color, phase: Float) {
    val on = 6.dp.toPx()
    val y = size.height / 2
    drawLine(
        color, Offset(0f, y), Offset(size.width, y), strokeWidth = 3.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(on, on), phase = -phase * 2 * on),
    )
}

private fun DrawScope.dots(color: Color) {
    val y = size.height / 2
    drawLine(
        color, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.01f, 4.dp.toPx())),
    )
}
