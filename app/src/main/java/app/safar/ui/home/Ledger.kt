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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.safar.ui.art.AtlasIcons
import app.safar.ui.components.AtlasIcon
import app.safar.ui.components.EmptyState
import app.safar.ui.components.FuelBars
import app.safar.ui.components.Hairline
import app.safar.ui.components.Kicker
import app.safar.ui.components.LedgerSize
import app.safar.ui.components.LedgerValue
import app.safar.ui.components.SourceLine
import app.safar.ui.components.Stamp
import app.safar.ui.components.StampTone
import app.safar.ui.components.liveAge
import app.safar.ui.format.Formatters
import app.safar.ui.theme.Atlas
import app.safar.ui.theme.AtlasShapes

/**
 * The ledger (home.js `dashboard`): odometer across, then a grid of bike, phone and calculated values.
 * Ages are read cell by cell and [speed] only in its own cell, so a clock tick or a GPS fix recomposes the
 * texts that change, never the whole grid.
 */
@Composable
fun Ledger(ledger: LedgerUi?, speed: () -> SpeedUi) {
    if (ledger == null) {
        EmptyState("A blank page, for now.", message = "Odometer, trips and fuel appear here a few seconds after the bike connects.")
        return
    }
    Column(Modifier.fillMaxWidth()) {
        OdometerRow(ledger)
        val columns = if (LocalDensity.current.fontScale > 1.3f) 2 else 3
        cells(ledger, speed).chunked(columns).forEach { row ->
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
private fun OdometerRow(l: LedgerUi) {
    val c = Atlas.colors
    val style = Atlas.type.numberLarge
    val age = liveAge(l.readingAt)
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Kicker("Odometer")
            SourceLine(l.bikeSource, Modifier.padding(top = 4.dp), age = age)
        }
        BasicText(
            buildAnnotatedString {
                append(Formatters.odometer(l.odometerKm))
                withStyle(SpanStyle(fontSize = style.fontSize * 0.36f, fontStyle = FontStyle.Italic, fontWeight = FontWeight(340), color = c.ink2)) { append(" km") }
            },
            Modifier.clearAndSetSemantics { contentDescription = "Odometer ${l.odometerKm} km, from ${l.bikeSource}, $age" },
            style = style.copy(color = if (l.live) c.ink else c.ink2),
            maxLines = 1,
        )
    }
    Hairline(thickness = 1.5.dp, color = c.ink)
}

private fun cells(l: LedgerUi, speed: () -> SpeedUi): List<@Composable () -> Unit> = listOf(
    { TripCell("Trip A", l.tripAKm, l) },
    { TripCell("Trip B", l.tripBKm, l) },
    { SpeedCell(speed) },
    { FuelCell(l) },
    { LedgerValue(l.rangeText, l.rangeSource.replace("calculated", "calc"), unit = "km", label = "Range", size = LedgerSize.SMALL) },
    { GearCell(l) },
)

@Composable
private fun TripCell(label: String, km: Double, l: LedgerUi) {
    LedgerValue(Formatters.km1(km), l.bikeSource, unit = "km", label = label, age = liveAge(l.readingAt), size = LedgerSize.SMALL, stale = !l.live)
}

/** Phone GPS speed: the only cell that reads the 1 Hz fix. */
@Composable
private fun SpeedCell(speed: () -> SpeedUi) {
    val s = speed()
    val speedAge = when {
        s.live -> "live"
        s.fixAt != null -> liveAge(s.fixAt)
        else -> "no fix"
    }
    LedgerValue(s.kmh?.toString() ?: "—", "phone GPS", unit = "km/h", label = "Speed", age = speedAge, size = LedgerSize.SMALL)
}

@Composable
private fun FuelCell(l: LedgerUi) {
    val c = Atlas.colors
    val age = liveAge(l.readingAt)
    val low = (l.fuelBars ?: Int.MAX_VALUE) <= 1
    val spoken = if (l.fuelBars == null) "Fuel waiting for the bike" else "Fuel ${l.fuelBars} of ${l.fuelMax} bars, from ${l.bikeSource}, $age"
    Column(Modifier.clearAndSetSemantics { contentDescription = spoken }) {
        Kicker("Fuel")
        Row(Modifier.padding(top = 4.dp, bottom = 2.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (l.fuelBars != null) FuelBars(l.fuelBars, l.fuelMax, height = 26.dp, barWidth = 8.dp, gap = 3.dp)
            BasicText(
                buildAnnotatedString {
                    append(l.fuelBars?.toString() ?: "—")
                    withStyle(SpanStyle(fontSize = Atlas.type.numberSmall.fontSize * 0.5f, fontStyle = FontStyle.Italic, color = c.ink2)) { append("/${l.fuelMax}") }
                },
                style = Atlas.type.numberSmall.copy(color = if (low) c.amber else if (l.live) c.ink else c.ink2),
                maxLines = 1,
            )
        }
        SourceLine(l.bikeSource, age = age)
    }
}

@Composable
private fun GearCell(l: LedgerUi) {
    val age = liveAge(l.readingAt)
    Column(Modifier.clearAndSetSemantics { contentDescription = "Gear ${l.gear}, probable, from ${l.bikeSource}, $age" }) {
        Kicker("Gear")
        Row(Modifier.padding(top = 4.dp, bottom = 2.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BasicText(l.gear, style = Atlas.type.numberSmall.copy(color = if (l.live) Atlas.colors.ink else Atlas.colors.ink2), maxLines = 1)
            Stamp("probable", tone = StampTone.INK)
        }
        SourceLine(l.bikeSource, age = age)
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
