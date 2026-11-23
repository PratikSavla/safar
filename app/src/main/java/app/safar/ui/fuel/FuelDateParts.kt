package app.safar.ui.fuel

import android.app.DatePickerDialog
import android.content.Context
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
import java.time.LocalDate
import java.time.ZoneId

private const val DAY_MILLIS = 86_400_000L

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

/** Android's date picker at [day], no later than [today] (both days since 1970-01-01). */
internal fun showFillDatePicker(context: Context, day: Long, today: Long, onPicked: (Long) -> Unit) {
    val start = LocalDate.ofEpochDay(day)
    val dialog = DatePickerDialog(
        context,
        { _, y, m, d -> onPicked(LocalDate.of(y, m + 1, d).toEpochDay()) },
        start.year, start.monthValue - 1, start.dayOfMonth,
    )
    // The end of today in this zone, so today itself stays selectable.
    val endOfToday = LocalDate.ofEpochDay(today).plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
    dialog.datePicker.maxDate = if (today > 0) endOfToday else System.currentTimeMillis() + DAY_MILLIS
    dialog.show()
}
