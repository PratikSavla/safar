package app.safar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.safar.ui.theme.Atlas
import app.safar.ui.theme.AtlasColors
import app.safar.ui.theme.AtlasShapes
import java.util.Locale

/** Ink of a [Stamp]. */
enum class StampTone { RUST, MOSS, SLATE, AMBER, INK }

fun StampTone.color(c: AtlasColors): Color = when (this) {
    StampTone.RUST -> c.rust
    StampTone.MOSS -> c.moss
    StampTone.SLATE -> c.slate
    StampTone.AMBER -> c.amber
    StampTone.INK -> c.ink2
}

/**
 * Rubber-stamp label (`.stamp`): mono caps, double rule, slightly rotated.
 * Examples: LINKED (moss), PROBABLE (slate), NO ACCOUNT (rust), LOW (amber).
 * [solid] fills it in the tone, for moments like "Ride mode · auto".
 */
@Composable
fun Stamp(text: String, modifier: Modifier = Modifier, tone: StampTone = StampTone.RUST, tilted: Boolean = true, solid: Boolean = false) {
    val c = Atlas.colors
    val ink = tone.color(c)
    val fg = if (solid) c.onRust else ink
    Box(
        modifier
            .padding(3.dp)
            .rotate(if (tilted) -2.5f else 0f)
            .doubleRule(ink)
            .then(if (solid) Modifier.background(ink, AtlasShapes.stamp) else Modifier)
            .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 3.dp),
    ) {
        BasicText(text.uppercase(Locale.ROOT), style = Atlas.type.stamp.copy(color = fg.copy(alpha = 0.92f)), maxLines = 1)
    }
}

/** 1.5 dp border plus a 1 dp outline 2 dp outside it (`border` + `outline-offset`). */
private fun Modifier.doubleRule(color: Color) = drawBehind {
    val inner = 1.5.dp.toPx()
    val outer = 1.dp.toPx()
    val gap = 2.dp.toPx()
    val r = 3.dp.toPx()
    drawRoundRect(color, Offset(inner / 2, inner / 2), Size(size.width - inner, size.height - inner), CornerRadius(r), style = Stroke(inner))
    val o = -(gap + outer / 2)
    drawRoundRect(color, Offset(o, o), Size(size.width - 2 * o, size.height - 2 * o), CornerRadius(r + gap), style = Stroke(outer))
}

@Preview
@Composable
private fun StampPreview() = PreviewSurface {
    Row {
        Stamp("Linked", tone = StampTone.MOSS)
        Stamp("Probable", tone = StampTone.SLATE)
        Stamp("No account")
        Stamp("Low", tone = StampTone.AMBER)
    }
}
