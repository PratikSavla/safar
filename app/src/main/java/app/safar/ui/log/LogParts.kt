package app.safar.ui.log

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.safar.ui.art.AtlasIcons
import app.safar.ui.components.AtlasButton
import app.safar.ui.components.AtlasChip
import app.safar.ui.components.ButtonTone
import app.safar.ui.components.DashedRule
import app.safar.ui.components.Hairline
import app.safar.ui.components.SwitchRow
import app.safar.ui.components.blinkShown
import app.safar.ui.components.rememberLoopPhase
import app.safar.ui.components.rememberOnScreen
import app.safar.ui.theme.Atlas
import app.safar.ui.theme.AtlasShapes

/** "Debug log is off" strip with its switch (`.row.offline`). */
@Composable
internal fun LogOffRow(onTurnOn: (Boolean) -> Unit) {
    Box(Modifier.fillMaxWidth().padding(bottom = 14.dp).background(Atlas.colors.amberWash, AtlasShapes.small).padding(horizontal = 10.dp)) {
        SwitchRow("Debug log is off", false, onTurnOn, subtitle = "Still keeps the last 500 entries in memory for support.", divider = false)
    }
}

/** Start/stop session, export, and the recording mark (`.log-acts`). */
@Composable
internal fun SessionBar(sessionActive: Boolean, onSession: () -> Unit, onExport: () -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().padding(bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        AtlasButton(
            if (sessionActive) "Stop session" else "Start session",
            onSession,
            tone = if (sessionActive) ButtonTone.RUST else ButtonTone.GHOST,
            icon = if (sessionActive) AtlasIcons.stop else AtlasIcons.play,
            small = true,
        )
        AtlasButton("Export", onExport, tone = ButtonTone.INK, icon = AtlasIcons.export, small = true)
        if (sessionActive) RecordingMark(Modifier.align(Alignment.CenterVertically))
    }
}

@Composable
private fun RecordingMark(modifier: Modifier) {
    val c = Atlas.colors
    val onScreen = rememberOnScreen()
    val phase = rememberLoopPhase(1000, "recBlink", running = onScreen.visible)
    Row(modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        // Layer alpha read in the layer block: a blink only updates a layer property, no recomposition or redraw.
        Box(Modifier.size(8.dp).then(onScreen.modifier).graphicsLayer { alpha = if (blinkShown(phase.value)) 1f else 0.2f }.background(c.rust, CircleShape))
        BasicText("writing to file", style = Atlas.type.src.copy(color = c.rust))
    }
}

/** Direction, error and type chips (`.filters`); each strip scrolls sideways. */
@Composable
internal fun LogFilters(f: LogFilter, onDir: (DirFilter) -> Unit, onType: (Char?) -> Unit, onErrors: (Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        DashedRule(thickness = 1.dp, dash = 4.dp, gap = 3.dp)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DirFilter.entries.forEach { d -> AtlasChip(d.label, f.dir == d, { onDir(d) }) }
            AtlasChip("Errors only", f.errorsOnly, onErrors, danger = true)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AtlasChip("Any type", f.type == null, { onType(null) })
            FRAME_TYPE_NAMES.forEach { (t, name) -> AtlasChip("$t $name", f.type == t, { on -> onType(if (on) t else null) }) }
        }
        DashedRule(thickness = 1.dp, dash = 4.dp, gap = 3.dp)
    }
}

/** One row of the wire (`.lrow`): badge, time and title, summary, start of the hex. */
@Composable
internal fun LogRowItem(row: LogRow, onOpen: (Long) -> Unit) {
    val c = Atlas.colors
    Column(Modifier.fillMaxWidth().clickable(onClickLabel = "Show frame detail", role = Role.Button) { onOpen(row.id) }) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Badge(row.badge)
            Column(Modifier.weight(1f)) {
                BasicText("${row.time} · ${row.title}", style = Atlas.type.src.copy(color = c.ink3, fontSize = 10.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText(
                    row.summary,
                    style = Atlas.type.uiSmall.copy(color = if (row.badge == LogBadge.ERR) c.rust else c.ink),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                row.hexPreview?.let { BasicText(it, style = Atlas.type.src.copy(color = c.ink3, fontSize = 9.sp), maxLines = 1, overflow = TextOverflow.Clip) }
            }
        }
        Hairline()
    }
}

@Composable
private fun Badge(badge: LogBadge) {
    val c = Atlas.colors
    val ink: Color = when (badge) {
        LogBadge.TX -> c.rust
        LogBadge.RX -> c.moss
        LogBadge.EVT -> c.slate
        LogBadge.ERR -> c.onRust
    }
    val face = Modifier.widthIn(min = 38.dp).heightIn(min = 22.dp)
    Box(
        if (badge == LogBadge.ERR) face.background(c.rust, AtlasShapes.tiny) else face.border(1.3.dp, ink, AtlasShapes.tiny),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(badge.label, Modifier.padding(horizontal = 4.dp), style = Atlas.type.src.copy(color = ink, fontSize = 10.sp, fontWeight = FontWeight.SemiBold))
    }
}
