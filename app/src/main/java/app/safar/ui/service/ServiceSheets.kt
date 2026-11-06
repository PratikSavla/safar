package app.safar.ui.service

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.safar.data.ServiceIcon
import app.safar.data.ServiceItem
import app.safar.data.SpanUnit
import app.safar.data.TimeSpan
import app.safar.ui.art.AtlasIcons
import app.safar.ui.components.AtlasBottomSheet
import app.safar.ui.components.AtlasTextField
import app.safar.ui.components.Note
import app.safar.ui.components.PrimaryButton
import app.safar.ui.components.SecondaryButton
import app.safar.ui.components.TextAction
import app.safar.ui.theme.Atlas
import java.time.LocalDate

private const val NO_DATE = Long.MIN_VALUE

/** Keeps the sheet's fields across rotation and process death, as Bundle-friendly values. */
private val FormSaver = listSaver<ServiceForm, Any>(
    save = {
        listOf(
            it.label, it.icon.name, it.dueBy.name, it.everyKm, it.everyTime.count, it.everyTime.unit.name,
            it.firstOn, it.firstAtKmText, it.firstByDate?.toEpochDay() ?: NO_DATE,
        )
    },
    restore = {
        ServiceForm(
            label = it[0] as String,
            icon = ServiceIcon.valueOf(it[1] as String),
            dueBy = DueBy.valueOf(it[2] as String),
            everyKm = it[3] as Int,
            everyTime = TimeSpan(it[4] as Int, SpanUnit.valueOf(it[5] as String)),
            firstOn = it[6] as Boolean,
            firstAtKmText = it[7] as String,
            firstByDate = (it[8] as Long).takeIf { d -> d != NO_DATE }?.let(LocalDate::ofEpochDay),
        )
    },
)

/** What the sheet edits: an existing [item], or a new one when null. */
internal class ServiceSheetSpec(
    val visible: Boolean,
    val item: ServiceItem?,
    val odoKm: Int?,
    val today: LocalDate,
    val lockReason: String?,
)

/**
 * Add an item, or change one (service.js `editSheet`): name (new items only), icon, due by distance, time or both,
 * the intervals, and an optional first service at a set odometer or date. The text fields lock while moving
 * (UX rule 3). [onDelete] is null for a new item.
 */
@Composable
internal fun ServiceItemSheet(spec: ServiceSheetSpec, onDismiss: () -> Unit, onSave: (ServiceForm) -> Unit, onDelete: (() -> Unit)? = null) {
    val item = spec.item
    var form by rememberSaveable(spec.visible, item?.id, stateSaver = FormSaver) { mutableStateOf(item?.let(::formFor) ?: ServiceForm()) }
    AtlasBottomSheet(spec.visible, onDismiss, title = item?.label ?: "Add to the logbook") {
        if (item == null) {
            AtlasTextField(form.label, { form = form.copy(label = it) }, label = "What needs doing", lockedReason = spec.lockReason, placeholder = "Coolant check", maxLength = 32)
        }
        ServiceIconPicker(form.icon) { form = form.copy(icon = it) }
        DueByChoice(form.dueBy) { form = form.copy(dueBy = it) }
        if (form.byKm) KmIntervalStepper(form.everyKm) { form = form.copy(everyKm = it) }
        if (form.byTime) TimeIntervalStepper(form.everyTime) { form = form.copy(everyTime = it) }
        FirstServiceFields(
            form, spec.today, spec.lockReason,
            onToggle = { on -> form = if (on) withFirstOn(form, spec.odoKm, spec.today) else form.copy(firstOn = false) },
            onChange = { form = it },
        )
        if (item == null) BasicText(addFromLine(spec.odoKm, form.dueBy), Modifier.padding(top = 10.dp), style = Atlas.type.src.copy(color = Atlas.colors.ink3))
        SheetActions(spec, form, onDismiss, onSave)
        if (onDelete != null && item != null) RemoveAction(item.label, onDelete)
    }
}

@Composable
private fun SheetActions(spec: ServiceSheetSpec, form: ServiceForm, onDismiss: () -> Unit, onSave: (ServiceForm) -> Unit) {
    val problem = formProblem(form, spec.odoKm, spec.today, spec.item)
    // A new item already says it needs the odometer in its "counted from" line.
    val shown = problem?.takeUnless { spec.item == null && it == addFromLine(spec.odoKm, form.dueBy) }
    if (shown != null) Note(shown, Modifier.padding(top = 12.dp), color = Atlas.colors.rust)
    val named = spec.item != null || cleanLabel(form.label) != null
    val ready = named && problem == null && (spec.item != null || spec.lockReason == null)
    Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SecondaryButton("Cancel", onDismiss, Modifier.weight(1f))
        if (spec.item == null) {
            PrimaryButton("Add", { onSave(form) }, Modifier.weight(1f), icon = AtlasIcons.plus, enabled = ready)
        } else {
            PrimaryButton("Save", { onSave(form) }, Modifier.weight(1f), icon = AtlasIcons.check, enabled = ready)
        }
    }
}

@Composable
private fun RemoveAction(label: String, onDelete: () -> Unit) {
    var confirm by rememberSaveable(label) { mutableStateOf(false) }
    if (confirm) {
        Note("Remove $label and its reminders from the logbook?", Modifier.padding(top = 14.dp))
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton("Keep", { confirm = false }, Modifier.weight(1f))
            PrimaryButton("Remove", onDelete, Modifier.weight(1f), icon = AtlasIcons.cross)
        }
    } else {
        TextAction("Remove this item", { confirm = true }, Modifier.padding(top = 10.dp), icon = AtlasIcons.cross, color = Atlas.colors.rust)
    }
}
