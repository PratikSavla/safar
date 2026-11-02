package app.safar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.safar.ui.theme.Atlas

/**
 * A serif figure with a light italic unit after it (`.num` + `em`): "84.6 km", "≈312 km".
 * [unitScale] sizes the unit against the figure.
 */
@Composable
fun NumberWithUnit(
    value: String,
    unit: String?,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Atlas.colors.ink,
    unitScale: Float = 0.4f,
    unitColor: Color = Atlas.colors.ink2,
) {
    BasicText(
        buildAnnotatedString {
            append(value)
            if (unit != null) {
                withStyle(SpanStyle(fontSize = style.fontSize * unitScale, fontStyle = FontStyle.Italic, fontWeight = FontWeight(350), color = unitColor, letterSpacing = 0.em)) {
                    append(" $unit")
                }
            }
        },
        modifier,
        style = style.copy(color = color),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * One cell of a [StatGrid]: kicker, figure with unit, then either where it came from ([source], drawn as a
 * [SourceLine]) or a plain mono [note].
 */
data class StatCell(
    val label: String,
    val value: String,
    val unit: String? = null,
    val source: String? = null,
    val note: String? = null,
    val valueColor: Color? = null,
)

/**
 * Ledger grid (`.trip-grid`, `.mile-cols`, `.totals`): an ink rule on top, a hairline under each row and
 * dashed rules between columns.
 */
@Composable
fun StatGrid(cells: List<StatCell>, columns: Int, modifier: Modifier = Modifier, numberStyle: TextStyle = Atlas.type.numberSmall) {
    val c = Atlas.colors
    Column(modifier.fillMaxWidth()) {
        Spacer(Modifier.fillMaxWidth().height(1.5.dp).background(c.ink))
        cells.chunked(columns.coerceAtLeast(1)).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                row.forEachIndexed { i, cell ->
                    if (i > 0) DashedColumnRule()
                    StatCellView(cell, numberStyle, Modifier.weight(1f).padding(start = if (i > 0) 14.dp else 0.dp))
                }
                repeat(columns - row.size) { Box(Modifier.weight(1f)) }
            }
            Hairline(color = c.rule2)
        }
    }
}

@Composable
private fun StatCellView(cell: StatCell, numberStyle: TextStyle, modifier: Modifier) {
    val c = Atlas.colors
    Column(modifier.padding(top = 14.dp, bottom = 12.dp).semantics(mergeDescendants = true) {}) {
        Kicker(cell.label)
        NumberWithUnit(cell.value, cell.unit, numberStyle, Modifier.padding(top = 6.dp, bottom = 2.dp), color = cell.valueColor ?: c.ink)
        if (cell.source != null) SourceLine(cell.source)
        if (cell.note != null) BasicText(cell.note, style = Atlas.type.src.copy(color = c.ink3))
    }
}

@Composable
private fun DashedColumnRule() {
    val color = Atlas.colors.rule2
    Box(
        Modifier.width(1.dp).fillMaxHeight().drawBehind {
            drawLine(
                color, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), strokeWidth = size.width,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
            )
        },
    )
}

@Preview
@Composable
private fun StatGridPreview() = PreviewSurface {
    StatGrid(
        listOf(
            StatCell("Distance", "84.6", "km", source = "bike odometer"),
            StatCell("Duration", "1 h 52", source = "phone"),
            StatCell("Average", "45", "km/h", source = "calculated"),
            StatCell("Top speed", "98", "km/h", source = "phone GPS", valueColor = Atlas.colors.rust),
        ),
        columns = 2,
    )
}
