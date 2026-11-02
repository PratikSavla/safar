package app.safar.ui.fuel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.safar.data.FuelField
import app.safar.data.FuelUp
import app.safar.ui.art.AtlasIcons
import app.safar.ui.components.AtlasBottomSheet
import app.safar.ui.components.AtlasTextField
import app.safar.ui.components.DisplayTitle
import app.safar.ui.components.Kicker
import app.safar.ui.components.Note
import app.safar.ui.components.NumberWithUnit
import app.safar.ui.components.PrimaryButton
import app.safar.ui.components.SecondaryButton
import app.safar.ui.components.TextAction
import app.safar.ui.components.liveAge
import app.safar.ui.format.Formatters
import app.safar.ui.theme.Atlas
import java.time.ZoneId
import kotlin.math.roundToInt

/** Litres and amount side by side with decimal keyboards, then the live price per litre (`.ff-pair`). */
@Composable
internal fun AmountFields(state: FuelUpUiState, actions: FuelUpActions) {
    val style = Atlas.type.numberSmall.copy(fontSize = Atlas.type.numberSmall.fontSize * 1.15f)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {
            AtlasTextField(
                state.form.litres, actions.onLitres, label = "Litres", placeholder = "0.0", textStyle = style,
                keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next, trailing = { UnitText("L") },
            )
            FieldLine(state.messages[FuelField.LITRES])
        }
        Column(Modifier.weight(1f)) {
            AtlasTextField(
                state.form.total, actions.onTotal, label = "Amount paid · ${state.currency}", placeholder = "0", textStyle = style,
                keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next,
            )
            FieldLine(state.messages[FuelField.TOTAL])
        }
    }
    BasicText(
        state.perLitre,
        Modifier.padding(top = 10.dp, bottom = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
        style = Atlas.type.src.copy(color = Atlas.colors.rust, fontSize = Atlas.type.src.fontSize * 1.09f),
    )
}

/** Odometer from the bike (read-only with an Edit action) or typed, with where it came from (`.ff-odo`). */
@Composable
internal fun OdometerBlock(state: FuelUpUiState, actions: FuelUpActions) {
    val c = Atlas.colors
    Column(Modifier.padding(top = 12.dp)) {
        if (state.form.editOdo) {
            AtlasTextField(
                state.form.odo, actions.onOdo, label = "Odometer", placeholder = "12345",
                textStyle = Atlas.type.numberSmall.copy(fontSize = Atlas.type.numberSmall.fontSize * 1.15f),
                keyboardType = KeyboardType.Number, imeAction = ImeAction.Done, trailing = { UnitText("km") },
            )
        } else {
            Kicker("Odometer")
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                val odo = state.form.odo.toIntOrNull()?.let(Formatters::odometer) ?: state.form.odo
                NumberWithUnit(odo, "km", Atlas.type.numberSmall.copy(fontSize = Atlas.type.numberSmall.fontSize * 1.15f), Modifier.weight(1f))
                TextAction("Edit", actions.onEditOdo, icon = AtlasIcons.edit)
            }
        }
        OdoSourceLine(state)
        state.bikeSaid?.let { BasicText(it, Modifier.padding(top = 4.dp), style = Atlas.type.src.copy(color = c.amber)) }
        FieldLine(state.messages[FuelField.ODO])
        BasicText("Whole km only, as on the cluster.", Modifier.padding(top = 4.dp), style = Atlas.type.uiSmall.copy(color = c.ink3))
    }
}

@Composable
private fun OdoSourceLine(state: FuelUpUiState) {
    val c = Atlas.colors
    val sugg = state.sugg ?: return
    val age = sugg.atMillis?.let { liveAge(it) }
    val label = odoLabel(state.entry, sugg, age)
    val color = when (label.tone) {
        OdoTone.MOSS -> c.moss
        OdoTone.AMBER -> c.amber
        OdoTone.RUST -> c.rust
        OdoTone.INK -> c.ink2
    }
    val style = if (label.tone == OdoTone.RUST) Atlas.type.uiSmall else Atlas.type.src
    BasicText(label.text, Modifier.padding(top = 4.dp), style = style.copy(color = color))
}

/** Inline validation under a field: rust for an error (blocks Save), amber for a warning. */
@Composable
private fun FieldLine(message: FieldMessage?) {
    if (message == null) return
    val color = if (message.error) Atlas.colors.rust else Atlas.colors.amber
    BasicText(
        message.text,
        Modifier.padding(top = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
        style = Atlas.type.uiSmall.copy(color = color),
    )
}

@Composable
private fun UnitText(unit: String) {
    BasicText(unit, style = Atlas.type.serifItalic.copy(color = Atlas.colors.ink2))
}

/** UX rule 3: no form while the phone moves (fuel-form.js `lockedHtml`). */
@Composable
internal fun LockedForm(speedKmh: Float?, onClose: () -> Unit) {
    DisplayTitle("Stop to log fuel.", null, Atlas.type.displaySmall)
    val speed = speedKmh?.let { " at ${it.roundToInt()} km/h" }.orEmpty()
    Note(
        "The phone is moving$speed. Typing is off while you ride; the form comes back when you stop.",
        Modifier.padding(top = 10.dp),
    )
    SecondaryButton("Close", onClose, Modifier.fillMaxWidth().padding(top = 18.dp))
}

/** Delete confirmation (fuel-form.js `confirmHtml`). */
@Composable
internal fun DeleteFuelUpSheet(visible: Boolean, entry: FuelUp, currency: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val date = fuelDate(entry.atMillis, ZoneId.systemDefault())
    AtlasBottomSheet(visible, onDismiss, title = "Remove ${jsNumber(entry.litres)} L on $date?") {
        Kicker("Delete fuel-up")
        Note(
            "${Formatters.money(entry.totalPaid, currency)} at ${Formatters.odometer(entry.odometerKm)} km. " +
                "Mileage and range are worked out again without it.",
            Modifier.padding(top = 8.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton("Keep", onDismiss, Modifier.weight(1f))
            PrimaryButton("Delete", onConfirm, Modifier.weight(1f), icon = AtlasIcons.cross)
        }
    }
}
