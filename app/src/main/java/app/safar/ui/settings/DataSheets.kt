package app.safar.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.safar.ui.art.AtlasIcons
import app.safar.ui.components.AtlasBottomSheet
import app.safar.ui.components.AtlasTextField
import app.safar.ui.components.Kicker
import app.safar.ui.components.Note
import app.safar.ui.components.PrimaryButton
import app.safar.ui.components.SecondaryButton
import app.safar.ui.components.TabBarClearance
import app.safar.ui.components.TextAction
import app.safar.ui.theme.Atlas
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The word the rider types to confirm "Delete everything". */
const val WIPE_WORD = "DELETE"

private val FILE_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

/** Confirms replacing everything with the export just read. Hidden while [offer] is null. */
@Composable
internal fun ImportSheet(offer: ImportOffer?, onExportFirst: () -> Unit, onReplace: () -> Unit, onCancel: () -> Unit) {
    AtlasBottomSheet(offer != null, onCancel, title = "Replace everything with this file?") {
        if (offer == null) return@AtlasBottomSheet
        Kicker("Import")
        Note(importText(offer, ZoneId.systemDefault()), Modifier.padding(top = 8.dp))
        if (offer.missingDocuments > 0) {
            val n = offer.missingDocuments
            Note(
                "$n ${if (n == 1) "paper" else "papers"} in the file had no attachment and will be left out.",
                Modifier.padding(top = 8.dp),
                color = Atlas.colors.amber,
            )
        }
        TextAction("Export what’s here first", onExportFirst, Modifier.padding(top = 12.dp), icon = AtlasIcons.export)
        Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton("Cancel", onCancel, Modifier.weight(1f))
            PrimaryButton("Replace", onReplace, Modifier.weight(1f), icon = AtlasIcons.check)
        }
        // Settings is a tab page: the shell's tabs sit over the bottom of the sheet.
        Spacer(Modifier.height(TabBarClearance))
    }
}

internal fun importText(offer: ImportOffer, zone: ZoneId): String {
    val date = if (offer.exportedAtMillis > 0) " from ${FILE_DATE.format(Instant.ofEpochMilli(offer.exportedAtMillis).atZone(zone))}" else ""
    return "This export$date holds ${exportSummary(offer.counts)}. It replaces everything on this phone now. " +
        "Your paired bike stays as it is."
}

/** Confirms "Delete everything": the rider types [WIPE_WORD] to enable the button. */
@Composable
internal fun WipeSheet(visible: Boolean, onExportFirst: () -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var typed by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(visible) { if (!visible) typed = "" }
    AtlasBottomSheet(visible, onDismiss, title = "Delete everything?") {
        Kicker("Start fresh")
        Note(WIPE_TEXT, Modifier.padding(top = 8.dp))
        TextAction("Export everything first", onExportFirst, Modifier.padding(top = 12.dp), icon = AtlasIcons.export)
        AtlasTextField(typed, { typed = it }, "Type $WIPE_WORD to confirm", Modifier.padding(top = 14.dp), placeholder = WIPE_WORD, maxLength = 12)
        Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton("Keep my data", onDismiss, Modifier.weight(1f))
            PrimaryButton("Delete", onConfirm, Modifier.weight(1f), icon = AtlasIcons.cross, enabled = wipeConfirmed(typed))
        }
        Spacer(Modifier.height(TabBarClearance))
    }
}

internal fun wipeConfirmed(typed: String): Boolean = typed.trim().equals(WIPE_WORD, ignoreCase = true)

private const val WIPE_TEXT =
    "This removes your rides and their routes, fuel log, service reminders, places, parked spot, papers and " +
        "settings from this phone, and forgets your bike. It can’t be undone. Safar then starts from the beginning."
