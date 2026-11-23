package app.safar.ui.service

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.safar.data.ServiceItem
import app.safar.ui.art.AtlasIcons
import app.safar.ui.components.AtlasBottomSheet
import app.safar.ui.components.AtlasTextField
import app.safar.ui.components.Kicker
import app.safar.ui.components.Note
import app.safar.ui.components.PrimaryButton
import app.safar.ui.components.SecondaryButton
import app.safar.ui.components.showPastDatePicker
import app.safar.ui.theme.Atlas
import java.time.LocalDate

private const val NO_ITEM = -1L

/** Keeps the sheet's fields across rotation and process death. */
private val PastSaver = listSaver<PastForm, Any>(
    save = { listOf(it.itemId ?: NO_ITEM, it.day, it.odo, it.cost, it.note) },
    restore = { PastForm((it[0] as Long).takeIf { id -> id != NO_ITEM }, it[1] as Long, it[2] as String, it[3] as String, it[4] as String) },
)

/** What the sheet needs from the logbook. */
internal class PastSheetSpec(
    val visible: Boolean,
    val items: List<ServiceItem>,
    val today: LocalDate,
    val currency: String,
    val lockReason: String?,
)

/**
 * Log a service done before (an oil change last spring, the first service at the dealer): what, when, the
 * odometer then, and an optional cost and note. The text fields lock while moving (UX rule 3).
 */
@Composable
internal fun PastServiceSheet(spec: PastSheetSpec, onDismiss: () -> Unit, onSave: (PastForm) -> Unit) {
    val today = spec.today.toEpochDay()
    var form by rememberSaveable(spec.visible, stateSaver = PastSaver) { mutableStateOf(PastForm(day = today)) }
    val context = LocalContext.current
    AtlasBottomSheet(spec.visible, onDismiss, title = "Log a past service") {
        Kicker("What was done", Modifier.padding(bottom = 8.dp))
        ItemChips(spec.items, form.itemId) { form = form.copy(itemId = it) }
        DayRow(form.day, today) { showPastDatePicker(context, form.day, today) { day -> form = form.copy(day = day) } }
        AtlasTextField(
            form.odo, { form = form.copy(odo = it.filter(Char::isDigit)) }, "Odometer then · km", Modifier.padding(top = 12.dp),
            lockedReason = spec.lockReason, placeholder = "12345", keyboardType = KeyboardType.Number,
            textStyle = Atlas.type.numberSmall,
        )
        AtlasTextField(
            form.cost, { form = form.copy(cost = it) }, "Cost · ${spec.currency} · optional", Modifier.padding(top = 12.dp),
            lockedReason = spec.lockReason, placeholder = "0", keyboardType = KeyboardType.Decimal, textStyle = Atlas.type.numberSmall,
        )
        AtlasTextField(
            form.note, { form = form.copy(note = it) }, "Note · optional", Modifier.padding(top = 12.dp),
            lockedReason = spec.lockReason, placeholder = "Dealer, parts, anything", maxLength = 80, textStyle = Atlas.type.ui,
        )
        PastActions(spec, form, onDismiss, onSave)
    }
}

@Composable
private fun DayRow(day: Long, today: Long, onPick: () -> Unit) {
    val c = Atlas.colors
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Kicker("Done on")
            val text = if (day == today) "Today" else LocalDate.ofEpochDay(day).let { "${it.dayOfMonth} ${monthName(it)} ${it.year}" }
            BasicText(text, Modifier.padding(top = 4.dp), style = Atlas.type.uiStrong.copy(color = c.ink))
        }
        SecondaryButton("Change", onPick, small = true)
    }
}

private fun monthName(date: LocalDate): String =
    date.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)

@Composable
private fun PastActions(spec: PastSheetSpec, form: PastForm, onDismiss: () -> Unit, onSave: (PastForm) -> Unit) {
    val problem = pastProblem(form, spec.items, spec.today)
    // "Choose what was done" is plain from the chips; say the rest once the rider has picked an item.
    if (problem != null && form.itemId != null) Note(problem, Modifier.padding(top = 12.dp), color = Atlas.colors.rust)
    Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SecondaryButton("Cancel", onDismiss, Modifier.weight(1f))
        PrimaryButton("Log it", { onSave(form) }, Modifier.weight(1f), icon = AtlasIcons.check, enabled = problem == null && spec.lockReason == null)
    }
}
