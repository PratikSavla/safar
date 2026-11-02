package app.safar.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.safar.ui.theme.Atlas

/** Amber notice with a thick left rule (`.warn-line`): "Bike not connected. …". Announced politely. */
@Composable
fun WarnLine(text: String, modifier: Modifier = Modifier, color: Color = Atlas.colors.amber) {
    val c = Atlas.colors
    BasicText(
        text,
        modifier
            .fillMaxWidth()
            .background(c.amberWash)
            .drawBehind { drawRect(color, size = size.copy(width = 3.dp.toPx())) }
            .padding(start = 15.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        style = Atlas.type.note.copy(color = c.ink),
    )
}

/** Rust dashes marching to the right (`.wait-line`): something is on its way. Decorative. */
@Composable
fun MarchingRule(modifier: Modifier = Modifier, color: Color = Atlas.colors.rust) {
    val onScreen = rememberOnScreen()
    val phase = rememberLoopPhase(700, "marchPhase", running = onScreen.visible)
    // Own layer, phase read while drawing: each frame redraws this 3 dp strip, not the page it sits on.
    Canvas(modifier.fillMaxWidth().height(3.dp).then(onScreen.modifier).graphicsLayer()) {
        val dash = 10.dp.toPx()
        val gap = 8.dp.toPx()
        drawLine(
            color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = size.height,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, gap), phase = -(dash + gap) * phase.value),
        )
    }
}

@Preview
@Composable
private fun NoticesPreview() = PreviewSurface {
    WarnLine("Bike not connected. Turns will reach the cluster once it reconnects.")
    Gap(12.dp)
    MarchingRule()
}
