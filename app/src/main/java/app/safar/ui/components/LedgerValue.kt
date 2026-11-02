package app.safar.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.safar.ui.format.ValueSource
import app.safar.ui.theme.Atlas

/** Size of a ledger figure: LARGE for the odometer, SMALL for grid cells. */
enum class LedgerSize { LARGE, SMALL }

/**
 * A bike or phone value as a ledger entry: optional kicker, big serif number with an italic unit, and its
 * [Freshness] underneath (nothing while fresh, a calm note when stale; the full line in Developer mode).
 * The number greys once the value is stale or from a dropped link. TalkBack reads it as one phrase.
 */
@Composable
fun LedgerValue(
    value: String,
    origin: ValueOrigin?,
    modifier: Modifier = Modifier,
    unit: String? = null,
    label: String? = null,
    size: LedgerSize = LedgerSize.LARGE,
    valueColor: Color = Atlas.colors.ink,
) {
    val stale = origin != null && rememberStale(origin)
    val spokenValue = "$value ${unit.orEmpty()}".trim()
    Column(modifier.semantics(mergeDescendants = true) {}) {
        if (label != null) Kicker(label)
        LedgerNumber(value, unit, size, if (stale) Atlas.colors.ink2 else valueColor, label != null, Modifier.semantics { contentDescription = spokenValue })
        if (origin != null) Freshness(origin)
    }
}

/** Old form with a free-text source and a ready-made age: the caption is Developer mode only. */
@Deprecated("Use LedgerValue(value, ValueOrigin(...)).")
@Composable
fun LedgerValue(
    value: String,
    source: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    label: String? = null,
    age: String? = null,
    size: LedgerSize = LedgerSize.LARGE,
    stale: Boolean = false,
    valueColor: Color = Atlas.colors.ink,
) {
    val spoken = listOfNotNull(label, "$value ${unit.orEmpty()}".trim()).joinToString(", ")
    Column(modifier.clearAndSetSemantics { contentDescription = spoken }) {
        if (label != null) Kicker(label)
        LedgerNumber(value, unit, size, if (stale) Atlas.colors.ink2 else valueColor, label != null)
        DevOnly { DeveloperLine(if (age == null) "↳ $source" else "↳ $source · $age") }
    }
}

@Composable
private fun LedgerNumber(value: String, unit: String?, size: LedgerSize, color: Color, belowLabel: Boolean, modifier: Modifier = Modifier) {
    val c = Atlas.colors
    val style = if (size == LedgerSize.LARGE) Atlas.type.numberLarge else Atlas.type.numberSmall
    BasicText(
        buildAnnotatedString {
            append(value)
            if (unit != null) {
                withStyle(SpanStyle(fontSize = style.fontSize * 0.4f, fontStyle = FontStyle.Italic, fontWeight = FontWeight(340), color = c.ink2, letterSpacing = 0.em)) {
                    append(" $unit")
                }
            }
        },
        modifier.padding(top = if (belowLabel) 4.dp else 0.dp, bottom = 2.dp),
        style = style.copy(color = color),
        maxLines = 1,
    )
}

/** Label ...... value row with dot leaders (`.lead`), for specs and decoded fields. */
@Composable
fun LeaderRow(label: String, value: String, modifier: Modifier = Modifier) {
    val c = Atlas.colors
    Row(
        modifier.fillMaxWidth().padding(vertical = 9.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText(label, style = Atlas.type.ui.copy(fontSize = Atlas.type.note.fontSize * 1.08f, color = c.ink2), maxLines = 1)
        DashedRule(Modifier.weight(1f).widthIn(min = 12.dp).padding(bottom = 4.dp), thickness = 1.5.dp, gap = 3.5.dp)
        BasicText(value, style = Atlas.type.monoValue.copy(color = c.ink, textAlign = TextAlign.End))
    }
}

@Preview
@Composable
private fun LedgerPreview() = PreviewSurface {
    LedgerValue("12,345", ValueOrigin(ValueSource.BIKE, atMillis = null), unit = "km", label = "Odometer")
    LedgerValue("142.6", null, unit = "km", label = "Trip A", size = LedgerSize.SMALL)
    LeaderRow("Firmware", "1.04.2")
}
