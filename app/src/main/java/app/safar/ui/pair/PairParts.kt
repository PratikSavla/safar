package app.safar.ui.pair

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.safar.core.ScannedBike
import app.safar.core.VehicleFamily
import app.safar.ui.art.AtlasIcons
import app.safar.ui.art.BikeSilhouette
import app.safar.ui.components.AtlasCard
import app.safar.ui.components.AtlasIcon
import app.safar.ui.components.CardStyle
import app.safar.ui.components.SignalBars
import app.safar.ui.components.rememberLoopPhase
import app.safar.ui.components.rssiBars
import app.safar.ui.theme.Atlas
import java.util.Locale

private val PageEase = CubicBezierEasing(0.22f, 0.8f, 0.24f, 1f)

/** Listening rings around the Bluetooth core (onboard.css `.radar`); still and faint when not scanning. */
@Composable
fun Radar(on: Boolean, modifier: Modifier = Modifier) {
    val c = Atlas.colors
    Box(modifier.size(210.dp).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        if (on) ListeningRings() else StillRings()
        Box(Modifier.size(64.dp).background(c.ink, CircleShape), contentAlignment = Alignment.Center) {
            AtlasIcon(AtlasIcons.bt, tint = c.paper, size = 28.dp)
        }
    }
}

/** Four rust rings widening from the core; phases read while drawing, in the rings' own layer. */
@Composable
private fun ListeningRings() {
    val rust = Atlas.colors.rust
    val phases = (1..4).map { i -> rememberLoopPhase(2600, "ring$i", easing = PageEase, delayMillis = i * 450) }
    Canvas(Modifier.size(210.dp).graphicsLayer()) {
        val center = Offset(size.width / 2, size.height / 2)
        phases.forEachIndexed { idx, phase ->
            val r = (idx + 1) * 26.dp.toPx()
            val p = phase.value
            scale(0.55f + 0.55f * p, center) {
                rotate(-10f + 18f * p, center) { ring(rust.copy(alpha = 0.9f * (1 - p)), center, r) }
            }
        }
    }
}

/** Not scanning: the same rings, faint and still, with no loop running. */
@Composable
private fun StillRings() {
    val ink = Atlas.colors.contourIdx
    Canvas(Modifier.size(210.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        for (i in 1..4) ring(ink, center, i * 26.dp.toPx())
    }
}

private fun DrawScope.ring(color: Color, center: Offset, r: Float) {
    drawOval(color, Offset(center.x - r * 1.04f, center.y - r * 0.96f), Size(r * 2.08f, r * 1.92f), style = Stroke(1.3.dp.toPx()))
}

/** One found cluster as a ticket (onboard.css `.tag`): silhouette, model, BLE name, signal. */
@Composable
fun ScanResult(bike: ScannedBike, onPick: () -> Unit) {
    val c = Atlas.colors
    val family = bike.model.family.name.lowercase(Locale.ROOT)
    AtlasCard(style = CardStyle.TICKET, onClick = onPick, onClickLabel = "Connect to ${bike.model.name}") {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp).clearAndSetSemantics {
                contentDescription = "${bike.model.name}, ${bike.name}, $family, signal ${rssiBars(bike.rssi)} of 4"
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BikeSilhouette(bike.model.family, width = 70.dp)
            Column(Modifier.weight(1f)) {
                BasicText(bike.model.name, style = Atlas.type.serifTitle.copy(fontSize = Atlas.type.serifTitle.fontSize * 1.04f, color = c.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText("${bike.name} · $family", style = Atlas.type.src.copy(color = c.ink3), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SignalBars(rssiBars(bike.rssi))
                BasicText("${bike.rssi} dBm", style = Atlas.type.src.copy(color = c.ink3))
            }
        }
    }
}

/** Phone — marching rust dashes — bike, while the GATT handshake runs (onboard.css `.pair-link`). */
@Composable
fun PairLink(family: VehicleFamily) {
    val c = Atlas.colors
    val phase = rememberLoopPhase(500, "pairLink")
    Row(Modifier.fillMaxWidth().padding(top = 40.dp, bottom = 26.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AtlasIcon(AtlasIcons.phone, size = 34.dp)
        Canvas(Modifier.weight(1f).height(3.dp).graphicsLayer()) {
            val on = 8.dp.toPx()
            val off = 6.dp.toPx()
            drawLine(
                c.rust, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = size.height,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(on, off), phase = -phase.value * (on + off)),
            )
        }
        BikeSilhouette(family, width = 110.dp)
    }
}

/** Handshake steps (onboard.css `.steps`): done ●, current ◐, to do ○. */
@Composable
fun HandshakeSteps(mtuSet: Boolean) {
    val c = Atlas.colors
    val steps = listOf("connectGatt · TRANSPORT_LE", "Services FFF0 / FFF1 / FFF2 by UUID", "MTU 65 · notify on", "Send rider name")
    val current = if (mtuSet) 3 else 1
    Column(Modifier.fillMaxWidth().padding(top = 26.dp)) {
        steps.forEachIndexed { i, s ->
            val (mark, ink, markInk) = when {
                i < current -> Triple("●", c.ink2, c.moss)
                i == current -> Triple("◐", c.ink, c.rust)
                else -> Triple("○", c.ink3, c.ink3)
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BasicText(mark, style = Atlas.type.src.copy(color = markInk))
                BasicText(s, style = Atlas.type.src.copy(color = ink))
            }
        }
    }
}
