package app.safar.ui.log

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.safar.core.LogRecord
import app.safar.ui.art.AtlasIcons
import app.safar.ui.components.AtlasCard
import app.safar.ui.components.CardStyle
import app.safar.ui.components.DisplayTitle
import app.safar.ui.components.Kicker
import app.safar.ui.components.LeaderRow
import app.safar.ui.components.Note
import app.safar.ui.components.PrimaryButton
import app.safar.ui.components.SecondaryButton
import app.safar.ui.components.SwitchRow
import app.safar.ui.theme.Atlas
import app.safar.ui.theme.AtlasColors
import app.safar.ui.theme.AtlasShapes

/** Frame or event detail: decoded fields, then the 30 bytes with start, type, checksum and end marked. */
@Composable
internal fun RecordDetail(r: LogRecord, onClose: () -> Unit) {
    val (title, em) = detailTitle(r)
    Kicker("${r.badge().label} · ${logTime(r.atMillis)}")
    DisplayTitle(title, em, Atlas.type.displaySmall, Modifier.padding(top = 6.dp, bottom = 8.dp))
    detailFields(r).forEach { (k, v) -> LeaderRow(k, v) }
    val hex = r.hex
    if (hex != null) {
        val bytes = decodeFrame(hex).bytes
        Kicker("${bytes.size} bytes", Modifier.padding(top = 16.dp))
        HexGrid(bytes)
        HexKey(showUnknown = bytes.any { it.role == ByteRole.UNKNOWN })
    }
    SecondaryButton("Close", onClose, Modifier.fillMaxWidth().padding(top = 18.dp))
}

@Composable
private fun HexGrid(bytes: List<HexByte>) {
    val c = Atlas.colors
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        bytes.chunked(10).forEach { line ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                line.forEach { b -> HexCell(b, c, Modifier.weight(1f)) }
                repeat(10 - line.size) { Column(Modifier.weight(1f)) {} }
            }
        }
    }
}

@Composable
private fun HexCell(b: HexByte, c: AtlasColors, modifier: Modifier) {
    val (bg, fg) = roleColors(b.role, c)
    val face = modifier.background(bg, AtlasShapes.tiny)
    val framed = when (b.role) {
        ByteRole.UNKNOWN -> face.border(2.dp, c.amber, AtlasShapes.tiny)
        ByteRole.DATA, ByteRole.PAD -> face.border(1.dp, c.rule, AtlasShapes.tiny)
        else -> face
    }
    Column(
        framed.padding(top = 4.dp, bottom = 2.dp).semantics { contentDescription = "Byte ${b.index}: ${b.text}, ${b.role.name.lowercase()}" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(b.text, style = Atlas.type.monoValue.copy(fontSize = 12.sp, color = if (b.role == ByteRole.PAD) c.ink3 else fg))
        BasicText(b.index.toString(), style = Atlas.type.src.copy(fontSize = 8.sp, color = if (b.role in COLOURED) fg.copy(alpha = 0.7f) else c.ink3))
    }
}

private val COLOURED = setOf(ByteRole.START, ByteRole.TYPE, ByteRole.CHECKSUM, ByteRole.END)

private fun roleColors(role: ByteRole, c: AtlasColors): Pair<Color, Color> = when (role) {
    ByteRole.START -> c.moss to c.paper
    ByteRole.TYPE -> c.slate to c.paper
    ByteRole.CHECKSUM -> c.rust to c.onRust
    ByteRole.END -> c.ink to c.paper
    else -> c.card2 to c.ink
}

@Composable
private fun HexKey(showUnknown: Boolean) {
    val c = Atlas.colors
    FlowRow(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(ByteRole.START to "A5 start", ByteRole.TYPE to "type", ByteRole.CHECKSUM to "checksum", ByteRole.END to "7F end").forEach { (role, label) ->
            val (bg, fg) = roleColors(role, c)
            BasicText(label, Modifier.background(bg, AtlasShapes.tiny).padding(horizontal = 6.dp, vertical = 2.dp), style = Atlas.type.src.copy(fontSize = 10.sp, color = fg))
        }
        if (showUnknown) {
            BasicText(
                "byte 25 · unknown",
                Modifier.border(2.dp, c.amber, AtlasShapes.tiny).padding(horizontal = 6.dp, vertical = 2.dp),
                style = Atlas.type.src.copy(fontSize = 10.sp, color = c.ink),
            )
        }
    }
}

/** Export sheet: file facts, the first lines, "Hide names" (on by default), Share. */
@Composable
internal fun ExportBody(sheet: ExportSheet, onHide: (Boolean) -> Unit, onShare: () -> Unit, onClose: () -> Unit) {
    val c = Atlas.colors
    val p = sheet.preview
    Kicker(if (p != null) p.subtitle() else if (sheet.busy) "writing…" else "not written")
    BasicText(p?.fileName ?: "suzuki-ble-log/1 · JSON Lines", Modifier.padding(top = 4.dp), style = Atlas.type.serifTitle.copy(color = c.ink))
    SwitchRow("Hide names", sheet.hideNames, onHide, subtitle = "Names become *** in this file")
    sheet.error?.let { Note(it, Modifier.padding(top = 8.dp), color = c.rust) }
    if (p != null) {
        AtlasCard(Modifier.padding(top = 10.dp), style = CardStyle.LINE) {
            p.firstLines.forEach { BasicText(it, style = Atlas.type.src.copy(fontSize = 10.sp, color = c.ink2)) }
        }
    }
    Note("Only you choose where it goes. Nothing is uploaded by Safar.", Modifier.padding(top = 10.dp), color = c.ink3)
    Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SecondaryButton("Cancel", onClose, Modifier.weight(1f))
        PrimaryButton("Share", onShare, Modifier.weight(1f), icon = AtlasIcons.share, enabled = sheet.file != null && !sheet.busy)
    }
}
