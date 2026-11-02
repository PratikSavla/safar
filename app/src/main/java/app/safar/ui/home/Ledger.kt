package app.safar.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.safar.ui.art.AtlasIcons
import app.safar.ui.components.AtlasIcon
import app.safar.ui.components.DevOnly
import app.safar.ui.components.DeveloperLine
import app.safar.ui.components.EmptyState
import app.safar.ui.components.Freshness
import app.safar.ui.components.FreshnessNote
import app.safar.ui.components.FuelBars
import app.safar.ui.components.Hairline
import app.safar.ui.components.Kicker
import app.safar.ui.components.LedgerSize
import app.safar.ui.components.LedgerValue
import app.safar.ui.components.Stamp
import app.safar.ui.components.StampTone
import app.safar.ui.components.ValueOrigin
import app.safar.ui.components.rememberStale
import app.safar.ui.format.Formatters
import app.safar.ui.format.ValueSource
import app.safar.ui.theme.Atlas
import app.safar.ui.theme.AtlasShapes

/**
 * The ledger (home.js `dashboard`): odometer across, then a grid of bike, phone and calculated values.
 * The bike's freshness shows once, under the odometer, and greys every bike figure together; it says nothing
 * while the reading is fresh. [speed] is read only in its own cell, so a GPS fix recomposes that cell alone.
 */
@Composable
fun Ledger(ledger: LedgerUi?, speed: () -> SpeedUi) {
    if (ledger == null) {
        EmptyState("A blank page, for now.", message = "Odometer, trips and fuel appear here a few seconds after the bike connects.")
        return
    }
    val origin = ledger.bikeOrigin
    // Recomposes the grid only when the reading turns stale (or fresh again), not on every clock tick.
    val bikeInk = if (rememberStale(origin)) Atlas.colors.ink2 else Atlas.colors.ink
    Column(Modifier.fillMaxWidth()) {
        OdometerRow(ledger, origin, bikeInk)
        val columns = if (LocalDensity.current.fontScale > 1.3f) 2 else 3
        cells(ledger, bikeInk, speed).chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                row.forEachIndexed { i, cell ->
                    if (i > 0) DashedDivider()
                    Box(Modifier.weight(1f).padding(start = if (i > 0) 10.dp else 0.dp, top = 9.dp, bottom = 8.dp)) { cell() }
                }
                repeat(columns - row.size) { Box(Modifier.weight(1f)) }
            }
            Hairline(color = Atlas.colors.rule2)
        }
        if (ledger.warningText != null) FuelWarningStrip(ledger.fuelWarning == FuelWarning.EMPTY, ledger.warningText)
    }
}

@Composable
private fun OdometerRow(l: LedgerUi, origin: ValueOrigin, ink: Color) {
    val c = Atlas.colors
    val style = Atlas.type.numberLarge
    val odometer = Formatters.odometer(l.odometerKm)
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Kicker("Odometer")
            Freshness(origin, Modifier.padding(top = 4.dp))
        }
        BasicText(
            buildAnnotatedString {
                append(odometer)
                withStyle(SpanStyle(fontSize = style.fontSize * 0.36f, fontStyle = FontStyle.Italic, fontWeight = FontWeight(340), color = c.ink2)) { append(" km") }
            },
            Modifier.semantics { contentDescription = "$odometer km" },
            style = style.copy(color = ink),
            maxLines = 1,
        )
    }
    Hairline(thickness = 1.5.dp, color = c.ink)
}

private fun cells(l: LedgerUi, bikeInk: Color, speed: () -> SpeedUi): List<@Composable () -> Unit> = listOf(
    { LedgerValue(Formatters.km1(l.tripAKm), null, unit = "km", label = "Trip A", size = LedgerSize.SMALL, valueColor = bikeInk) },
    { LedgerValue(Formatters.km1(l.tripBKm), null, unit = "km", label = "Trip B", size = LedgerSize.SMALL, valueColor = bikeInk) },
    { SpeedCell(speed) },
    { FuelCell(l, bikeInk) },
    { RangeCell(l) },
    { GearCell(l, bikeInk) },
)

