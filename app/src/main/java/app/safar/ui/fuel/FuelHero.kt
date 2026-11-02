package app.safar.ui.fuel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.safar.data.FuelMethod
import app.safar.data.FuelStats
import app.safar.ui.art.AtlasIcons
import app.safar.ui.components.AtlasIcon
import app.safar.ui.components.DevOnly
import app.safar.ui.components.DeveloperLine
import app.safar.ui.components.Freshness
import app.safar.ui.components.FuelBars
import app.safar.ui.components.Kicker
import app.safar.ui.components.NumberWithUnit
import app.safar.ui.components.rememberStale
import app.safar.ui.format.Formatters
import app.safar.ui.theme.Atlas

private const val LOW_FRACTION = 0.2f

/** Range left, how it was worked out, the tank bar and the bike's own bars (`.range-hero`). Sources: Developer mode. */
@Composable
internal fun RangeHero(stats: FuelStats, reading: BarsReading) {
    val c = Atlas.colors
    Column(Modifier.fillMaxWidth().drawBehind { drawLine(c.ink, Offset(0f, size.height), Offset(size.width, size.height), 1.5.dp.toPx()) }.padding(bottom = 16.dp)) {
        Kicker("Range left")
        val big = Atlas.type.numberLarge.copy(fontSize = Atlas.type.numberLarge.fontSize * 1.53f)
        NumberWithUnit(Formatters.approx(stats.rangeKm?.toDouble()), "km", big, Modifier.padding(top = 6.dp), unitScale = 0.32f)
        DevOnly { DeveloperLine("↳ ${rangeSource(stats.method)}") }
        BasicText(methodLine(stats), Modifier.padding(top = 6.dp, bottom = 14.dp), style = Atlas.type.serifItalic.copy(color = c.ink2))
        TankBar(tankFraction(stats), stats.barsMax, tankDescription(stats))
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val mono = Atlas.type.src.copy(color = c.ink2)
            val left = if (stats.remainingL == null) "—" else "≈${jsNumber(stats.remainingL)} L"
            // The amount never wraps (a lone "L" on its own line); the note beside it takes the rest and wraps.
            BasicText("$left of ${jsNumber(stats.tank)} L", style = mono, softWrap = false)
            if (stats.method == FuelMethod.HISTORY && stats.sinceFullKm != null) {
                BasicText("${Formatters.odometer(stats.sinceFullKm)} km since the last full tank", Modifier.weight(1f), style = mono.copy(textAlign = TextAlign.End))
            }
        }
        BikeBars(reading)
        if (stats.gaugeDisagrees) GaugeHint(stats)
    }
}

/** Horizontal tank: ink fill (amber when low) with dashed ticks at each bar. */
@Composable
private fun TankBar(fraction: Float, bars: Int, description: String) {
    val c = Atlas.colors
    Canvas(Modifier.fillMaxWidth().height(16.dp).semantics { contentDescription = description }) {
        val line = 1.5.dp.toPx()
        val r = CornerRadius(2.dp.toPx())
        drawRoundRect(if (fraction < LOW_FRACTION) c.amber else c.ink, size = Size(size.width * fraction, size.height), cornerRadius = r)
        val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))
        for (i in 1 until bars) {
            val x = size.width * i / bars
            drawLine(c.paper.copy(alpha = 0.8f), Offset(x, 0f), Offset(x, size.height), line, pathEffect = dash)
        }
        drawRoundRect(c.ink, Offset(line / 2, line / 2), Size(size.width - line, size.height - line), r, style = Stroke(line))
    }
}

@Composable
private fun BikeBars(reading: BarsReading) {
    val c = Atlas.colors
    val origin = reading.origin
    val stale = rememberStale(origin)
    Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (reading.bars != null) FuelBars(reading.bars, reading.max, height = 30.dp, barWidth = 9.dp)
        val style = Atlas.type.numberSmall.copy(fontSize = Atlas.type.numberSmall.fontSize * 0.875f)
        NumberWithUnit(reading.bars?.toString() ?: "—", "/${reading.max} bars", style, color = if (stale) c.ink2 else c.ink, unitScale = 0.55f, unitColor = c.ink3)
    }
    Freshness(origin)
}

/** Calm note when the history and the gauge disagree (`.calm-hint`). */
@Composable
private fun GaugeHint(stats: FuelStats) {
    val c = Atlas.colors
    Row(
        Modifier
            .padding(top = 14.dp)
            .fillMaxWidth()
            .background(c.slate.copy(alpha = 0.1f))
            .drawBehind { drawRect(c.slate, size = Size(3.dp.toPx(), size.height)) }
            .padding(start = 15.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AtlasIcon(AtlasIcons.info, Modifier.width(18.dp), tint = c.slate, size = 18.dp)
        BasicText(gaugeHint(stats), style = Atlas.type.note.copy(color = c.ink2))
    }
}
