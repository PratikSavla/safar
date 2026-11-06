package app.safar.ui.service

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.safar.data.ServiceState
import app.safar.ui.art.AtlasIcons
import app.safar.ui.components.AtlasButton
import app.safar.ui.components.AtlasIcon
import app.safar.ui.components.ButtonTone
import app.safar.ui.components.Hairline
import app.safar.ui.components.IconBox
import app.safar.ui.components.Stamp
import app.safar.ui.components.StampTone
import app.safar.ui.components.blinkShown
import app.safar.ui.components.rememberLoopPhase
import app.safar.ui.components.rememberOnScreen
import app.safar.ui.theme.Atlas


@Composable
private fun stateColor(state: ServiceState): Color = when (state) {
    ServiceState.OVERDUE -> Atlas.colors.rust
    ServiceState.DUE_SOON -> Atlas.colors.amber
    ServiceState.OK -> Atlas.colors.ink
}

private fun ServiceState.tone() = when (this) {
    ServiceState.OVERDUE -> StampTone.RUST
    ServiceState.DUE_SOON -> StampTone.AMBER
    ServiceState.OK -> StampTone.MOSS
}

/** One logbook item (`.svc`): head, the road from last service to the flag, the due line and actions. */
@Composable
internal fun ServiceItemCard(row: ServiceRowUi, onDone: () -> Unit, onEdit: () -> Unit) {
    val c = Atlas.colors
    val item = row.status.item
    val state = row.status.state
    val accent = stateColor(state)
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBox(item.icon.vector(), tint = if (state == ServiceState.OK) c.ink else accent, outline = if (state == ServiceState.OK) c.rule2 else accent)
            Column(Modifier.weight(1f)) {
                BasicText(item.label, style = Atlas.type.serifTitle.copy(color = c.ink))
                BasicText(row.everyLine, style = Atlas.type.src.copy(color = c.ink3))
            }
            Stamp(row.word, tone = state.tone())
        }
        ServiceRoad(row, accent)
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 22.dp, top = 2.dp)) {
            val mono = Atlas.type.src.copy(color = c.ink3, fontSize = Atlas.type.src.fontSize * 0.9f)
            BasicText(row.fromLabel, Modifier.weight(1f), style = mono)
            BasicText(row.toLabel, style = mono)
        }
        BasicText(
            row.line,
            Modifier.padding(top = 10.dp, bottom = 12.dp),
            style = Atlas.type.ui.copy(color = if (state == ServiceState.OVERDUE) c.rust else c.ink2, fontSize = Atlas.type.note.fontSize * 1.08f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AtlasButton(
                "Mark done", onDone, Modifier.semantics { contentDescription = "Mark ${item.label} done" },
                tone = if (state == ServiceState.OK) ButtonTone.GHOST else ButtonTone.INK, icon = AtlasIcons.check, small = true,
            )
            AtlasButton(
                "Edit", onEdit, Modifier.semantics { contentDescription = "Change ${item.label}: interval, first service or icon" },
                tone = ButtonTone.GHOST, icon = AtlasIcons.edit, small = true,
            )
        }
        Hairline(Modifier.padding(top = 18.dp), color = c.rule2)
    }
}

/** Dashed road with the travelled part inked, a rider dot (blinking when overdue) and a flag at the due point. */
@Composable
private fun ServiceRoad(row: ServiceRowUi, accent: Color) {
    val c = Atlas.colors
    val overdue = row.status.state == ServiceState.OVERDUE
    val onScreen = rememberOnScreen()
    val blink = if (overdue) rememberLoopPhase(1200, "svcBlink", running = onScreen.visible) else null
    val spoken = roadSpoken(row)
    Box(Modifier.fillMaxWidth().height(26.dp).padding(start = 4.dp).clearAndSetSemantics { contentDescription = spoken }) {
        Canvas(Modifier.fillMaxWidth().height(26.dp).padding(end = 22.dp).then(if (overdue) onScreen.modifier.graphicsLayer() else Modifier)) {
            val y = 12.dp.toPx() + 1.25.dp.toPx()
            val w = 2.5.dp.toPx()
            val dash = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))
            drawLine(c.rule2, Offset(0f, y), Offset(size.width, y), w, pathEffect = dash)
            val x = size.width * row.drawn
            drawLine(accent, Offset(0f, y), Offset(x, y), w)
            val r = 7.dp.toPx()
            drawCircle(c.paper, r, Offset(x, y))
            if (blink == null || blinkShown(blink.value)) drawCircle(accent, r - 1.5.dp.toPx(), Offset(x, y), style = Stroke(3.dp.toPx()))
        }
        AtlasIcon(AtlasIcons.flag, Modifier.align(Alignment.TopEnd), tint = c.ink2, size = 22.dp)
    }
}