/** Phone GPS speed: the only cell that reads the 1 Hz fix. Nothing under it while moving. */
@Composable
private fun SpeedCell(speed: () -> SpeedUi) {
    val s = speed()
    val value = s.kmh?.toString() ?: "—"
    if (s.live) {
        Column(Modifier.semantics(mergeDescendants = true) {}) {
            LedgerValue(value, null, unit = "km/h", label = "Speed", size = LedgerSize.SMALL)
            DevOnly { DeveloperLine("↳ ${ValueSource.PHONE_GPS.label} · live") }
        }
    } else {
        LedgerValue(value, ValueOrigin(ValueSource.PHONE_GPS, s.fixAt), unit = "km/h", label = "Speed", size = LedgerSize.SMALL)
    }
}

@Composable
private fun FuelCell(l: LedgerUi, bikeInk: Color) {
    val c = Atlas.colors
    val low = (l.fuelBars ?: Int.MAX_VALUE) <= 1
    val spoken = if (l.fuelBars == null) "Fuel, no reading yet" else "Fuel, ${l.fuelBars} of ${l.fuelMax} bars"
    Column(Modifier.clearAndSetSemantics { contentDescription = spoken }) {
        Kicker("Fuel")
        Row(Modifier.padding(top = 4.dp, bottom = 2.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (l.fuelBars != null) FuelBars(l.fuelBars, l.fuelMax, height = 26.dp, barWidth = 8.dp, gap = 3.dp)
            BasicText(
                buildAnnotatedString {
                    append(l.fuelBars?.toString() ?: "—")
                    withStyle(SpanStyle(fontSize = Atlas.type.numberSmall.fontSize * 0.5f, fontStyle = FontStyle.Italic, color = c.ink2)) { append("/${l.fuelMax}") }
                },
                style = Atlas.type.numberSmall.copy(color = if (low) c.amber else bikeInk),
                maxLines = 1,
            )
        }
    }
}

/** Range: a note only when it is a rough guess or cannot be worked out yet; the method in Developer mode. */
@Composable
private fun RangeCell(l: LedgerUi) {
    Column(Modifier.semantics(mergeDescendants = true) {}) {
        LedgerValue(l.rangeText, null, unit = "km", label = "Range", size = LedgerSize.SMALL)
        l.rangeNote?.let { FreshnessNote(it) }
        DevOnly { DeveloperLine("↳ ${l.rangeSource}") }
    }
}

/** The gear is a best guess from the bike: the stamp says so, and TalkBack says "probably". */
@Composable
private fun GearCell(l: LedgerUi, bikeInk: Color) {
    val spoken = if (l.gear == NO_GEAR) "Gear, no reading yet" else "Gear ${l.gear}, probably"
    Column(Modifier.clearAndSetSemantics { contentDescription = spoken }) {
        Kicker("Gear")
        Row(Modifier.padding(top = 4.dp, bottom = 2.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BasicText(l.gear, style = Atlas.type.numberSmall.copy(color = bikeInk), maxLines = 1)
            Stamp("probable", tone = StampTone.INK)
        }
    }
}

@Composable
private fun DashedDivider() {
    val color = Atlas.colors.rule2
    Canvas(Modifier.fillMaxHeight().width(1.dp)) {
        drawLine(color, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
    }
}

@Composable
private fun FuelWarningStrip(empty: Boolean, text: String) {
    val c = Atlas.colors
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp).background(if (empty) c.rustWash else c.amberWash, AtlasShapes.small).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AtlasIcon(AtlasIcons.fuel, tint = if (empty) c.rust else c.amber, size = 20.dp)
        BasicText(text, style = Atlas.type.note.copy(color = c.ink))
    }
}
