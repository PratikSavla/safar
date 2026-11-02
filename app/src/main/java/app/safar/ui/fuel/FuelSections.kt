package app.safar.ui.fuel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.safar.data.FuelStats
import app.safar.ui.components.AtlasCard
import app.safar.ui.components.CardStyle
import app.safar.ui.components.DashedRule
import app.safar.ui.components.LeaderRow
import app.safar.ui.components.NumberWithUnit
import app.safar.ui.components.Stamp
import app.safar.ui.components.StampTone
import app.safar.ui.components.StatCell
import app.safar.ui.components.StatGrid
import app.safar.ui.format.Formatters
import app.safar.ui.theme.Atlas
import app.safar.ui.theme.AtlasColors
import java.time.ZoneId

/** Average, last tank and lifetime km/L (`.mile-cols`) and the trend chart. */
@Composable
internal fun MileageSection(stats: FuelStats, zone: ZoneId) {
    val unit = "km/L"
    StatGrid(
        listOf(
            StatCell("Average", stats.avgKmpl?.let(::jsNumber) ?: "—", unit, source = "last 3 full tanks", valueColor = Atlas.colors.rust),
            StatCell("Last tank", stats.lastKmpl?.let(::jsNumber) ?: "—", unit, source = "most recent"),
            StatCell("Lifetime", stats.lifetimeKmpl?.let(::jsNumber) ?: "—", unit, source = "${stats.segments.size} tanks"),
        ),
        columns = 3,
        numberStyle = Atlas.type.numberSmall.copy(fontSize = Atlas.type.numberSmall.fontSize * 1.1f),
    )
    if (stats.segments.size >= 2) {
        AtlasCard(Modifier.padding(top = 12.dp), contentPadding = PaddingValues(8.dp)) { TrendCanvas(stats, zone) }
    }
}

@Composable
private fun TrendCanvas(stats: FuelStats, zone: ZoneId) {
    val c = Atlas.colors
    val measurer = rememberTextMeasurer()
    val value = Atlas.type.monoValue.copy(color = c.ink, fontFamily = Atlas.type.numberSmall.fontFamily, fontSize = Atlas.type.src.fontSize)
    val axis = Atlas.type.src.copy(color = c.ink3, fontSize = Atlas.type.src.fontSize * 0.78f)
    val avgStyle = axis.copy(color = c.rust)
    val dates = stats.segments.map { fuelDate(it.atMillis, zone) }
    val spoken = "Mileage per full tank: " + stats.segments.joinToString { jsNumber(it.kmpl) }
    Canvas(Modifier.fillMaxWidth().height(118.dp).semantics { contentDescription = spoken }) {
        val chart = trendChart(stats.segments, stats.avgKmpl, size.width, size.height, 14.dp.toPx(), 22.dp.toPx(), 22.dp.toPx())
            ?: return@Canvas
        chart.avgY?.let { y ->
            val dash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx()))
            drawLine(c.rust, Offset(14.dp.toPx(), y), Offset(size.width - 14.dp.toPx(), y), 1.dp.toPx(), pathEffect = dash)
            drawText(measurer, "avg ${stats.avgKmpl?.let(::jsNumber)}", Offset(14.dp.toPx(), y + 3.dp.toPx()), avgStyle)
        }
        drawTrendLine(chart, c)
        chart.xs.indices.forEach { i ->
            centred(measurer, jsNumber(stats.segments[i].kmpl), chart.xs[i], chart.ys[i] - 9.dp.toPx(), value)
            centred(measurer, dates[i], chart.xs[i], size.height - 2.dp.toPx(), axis)
        }
    }
}

private fun DrawScope.drawTrendLine(chart: TrendChart, c: AtlasColors) {
    val path = Path()
    chart.xs.indices.forEach { i -> if (i == 0) path.moveTo(chart.xs[i], chart.ys[i]) else path.lineTo(chart.xs[i], chart.ys[i]) }
    drawPath(path, c.ink, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
    chart.xs.indices.forEach { i ->
        val p = Offset(chart.xs[i], chart.ys[i])
        drawCircle(c.card, 4.dp.toPx(), p)
        drawCircle(c.ink, 4.dp.toPx(), p, style = Stroke(2.dp.toPx()))
    }
}

/** Text centred on [x] with its bottom at [bottom], kept inside the canvas. */
private fun DrawScope.centred(measurer: TextMeasurer, text: String, x: Float, bottom: Float, style: TextStyle) {
    val layout = measurer.measure(text, style)
    val left = (x - layout.size.width / 2f).coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
    drawText(layout, topLeft = Offset(left, bottom - layout.size.height))
}

/** Cost per km, last 30 days, last price and all logged (`.costcard`). */
@Composable
internal fun CostCard(stats: FuelStats, currency: String) {
    AtlasCard(style = CardStyle.LINE, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
        LeaderRow("Cost per km", stats.costPerKm?.let { Formatters.money(it, currency, 2) } ?: "—")
        LeaderRow("Last 30 days", "${stats.month.count} fill-ups · ${jsNumber(stats.month.litres)} L · ${Formatters.money(stats.month.money.toDouble(), currency)}")
        LeaderRow("Last price", stats.lastPerLitre?.let { "${Formatters.money(it, currency, 2)} / L" } ?: "—")
        LeaderRow("All logged", "${jsNumber(stats.spentL)} L · ${Formatters.money(stats.spentMoney.toDouble(), currency)}")
    }
}

private fun OdoBadge.tone(): StampTone = when (this) {
    OdoBadge.BIKE -> StampTone.MOSS
    OdoBadge.EDITED -> StampTone.AMBER
    OdoBadge.TYPED -> StampTone.SLATE
}

/** One receipt (`.fu-row`); tap to edit. */
@Composable
internal fun FuelUpRow(row: FuelUpRowUi, currency: String, onClick: () -> Unit) {
    val c = Atlas.colors
    val mono = Atlas.type.src.copy(color = c.ink3)
    Column(Modifier.fillMaxWidth().clickable(onClickLabel = "Edit fuel-up", role = Role.Button, onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BasicText(row.date, Modifier.width(52.dp), style = Atlas.type.src.copy(color = c.ink2))
            Column(Modifier.weight(1f)) {
                NumberWithUnit(jsNumber(row.litres), "L", Atlas.type.numberSmall.copy(fontSize = Atlas.type.numberSmall.fontSize * 0.9f), unitScale = 0.55f, unitColor = c.ink3)
                BasicText(
                    "${Formatters.money(row.total, currency)} · ${Formatters.money(row.perLitre, currency, 2)}/L",
                    style = mono, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText("${Formatters.odometer(row.odometerKm)} km", style = Atlas.type.src.copy(color = c.ink))
                    Stamp(row.badge.label, tone = row.badge.tone(), tilted = false)
                }
                Stamp(if (row.full) "Full" else "Top-up", tone = StampTone.INK, tilted = false)
            }
        }
        DashedRule(thickness = 1.dp, dash = 4.dp, gap = 3.dp)
    }
}
