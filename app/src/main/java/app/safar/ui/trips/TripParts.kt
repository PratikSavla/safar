package app.safar.ui.trips

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.safar.ui.art.AtlasIcons
import app.safar.ui.components.AtlasCard
import app.safar.ui.components.AtlasIcon
import app.safar.ui.components.DashedRule
import app.safar.ui.components.FuelBars
import app.safar.ui.components.Kicker
import app.safar.ui.components.Note
import app.safar.ui.components.NumberWithUnit
import app.safar.ui.components.SectionHeader
import app.safar.ui.components.SourceLine
import app.safar.ui.components.StatCell
import app.safar.ui.components.StatGrid
import app.safar.ui.format.Formatters
import app.safar.ui.theme.Atlas
import kotlin.math.roundToInt

private val MAP_HEIGHT = 300.dp

/** The big ink track with north mark, fix count and a scale bar (`.sheet-map`). */
@Composable
internal fun TrackFigure(trip: TripDetailUi) {
    val c = Atlas.colors
    AtlasCard(Modifier.padding(bottom = 18.dp), contentPadding = PaddingValues(bottom = 10.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { MAP_HEIGHT.toPx() }
            val padPx = with(density) { trackPad(big = true).toPx() }
            val scale = projectTrack(trip.trackLat, trip.trackLng, widthPx, heightPx, padPx).pxPerMetre
            Column {
                Box {
                    TrackMap(
                        trip.trackLat, trip.trackLng, Modifier.fillMaxWidth().height(MAP_HEIGHT), big = true,
                        description = "GPS track of ${trip.title}, ${trip.fixes} fixes",
                    )
                    BasicText("N ↑", Modifier.align(Alignment.TopEnd).padding(10.dp, 12.dp), style = Atlas.type.src.copy(color = c.rust))
                }
                MapCaption(trip.fixes, scaleBar(scale, widthPx * 0.3f))
            }
        }
    }
}

@Composable
private fun MapCaption(fixes: Int, bar: ScaleBar?) {
    val c = Atlas.colors
    val style = Atlas.type.src.copy(color = c.ink3, fontSize = Atlas.type.src.fontSize * 0.9f)
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(if (fixes == 0) "no GPS fixes for this ride" else "phone GPS · $fixes fixes", Modifier.weight(1f), style = style)
        if (bar != null) {
            val w = with(LocalDensity.current) { bar.px.toDp() }
            Canvas(Modifier.size(w, 6.dp)) {
                val s = 1.5.dp.toPx()
                drawLine(c.ink2, Offset(0f, 0f), Offset(0f, size.height), s)
                drawLine(c.ink2, Offset(0f, size.height - s / 2), Offset(size.width, size.height - s / 2), s)
                drawLine(c.ink2, Offset(size.width, 0f), Offset(size.width, size.height), s)
            }
            BasicText(bar.label, Modifier.padding(start = 6.dp), style = style)
        }
    }
}

/** Distance, duration, average and top speed, each with its source (`.trip-grid`). */
@Composable
internal fun TripStats(trip: TripDetailUi) {
    StatGrid(
        listOf(
            StatCell("Distance", Formatters.km1(trip.km), "km", source = trip.distanceSource),
            StatCell("Duration", durationText(trip.minutes), source = if (trip.recording) "phone · live" else "phone"),
            StatCell("Average", trip.avgKmh.roundToInt().toString(), "km/h", source = "calculated"),
            StatCell("Top speed", trip.topKmh.roundToInt().toString(), "km/h", source = "phone GPS", valueColor = Atlas.colors.rust),
        ),
        columns = 2,
        numberStyle = Atlas.type.numberSmall.copy(fontSize = Atlas.type.numberSmall.fontSize * 1.3f),
    )
}

/** Odometer at connect, a dashed road, odometer at disconnect (`.odo-run`). */
@Composable
internal fun OdometerRun(trip: TripDetailUi) {
    SectionHeader("Odometer")
    if (trip.odoStart == null || trip.odoEnd == null) {
        Note("No odometer for this ride: the bike was not connected at the start or the end.")
        return
    }
    val style = Atlas.type.numberSmall.copy(fontSize = Atlas.type.numberSmall.fontSize * 0.94f)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NumberWithUnit(Formatters.odometer(trip.odoStart), null, style)
        DashedRule(Modifier.weight(1f), color = Atlas.colors.ink2, thickness = 2.dp, dash = 6.dp, gap = 5.dp)
        NumberWithUnit(Formatters.odometer(trip.odoEnd), "km", style)
    }
    SourceLine("bike · at connect and disconnect", Modifier.padding(top = 6.dp))
}

/** Fuel bars at the start and the end of the ride (`.fuel-run`). */
@Composable
internal fun FuelRun(trip: TripDetailUi) {
    val fuel = trip.fuel
    SectionHeader("Fuel") { if (fuel != null) Kicker(fuel.aside) }
    if (fuel == null) {
        Note("No fuel reading for this ride.")
        return
    }
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FuelBars(fuel.start, fuel.max, barWidth = 11.dp)
        AtlasIcon(AtlasIcons.arrow, Modifier.padding(bottom = 6.dp), tint = Atlas.colors.ink3, size = 20.dp)
        FuelBars(fuel.end, fuel.max, barWidth = 11.dp)
    }
    BasicText(fuel.note, Modifier.padding(top = 10.dp), style = Atlas.type.src.copy(color = Atlas.colors.ink3))
    SourceLine("bike · fuel bars at connect and disconnect")
}
