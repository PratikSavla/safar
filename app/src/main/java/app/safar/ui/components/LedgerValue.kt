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
import app.safar.ui.theme.Atlas

/** Size of a ledger figure: LARGE for the odometer, SMALL for grid cells. */
enum class LedgerSize { LARGE, SMALL }

/**
 * A bike or phone value as a ledger entry: optional kicker, big serif number with an italic unit,
 * and its source and age underneath ("↳ bike · 3 s ago"). [stale] greys the number when the
 * reading is old. TalkBack reads it as one phrase.
 */
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
    val c = Atlas.colors
    val style = if (size == LedgerSize.LARGE) Atlas.type.numberLarge else Atlas.type.numberSmall
    val spoken = listOfNotNull(label, "$value ${unit.orEmpty()}".trim(), "from $source", age).joinToString(", ")
    Column(modifier.clearAndSetSemantics { contentDescription = spoken }) {
        if (label != null) Kicker(label)
        BasicText(
            buildAnnotatedString {
                append(value)
                if (unit != null) {
                    withStyle(SpanStyle(fontSize = style.fontSize * 0.4f, fontStyle = FontStyle.Italic, fontWeight = FontWeight(340), color = c.ink2, letterSpacing = 0.em)) {
                        append(" $unit")
                    }
                }
            },
            Modifier.padding(top = if (label != null) 4.dp else 0.dp, bottom = 2.dp),
            style = style.copy(color = if (stale) c.ink2 else valueColor),
            maxLines = 1,
        )
        SourceLine(source, age = age)
    }
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
    LedgerValue("12,345", "bike", unit = "km", label = "Odometer", age = "3 s ago")
    LedgerValue("142.6", "bike", unit = "km", label = "Trip A", age = "3 s ago", size = LedgerSize.SMALL)
    LeaderRow("Firmware", "1.04.2")
}
