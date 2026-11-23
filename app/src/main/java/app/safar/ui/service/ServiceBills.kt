package app.safar.ui.service

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.safar.data.BikeDocument
import app.safar.data.DocumentFiles
import app.safar.ui.art.AtlasIcons
import app.safar.ui.components.AtlasIcon
import app.safar.ui.components.Kicker
import app.safar.ui.components.Note
import app.safar.ui.components.PrimaryButton
import app.safar.ui.components.SecondaryButton
import app.safar.ui.components.TextAction
import app.safar.ui.documents.PaperThumb
import app.safar.ui.theme.Atlas
import java.io.File

/** One bill in the sheet: a saved paper ([documentId]) or a file picked in this sheet. */
private data class BillLine(val documentId: Long?, val fileName: String, val mimeType: String, val name: String)

/** The bills a history sheet shows, and what it can do with them. */
internal class SheetBills(
    /** Papers already linked to the record being edited. */
    val saved: List<BikeDocument> = emptyList(),
    val draft: BillDraftUi = BillDraftUi(),
    val file: (String) -> File = { File(it) },
    val onAttach: () -> Unit = {},
    val onOpen: (fileName: String, mimeType: String) -> Unit = { _, _ -> },
    val onRemoveSaved: (Long) -> Unit = {},
    val onRemovePending: (String) -> Unit = {},
)

/** Bills and receipts kept with the service, as thumbnails: tap to open, × to take off, and a button to add one. */
@Composable
internal fun BillsBlock(bills: SheetBills, enabled: Boolean) {
    val lines = bills.saved.filter { it.id !in bills.draft.removed }.map { BillLine(it.id, it.fileName, it.mimeType, it.title) } +
        bills.draft.pending.map { BillLine(null, it.fileName, it.mimeType, it.displayName ?: "Bill") }
    Kicker("Bills and receipts", Modifier.padding(top = 18.dp, bottom = 6.dp))
    lines.forEach { BillRow(it, bills, enabled) }
    TextAction(
        if (bills.draft.adding) "Adding…" else "Attach a bill or photo", bills.onAttach, Modifier.padding(top = 2.dp),
        icon = AtlasIcons.plus, enabled = enabled && !bills.draft.adding,
    )
    if (lines.isEmpty()) {
        Note("PDFs or photos, up to ${DocumentFiles.MAX_BYTES / (1024 * 1024)} MB. They are kept in Papers too.", color = Atlas.colors.ink3)
    }
}

@Composable
private fun BillRow(line: BillLine, bills: SheetBills, enabled: Boolean) {
    val c = Atlas.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.weight(1f).clickable(onClickLabel = "Open") { bills.onOpen(line.fileName, line.mimeType) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PaperThumb(bills.file(line.fileName), line.mimeType, 48.dp)
            Column(Modifier.weight(1f)) {
                BasicText(line.name, style = Atlas.type.uiStrong.copy(color = c.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                val kind = if (DocumentFiles.isPdf(line.mimeType)) "PDF" else "Photo"
                BasicText(if (line.documentId == null) "$kind · not saved yet" else kind, style = Atlas.type.uiSmall.copy(color = c.ink3))
            }
        }
        val remove = { if (line.documentId != null) bills.onRemoveSaved(line.documentId) else bills.onRemovePending(line.fileName) }
        AtlasIcon(
            AtlasIcons.cross,
            Modifier.clickable(enabled = enabled, onClick = remove).padding(12.dp).semantics { contentDescription = "Remove ${line.name}" },
            tint = c.ink2,
        )
    }
}

/** "Remove from history", confirmed in place. Bills stay in Papers. */
@Composable
internal fun RemoveRecordAction(onDelete: () -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    if (!confirm) {
        TextAction("Remove from history", { confirm = true }, Modifier.padding(top = 10.dp), icon = AtlasIcons.cross, color = Atlas.colors.rust)
        return
    }
    Note("Only the history line goes. The reminder keeps counting from the last service, and its bills stay in Papers.", Modifier.padding(top = 14.dp))
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SecondaryButton("Keep", { confirm = false }, Modifier.weight(1f))
        PrimaryButton("Remove", onDelete, Modifier.weight(1f), icon = AtlasIcons.cross)
    }
}
