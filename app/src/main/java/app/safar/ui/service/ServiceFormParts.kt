package app.safar.ui.service

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.safar.data.SpanUnit
import app.safar.data.TimeSpan
import app.safar.ui.art.AtlasIcons
import app.safar.ui.components.AtlasButton
import app.safar.ui.components.AtlasTextField
import app.safar.ui.components.ButtonTone
import app.safar.ui.components.Kicker
import app.safar.ui.components.Note
import app.safar.ui.components.NumberWithUnit
import app.safar.ui.components.SegmentedChoice
import app.safar.ui.components.SwitchRow
import app.safar.ui.format.Formatters
import app.safar.ui.theme.Atlas
import java.time.LocalDate

private val DUE_BY = listOf(DueBy.DISTANCE to "Distance", DueBy.TIME to "Time", DueBy.BOTH to "Both")
private val UNITS = listOf(SpanUnit.DAYS to "Days", SpanUnit.WEEKS to "Weeks", SpanUnit.MONTHS to "Months")

/** Minus, a value, plus (`.stepper`). [less] and [more] are what TalkBack calls the two buttons. */
@Composable
private fun Stepper(less: String, more: String, onStep: (Int) -> Unit, value: @Composable (Modifier) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AtlasButton("", { onStep(-1) }, Modifier.width(56.dp).semantics { contentDescription = less }, ButtonTone.GHOST, AtlasIcons.minus)
        value(Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite })
        AtlasButton("", { onStep(+1) }, Modifier.width(56.dp).semantics { contentDescription = more }, ButtonTone.GHOST, AtlasIcons.plus)
    }
}

private val bigNumber @Composable get() = Atlas.type.numberLarge.copy(fontSize = Atlas.type.numberLarge.fontSize * 0.93f, textAlign = TextAlign.Center)

/** Distance, time or both (`.seg`). */
@Composable
internal fun DueByChoice(dueBy: DueBy, onChange: (DueBy) -> Unit) {
    Kicker("Due by", Modifier.padding(top = 18.dp, bottom = 10.dp))
    SegmentedChoice(DUE_BY, dueBy, onChange)
}

/** The distance interval in km. */
@Composable
internal fun KmIntervalStepper(km: Int, onChange: (Int) -> Unit) {
    Kicker("Every (distance)", Modifier.padding(top = 18.dp))
    Stepper("Shorter interval", "Longer interval", { onChange(stepInterval(km, it)) }) { m ->
        NumberWithUnit(Formatters.odometer(km), "km", bigNumber, m)
    }
    Note("Steps of ${Formatters.odometer(intervalStep(km))} km. Counted from your odometer at the last service.")
}

/** The time interval: a count and its unit. */
@Composable
internal fun TimeIntervalStepper(span: TimeSpan, onChange: (TimeSpan) -> Unit) {
    Kicker("Every (time)", Modifier.padding(top = 18.dp))
    Stepper("Shorter interval", "Longer interval", { onChange(stepCount(span, it)) }) { m ->
        BasicText(spanText(span), m, style = bigNumber.copy(color = Atlas.colors.ink, fontSize = Atlas.type.numberLarge.fontSize * 0.6f))
    }
    SegmentedChoice(UNITS, span.unit, { onChange(withUnit(span, it)) })
    Note("Counted from the day of the last service.", Modifier.padding(top = 8.dp))
}

/** Switch for a first service that differs from the interval, and its odometer and date when on. */
@Composable
internal fun FirstServiceFields(
    form: ServiceForm,
    today: LocalDate,
    lockReason: String?,
    onToggle: (Boolean) -> Unit,
    onChange: (ServiceForm) -> Unit,
) {
    SwitchRow(
        "Different first service", form.firstOn, onToggle, Modifier.padding(top = 10.dp),
        subtitle = "Due once at a set odometer or date, then every interval above.",
    )
    if (!form.firstOn) return
    if (form.byKm) {
        AtlasTextField(
            form.firstAtKmText, { onChange(form.copy(firstAtKmText = it.filter(Char::isDigit))) },
            label = "First at (odometer, km)", lockedReason = lockReason, placeholder = "1000", maxLength = 7,
            keyboardType = KeyboardType.Number,
        )
    }
    val date = form.firstByDate
    if (form.byTime && date != null) {
        Kicker("First by", Modifier.padding(top = 12.dp))
        Stepper("Earlier date", "Later date", { onChange(form.copy(firstByDate = stepFirstDate(date, form.everyTime.unit, it, today))) }) { m ->
            BasicText(longDate(date), m, style = bigNumber.copy(color = Atlas.colors.ink, fontSize = Atlas.type.numberLarge.fontSize * 0.5f))
        }
    }
}
