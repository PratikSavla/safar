package app.safar.ui.service

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.safar.data.ServiceItem
import app.safar.ui.art.AtlasIcons
import app.safar.ui.components.AtlasBottomSheet
import app.safar.ui.components.Hairline
import app.safar.ui.components.IconBox
import app.safar.ui.components.Kicker
import app.safar.ui.components.Note
import app.safar.ui.components.PrimaryButton
import app.safar.ui.components.SecondaryButton
import app.safar.ui.components.SectionHeader
import app.safar.ui.components.TextAction
import app.safar.ui.theme.Atlas
import app.safar.ui.theme.AtlasShapes

/** Services done, newest first, with "Log a past service". Tapping a line offers to remove it. */
@Composable
internal fun HistorySection(rows: List<HistoryRowUi>, canLog: Boolean, onLogPast: () -> Unit, onRecord: (Long) -> Unit) {
    SectionHeader("Service history")
    if (rows.isEmpty()) {
        Note("Services you mark done show up here. Add the ones done before you started using Safar.")
    }
    rows.forEach { row -> HistoryLine(row) { onRecord(row.id) } }
    if (canLog) TextAction("Log a past service", onLogPast, Modifier.padding(top = 12.dp), icon = AtlasIcons.plus)
}

@Composable
private fun HistoryLine(row: HistoryRowUi, onClick: () -> Unit) {
    val c = Atlas.colors
    Column(Modifier.fillMaxWidth().clickable(onClickLabel = "Remove from history", onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Atlas.dimens.touchTarget).padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconBox(row.icon.vector(), tint = c.ink2, outline = c.rule2)
            Column(Modifier.weight(1f)) {
                BasicText(row.label, style = Atlas.type.uiStrong.copy(color = c.ink))
                val sub = listOf(row.date, row.detail).filter { it.isNotEmpty() }.joinToString(" · ")
                BasicText(sub, Modifier.padding(top = 2.dp), style = Atlas.type.src.copy(color = c.ink3))
                row.note?.let { BasicText(it, Modifier.padding(top = 2.dp), style = Atlas.type.uiSmall.copy(color = c.ink2)) }
            }
        }
        Hairline(color = c.rule2)
    }
}

/** Confirms removing a history line. The item's reminder is left as it is. */
@Composable
internal fun RemoveRecordSheet(row: HistoryRowUi?, onDismiss: () -> Unit, onRemove: (Long) -> Unit) {
    AtlasBottomSheet(row != null, onDismiss, title = row?.let { "Remove ${it.label} on ${it.date}?" }) {
        Kicker("Service history")
        Note("Only the history line goes. The reminder keeps counting from the last service.", Modifier.padding(top = 8.dp))
        Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton("Keep", onDismiss, Modifier.weight(1f))
            PrimaryButton("Remove", { row?.let { onRemove(it.id) } }, Modifier.weight(1f), icon = AtlasIcons.cross)
        }
    }
}

/** The logbook items as chips, one chosen (radio buttons for TalkBack). */
@Composable
internal fun ItemChips(items: List<ServiceItem>, selected: Long?, onSelect: (Long) -> Unit) {
    val c = Atlas.colors
    FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            val on = item.id == selected
            Box(
                Modifier
                    .heightIn(min = Atlas.dimens.touchTarget)
                    .border(Atlas.dimens.outline, if (on) c.ink else c.rule2, AtlasShapes.small)
                    .background(if (on) c.ink else c.paper.copy(alpha = 0f), AtlasShapes.small)
                    .selectable(on, role = Role.RadioButton, onClick = { onSelect(item.id) })
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(item.label, style = Atlas.type.uiStrong.copy(color = if (on) c.paper else c.ink2))
            }
        }
    }
}
