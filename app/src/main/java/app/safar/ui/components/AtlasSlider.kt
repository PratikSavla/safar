package app.safar.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.safar.ui.theme.Atlas
import kotlin.math.roundToInt

/** Value for a touch at [x] along a track [width] px wide, clamped to [range]. */
internal fun sliderValueAt(x: Float, width: Float, range: IntRange): Int {
    if (width <= 0f) return range.first
    val f = (x / width).coerceIn(0f, 1f)
    return (range.first + f * (range.last - range.first)).roundToInt()
}

/**
 * Speed-alert style slider (`.slider`): big serif value with its unit, a dashed ink track,
 * a rust thumb ringed in paper, and min / max ticks with the unit. Whole numbers only.
 */
@Composable
fun AtlasSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    label: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    enabled: Boolean = true,
) {
    val c = Atlas.colors
    val current = value.coerceIn(range)
    val change by rememberUpdatedState(onValueChange)
    Column(modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f).padding(top = 4.dp, bottom = 14.dp)) {
        BasicText(
            buildAnnotatedString {
                append(current.toString())
                if (unit != null) withStyle(SpanStyle(fontSize = Atlas.type.displayLarge.fontSize * 0.4f, fontStyle = FontStyle.Italic, fontWeight = FontWeight(350), color = c.ink2)) { append(" $unit") }
            },
            style = Atlas.type.displayLarge.copy(color = c.ink),
        )
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(Atlas.dimens.touchTarget)
                .sliderSemantics(label, current, unit, range, enabled) { change(it) }
                .then(if (enabled) Modifier.trackGestures(range) { change(it) } else Modifier),
        ) {
            val thumbR = 13.dp.toPx()
            val inset = thumbR
            val usable = size.width - inset * 2
            val y = size.height / 2
            drawLine(
                c.ink, Offset(inset, y), Offset(size.width - inset, y), strokeWidth = 3.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
            )
            val span = (range.last - range.first).coerceAtLeast(1)
            val x = inset + usable * (current - range.first) / span
            drawCircle(c.rust, thumbR + 1.5.dp.toPx(), Offset(x, y))
            drawCircle(c.paper, thumbR, Offset(x, y))
            drawCircle(c.rust, thumbR - 4.dp.toPx(), Offset(x, y))
            drawCircle(c.rust, thumbR + 1.5.dp.toPx(), Offset(x, y), style = Stroke(1.5.dp.toPx()))
        }
        SliderTicks(range, unit)
    }
}

@Composable
private fun SliderTicks(range: IntRange, unit: String?) {
    // The ends only: the arithmetic middle ("154") reads as an odd number to aim for.
    Row(Modifier.fillMaxWidth()) {
        val tick = Atlas.type.src.copy(color = Atlas.colors.ink3)
        val suffix = unit?.let { " $it" }.orEmpty()
        BasicText("${range.first}$suffix", Modifier.weight(1f), style = tick)
        BasicText("${range.last}$suffix", Modifier.weight(1f), style = tick.copy(textAlign = TextAlign.End))
    }
}

/** TalkBack: label, spoken value, range and volume-key style adjustment. */
private fun Modifier.sliderSemantics(label: String, current: Int, unit: String?, range: IntRange, enabled: Boolean, onValue: (Int) -> Unit) =
    clearAndSetSemantics {
        contentDescription = label
        stateDescription = "$current ${unit.orEmpty()}".trim()
        val steps = (range.last - range.first - 1).coerceAtLeast(0)
        progressBarRangeInfo = ProgressBarRangeInfo(current.toFloat(), range.first.toFloat()..range.last.toFloat(), steps)
        if (enabled) {
            setProgress { target -> onValue(target.roundToInt().coerceIn(range)); true }
        } else {
            disabled()
        }
    }

private fun Modifier.trackGestures(range: IntRange, onValue: (Int) -> Unit): Modifier = this
    .pointerInput(range) {
        val inset = 13.dp.toPx()
        detectTapGestures { pos -> onValue(sliderValueAt(pos.x - inset, size.width - inset * 2, range)) }
    }
    .pointerInput(range) {
        val inset = 13.dp.toPx()
        detectHorizontalDragGestures { change, _ ->
            change.consume()
            onValue(sliderValueAt(change.position.x - inset, size.width - inset * 2, range))
        }
    }

@Preview
@Composable
private fun SliderPreview() = PreviewSurface {
    AtlasSlider(100, {}, 10..299, label = "Speed alert", unit = "km/h")
    AtlasSlider(60, {}, 5..120, label = "Speed alert", unit = "km/h", enabled = false)
}
