package app.safar.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.safar.ui.theme.Atlas

/** Signal strength 1..4 from RSSI in dBm (ui.js `rssiBars`). */
fun rssiBars(rssi: Int): Int = when {
    rssi > -55 -> 4
    rssi > -70 -> 3
    rssi > -82 -> 2
    else -> 1
}

/** Four rising bars, [bars] of them inked (ui.js `sigBars`). TalkBack reads "Signal 3 of 4". */
@Composable
fun SignalBars(bars: Int, modifier: Modifier = Modifier) {
    val c = Atlas.colors
    val on = bars.coerceIn(0, 4)
    Canvas(modifier.size(22.dp, 16.dp).clearAndSetSemantics { contentDescription = "Signal $on of 4" }) {
        val w = 4.dp.toPx()
        val gap = 2.dp.toPx()
        for (i in 1..4) {
            val h = (4 + i * 3).dp.toPx()
            drawRoundRect(
                if (i <= on) c.ink else c.rule2,
                Offset((i - 1) * (w + gap), size.height - h),
                Size(w, h),
                CornerRadius(1.dp.toPx()),
            )
        }
    }
}

@Preview
@Composable
private fun SignalBarsPreview() = PreviewSurface {
    Row {
        SignalBars(rssiBars(-50))
        SignalBars(rssiBars(-75))
        SignalBars(rssiBars(-90))
    }
}
