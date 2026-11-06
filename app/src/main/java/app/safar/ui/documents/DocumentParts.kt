package app.safar.ui.documents

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.safar.data.DocKind
import app.safar.data.DocumentFiles
import app.safar.data.label
import app.safar.data.usuallyExpires
import app.safar.ui.art.AtlasIcons
import app.safar.ui.components.AtlasBottomSheet
import app.safar.ui.components.Kicker
import app.safar.ui.components.Note
import app.safar.ui.components.PrimaryButton
import app.safar.ui.components.SecondaryButton
import app.safar.ui.components.TextAction
import app.safar.ui.theme.Atlas
import app.safar.ui.theme.AtlasShapes
import java.io.File
import java.time.LocalDate

/** The paper kinds as chips, one chosen (radio buttons for TalkBack). */
@Composable
internal fun KindChips(selected: DocKind, onSelect: (DocKind) -> Unit) {
    val c = Atlas.colors
    FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DocKind.entries.forEach { kind ->
            val on = kind == selected
            Box(
                Modifier
                    .heightIn(min = Atlas.dimens.touchTarget)
                    .border(Atlas.dimens.outline, if (on) c.ink else c.rule2, AtlasShapes.small)
                    .background(if (on) c.ink else c.paper.copy(alpha = 0f), AtlasShapes.small)
                    .selectable(on, role = Role.RadioButton, onClick = { onSelect(kind) })
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(kind.label(), style = Atlas.type.uiStrong.copy(color = if (on) c.paper else c.ink2))
            }
        }
    }
}

/** The chosen file with a preview, or a button to choose one. */
@Composable
internal fun FileBlock(file: FormFile?, dir: File, reading: Boolean, enabled: Boolean, onChoose: () -> Unit, onOpen: () -> Unit) {
    if (file == null) {
        PrimaryButton(if (reading) "Adding…" else "Choose a PDF or photo", onChoose, Modifier.fillMaxWidth(), icon = AtlasIcons.plus, enabled = enabled && !reading)
        Note("Up to ${DocumentFiles.MAX_BYTES / (1024 * 1024)} MB.", Modifier.padding(top = 6.dp), color = Atlas.colors.ink3)
        return
    }
    val c = Atlas.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PaperThumb(File(dir, file.fileName), file.mimeType, 72.dp)
        Column(Modifier.weight(1f)) {
            val kind = if (DocumentFiles.isPdf(file.mimeType)) "PDF" else "Photo"
            BasicText(file.displayName ?: kind, style = Atlas.type.uiStrong.copy(color = c.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText("$kind · ${sizeText(file.sizeBytes)}", Modifier.padding(top = 2.dp), style = Atlas.type.uiSmall.copy(color = c.ink3))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TextAction("Show", onOpen, Modifier.padding(top = 6.dp))
                TextAction(if (reading) "Adding…" else "Replace", onChoose, Modifier.padding(top = 6.dp), enabled = enabled && !reading)
            }
        }
    }
}

/** Expiry date with set / change / clear. PUC, insurance and licence get a nudge to add one. */
@Composable
internal fun ExpiryBlock(kind: DocKind, expiry: Long?, enabled: Boolean, onPick: () -> Unit, onClear: () -> Unit) {
    val c = Atlas.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            BasicText(expiry?.let(::expiryDateText) ?: "No expiry date", style = Atlas.type.uiStrong.copy(color = if (expiry == null) c.ink3 else c.ink))
            val hint = when {
                expiry != null -> "Safar reminds you 30 and 7 days before, and on the day."
                kind.usuallyExpires() -> "Add the date to get a reminder before it runs out."
                else -> "Optional."
            }
            BasicText(hint, Modifier.padding(top = 2.dp), style = Atlas.type.uiSmall.copy(color = c.ink3))
        }
        SecondaryButton(if (expiry == null) "Set date" else "Change", onPick, enabled = enabled, small = true)
    }
    if (expiry != null) TextAction("Remove expiry date", onClear, Modifier.padding(top = 8.dp), icon = AtlasIcons.cross, enabled = enabled)
}

/** Android's date picker, starting at [current] or a year from today. */
internal fun showExpiryPicker(context: Context, current: Long?, onPicked: (Long) -> Unit) {
    val start = current?.let(LocalDate::ofEpochDay) ?: LocalDate.now().plusYears(1)
    DatePickerDialog(context, { _, y, m, d -> onPicked(LocalDate.of(y, m + 1, d).toEpochDay()) }, start.year, start.monthValue - 1, start.dayOfMonth).show()
}

@Composable
internal fun DeletePaperSheet(visible: Boolean, title: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AtlasBottomSheet(visible, onDismiss, title = "Delete $title?") {
        Kicker("Delete paper")
        Note("The file is removed from this phone. Expiry reminders for it stop.", Modifier.padding(top = 8.dp))
        Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton("Keep", onDismiss, Modifier.weight(1f))
            PrimaryButton("Delete", onConfirm, Modifier.weight(1f), icon = AtlasIcons.cross)
        }
    }
}
