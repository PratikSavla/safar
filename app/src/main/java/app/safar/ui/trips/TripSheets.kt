package app.safar.ui.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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
import app.safar.ui.format.Formatters

private const val TITLE_MAX = 40

/** Rename sheet. The field locks while the phone moves (UX rule 3) and says why. */
@Composable
internal fun RenameSheet(visible: Boolean, title: String, lockReason: String?, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by rememberSaveable(visible, title) { mutableStateOf(title) }
    AtlasBottomSheet(visible, onDismiss, title = "Rename this ride") {
        AtlasTextField(text, { text = it }, label = "Title", lockedReason = lockReason, maxLength = TITLE_MAX)
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton("Cancel", onDismiss, Modifier.weight(1f))
            PrimaryButton("Save", { onSave(text) }, Modifier.weight(1f), icon = AtlasIcons.check, enabled = lockReason == null && text.isNotBlank())
        }
    }
}

/** Delete confirmation: says what goes and what stays. */
@Composable
internal fun DeleteSheet(visible: Boolean, trip: TripDetailUi, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AtlasBottomSheet(visible, onDismiss, title = "Delete ${trip.title}?") {
        Kicker("Ride ${entryNumber(trip.number)} · ${trip.day}")
        Note(
            "The ride (${Formatters.km1(trip.km)} km) and its route are deleted. Your fuel-ups and odometer stay as they are.",
            Modifier.padding(top = 8.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton("Keep", onDismiss, Modifier.weight(1f))
            PrimaryButton("Delete", onConfirm, Modifier.weight(1f), icon = AtlasIcons.cross)
        }
    }
}
