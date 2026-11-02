package app.safar.ui.service

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.safar.data.ServiceItem
import app.safar.ui.art.AtlasIcons
import app.safar.ui.components.AtlasBottomSheet
import app.safar.ui.components.AtlasButton
import app.safar.ui.components.AtlasTextField
import app.safar.ui.components.ButtonTone
import app.safar.ui.components.Kicker
import app.safar.ui.components.Note
import app.safar.ui.components.NumberWithUnit
import app.safar.ui.components.PrimaryButton
import app.safar.ui.components.SecondaryButton
import app.safar.ui.components.TextAction
import app.safar.ui.format.Formatters
import app.safar.ui.theme.Atlas

private const val NEW_ITEM_KM = 1000

/** Minus, the interval in km, plus (`.stepper`). */
@Composable
private fun IntervalStepper(km: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AtlasButton("", { onChange(stepInterval(km, -1)) }, Modifier.width(56.dp).semantics { contentDescription = "Less" }, ButtonTone.GHOST, AtlasIcons.minus)
        NumberWithUnit(
            Formatters.odometer(km), "km",
            Atlas.type.numberLarge.copy(fontSize = Atlas.type.numberLarge.fontSize * 0.93f, textAlign = TextAlign.Center),
            Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
        )
        AtlasButton("", { onChange(stepInterval(km, +1)) }, Modifier.width(56.dp).semantics { contentDescription = "More" }, ButtonTone.GHOST, AtlasIcons.plus)
    }
    Note("Steps of ${Formatters.odometer(intervalStep(km))} km. Counted from the odometer at the last service.")
}

/** Change an item's interval, or remove the item (service.js `editSheet`). */
@Composable
internal fun IntervalSheet(item: ServiceItem?, onDismiss: () -> Unit, onSave: (Int) -> Unit, onDelete: () -> Unit) {
    var km by rememberSaveable(item?.id) { mutableIntStateOf(item?.everyKm ?: NEW_ITEM_KM) }
    var confirmDelete by rememberSaveable(item?.id) { mutableStateOf(false) }
    AtlasBottomSheet(item != null, onDismiss, title = item?.label) {
        Kicker("Service interval")
        IntervalStepper(km) { km = it }
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton("Cancel", onDismiss, Modifier.weight(1f))
            PrimaryButton("Save", { onSave(km) }, Modifier.weight(1f), icon = AtlasIcons.check)
        }
        if (confirmDelete) {
            Note("Remove ${item?.label} and its reminders from the logbook?", Modifier.padding(top = 14.dp))
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton("Keep", { confirmDelete = false }, Modifier.weight(1f))
                PrimaryButton("Remove", onDelete, Modifier.weight(1f), icon = AtlasIcons.cross)
            }
        } else {
            TextAction("Remove this item", { confirmDelete = true }, Modifier.padding(top = 10.dp), icon = AtlasIcons.cross, color = Atlas.colors.rust)
        }
    }
}

/** Add a custom item counted from the current odometer. The name field locks while moving (UX rule 3). */
@Composable
internal fun AddItemSheet(visible: Boolean, odoKm: Int?, lockReason: String?, onDismiss: () -> Unit, onAdd: (String, Int) -> Unit) {
    var label by rememberSaveable(visible) { mutableStateOf("") }
    var km by rememberSaveable(visible) { mutableIntStateOf(NEW_ITEM_KM) }
    AtlasBottomSheet(visible, onDismiss, title = "Add to the logbook") {
        AtlasTextField(label, { label = it }, label = "What needs doing", lockedReason = lockReason, placeholder = "Coolant check", maxLength = 32)
        IntervalStepper(km) { km = it }
        BasicText(addFromLine(odoKm), Modifier.padding(top = 6.dp), style = Atlas.type.src.copy(color = Atlas.colors.ink3))
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton("Cancel", onDismiss, Modifier.weight(1f))
            val ready = lockReason == null && odoKm != null && cleanLabel(label) != null
            PrimaryButton("Add", { onAdd(label, km) }, Modifier.weight(1f), icon = AtlasIcons.plus, enabled = ready)
        }
    }
}
