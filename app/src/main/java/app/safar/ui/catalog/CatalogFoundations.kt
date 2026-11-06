package app.safar.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.safar.ui.components.DashedRule
import app.safar.ui.components.DisplayTitle
import app.safar.ui.components.FuelBars
import app.safar.ui.components.Gap
import app.safar.ui.components.Hairline
import app.safar.ui.components.Kicker
import app.safar.ui.components.LedgerSize
import app.safar.ui.components.LedgerValue
import app.safar.ui.components.Lede
import app.safar.ui.components.Note
import app.safar.ui.components.SectionHeader
import app.safar.ui.components.SourceLine
import app.safar.ui.components.Stamp
import app.safar.ui.components.StampTone
import app.safar.ui.format.Formatters
import app.safar.ui.theme.Atlas
import app.safar.ui.theme.AtlasColors
import app.safar.ui.theme.AtlasShapes

@Composable
internal fun TypeSection() {
    val t = Atlas.type
    val ink = Atlas.colors.ink
    SectionHeader("Type") { Kicker("Fraunces · Plex Mono · Instrument") }
    DisplayTitle("Margins", " & notes.", t.displayLarge)
    DisplayTitle("Bike information", null, t.displayMedium, Modifier.padding(top = 8.dp))
    DisplayTitle("Log a fuel-up", null, t.displaySmall, Modifier.padding(top = 8.dp))
    BasicText("0123456789", Modifier.padding(top = 8.dp), style = t.numberLarge.copy(color = ink))
    Kicker("Kicker · mono · uppercase", Modifier.padding(top = 8.dp))
    Gap(6.dp)
    Lede("Safar sends turns, callers and the clock to your cluster. No account, no server.")
    Gap(6.dp)
    Note("Note text for explanations. Only names and counts go to the bike.")
    BasicText("Instrument Sans body text at 16 sp", Modifier.padding(top = 6.dp), style = t.ui.copy(color = ink))
    BasicText("Serif list title", Modifier.padding(top = 6.dp), style = t.serifTitle.copy(color = ink))
    BasicText("italic serif aside", Modifier.padding(top = 4.dp), style = t.serifItalic.copy(color = Atlas.colors.ink2))
    SourceLine("bike", age = "3 s ago", modifier = Modifier.padding(top = 6.dp))
}

private fun swatches(c: AtlasColors): List<Pair<String, Color>> = listOf(
    "paper" to c.paper, "paper2" to c.paper2, "card" to c.card, "card2" to c.card2,
    "ink" to c.ink, "ink2" to c.ink2, "ink3" to c.ink3, "rule" to c.rule, "rule2" to c.rule2,
    "rust" to c.rust, "rust2" to c.rust2, "rustWash" to c.rustWash, "onRust" to c.onRust,
    "moss" to c.moss, "mossWash" to c.mossWash, "slate" to c.slate, "amber" to c.amber, "amberWash" to c.amberWash,
    "contour" to c.contour, "contourIdx" to c.contourIdx, "scrim" to c.scrim,
    "lcd" to c.lcd, "lcdInk" to c.lcdInk, "lcdDim" to c.lcdDim,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColourSection() {
    val c = Atlas.colors
    SectionHeader("Colour") { Kicker(if (c.isDark) "blue-ink" else "paper") }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        swatches(c).forEach { (name, color) ->
            Column(Modifier.width(72.dp)) {
                Spacer(
                    Modifier.padding(bottom = 4.dp).size(72.dp, 32.dp).background(color, AtlasShapes.small)
                        .border(1.dp, c.rule, AtlasShapes.small),
                )
                BasicText(name, style = Atlas.type.src.copy(color = c.ink2), maxLines = 1)
            }
        }
    }
}

@Composable
internal fun LedgerSection() {
    val now = remember { System.currentTimeMillis() }
    SectionHeader("Ledger") { Kicker("every value shows its source") }
    Row(verticalAlignment = Alignment.Bottom) {
        LedgerValue(Formatters.odometer(12_345), "bike", Modifier.weight(1f), unit = "km", label = "Odometer", age = Formatters.age(now - 3_000, now))
        FuelBars(4, 6)
    }
    Hairline(Modifier.padding(top = 8.dp), color = Atlas.colors.ink, thickness = 1.5.dp)
    Row(Modifier.fillMaxWidth().padding(top = 9.dp)) {
        LedgerValue(Formatters.km1(142.64), "bike", Modifier.weight(1f), unit = "km", label = "Trip A", age = "3 s ago", size = LedgerSize.SMALL)
        LedgerValue("≈312", "calculated", Modifier.weight(1f), unit = "km", label = "Range", size = LedgerSize.SMALL)
        LedgerValue("84", "bike", Modifier.weight(1f), unit = "°C", label = "Engine", age = "5 s", size = LedgerSize.SMALL, stale = true)
    }
    Gap(14.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Bottom) {
        FuelSample("6 bars", 6, 6)
        FuelSample("5 · low", 1, 5)
        FuelSample("empty", 0, 6)
    }
    Gap(10.dp)
    DashedRule()
}

@Composable
private fun FuelSample(label: String, bars: Int, max: Int) {
    Column {
        FuelBars(bars, max)
        Kicker(label, Modifier.padding(top = 4.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StampSection() {
    SectionHeader("Stamps")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Stamp("Linked", tone = StampTone.MOSS)
        Stamp("Probable", tone = StampTone.SLATE)
        Stamp("No account")
        Stamp("Low fuel", tone = StampTone.AMBER)
        Stamp("tip", tone = StampTone.INK, tilted = false)
        Stamp("Ride mode · auto", solid = true)
    }
}
