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
import androidx.compose.ui.unit.dp
import app.safar.ui.components.Kicker
import app.safar.ui.components.SecondaryButton
import app.safar.ui.theme.Atlas

/** The fill's date with a Change button: a receipt can be logged on a later day than the fill. */
@Composable
internal fun FillDateRow(state: FuelUpUiState, onPick: () -> Unit) {
    val c = Atlas.colors
    val word = dayWord(state.day, state.today).replaceFirstChar { it.uppercase() }
    Row(
        Modifier.fillMaxWidth().padding(bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Kicker("Filled on")
            BasicText(word, Modifier.padding(top = 4.dp), style = Atlas.type.uiStrong.copy(color = c.ink))
        }
        SecondaryButton("Change", onPick, small = true)
    }
}
