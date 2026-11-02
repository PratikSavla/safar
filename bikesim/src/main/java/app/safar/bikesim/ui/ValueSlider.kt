package app.safar.bikesim.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Integer slider from [min] to [max]; tap or drag anywhere on the track. Foundation only, no Material. */
@Composable
fun ValueSlider(value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    val p = LocalPalette.current
    val latest = rememberUpdatedState(onChange)
    fun valueAt(x: Float, width: Int): Int {
        if (width <= 0) return min
        val fraction = (x / width).coerceIn(0f, 1f)
        return (min + fraction * (max - min)).roundToInt()
    }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(min, max) {
                detectTapGestures { latest.value(valueAt(it.x, size.width)) }
            }
            .pointerInput(min, max) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    latest.value(valueAt(change.position.x, size.width))
                }
            },
    ) {
        val y = size.height / 2
        val fraction = if (max > min) (value - min).toFloat() / (max - min) else 0f
        val x = fraction.coerceIn(0f, 1f) * size.width
        drawLine(p.line, Offset(0f, y), Offset(size.width, y), strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
        drawLine(p.accent, Offset(0f, y), Offset(x, y), strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(p.accent, radius = 11.dp.toPx(), center = Offset(x, y))
    }
}
