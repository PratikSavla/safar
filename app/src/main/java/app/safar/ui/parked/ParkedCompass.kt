package app.safar.ui.parked

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.safar.ui.theme.Atlas
import app.safar.ui.theme.AtlasColors
import kotlin.math.cos
import kotlin.math.sin

private const val TICKS = 72

/**
 * Compass rose with a needle to the bike (art.js `compass`). [roseDeg] turns the rose with the phone,
 * [pointerDeg] is the needle angle on screen; null hides the needle (no phone fix yet).
 */
@Composable
fun ParkedCompass(pointerDeg: Float?, roseDeg: Float, description: String, modifier: Modifier = Modifier, size: Dp = 280.dp) {
    val c = Atlas.colors
    val measurer = rememberTextMeasurer()
    val label = Atlas.type.numberSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = Atlas.type.numberSmall.fontSize * 0.78f)
    val needleAngle = rememberSmoothAngle(pointerDeg ?: 0f)
    val roseAngle = rememberSmoothAngle(roseDeg)
    Canvas(modifier.size(size).graphicsLayer().semantics { contentDescription = description }) {
        val rose = roseAngle.value
        val needle = needleAngle.value
        val centre = Offset(this.size.width / 2, this.size.height / 2)
        val r = this.size.width / 2 - 14.dp.toPx()
        drawCircle(c.card, r, centre)
        drawCircle(c.ink, r, centre, style = Stroke(1.5.dp.toPx()))
        val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))
        drawCircle(c.rule2, r - 44.dp.toPx(), centre, style = Stroke(1.dp.toPx(), pathEffect = dash))
        rotate(rose, centre) { drawTicks(centre, r, c) }
        drawCardinals(measurer, centre, r - 28.dp.toPx(), rose, label, c)
        if (pointerDeg != null) rotate(needle, centre) { drawNeedle(centre, r, c) }
        drawCircle(c.ink, 5.dp.toPx(), centre)
    }
}

/** An angle that glides to each new value the short way round. Read `.value` while drawing only. */
@Composable
private fun rememberSmoothAngle(target: Float): State<Float> {
    val anim = remember { Animatable(target) }
    LaunchedEffect(target) { anim.animateTo(shortestRotation(anim.value, target), tween(600)) }
    return anim.asState()
}

private fun DrawScope.drawTicks(centre: Offset, r: Float, c: AtlasColors) {
    for (i in 0 until TICKS) {
        val a = Math.toRadians(i * 5.0)
        val long = i % 6 == 0
        val r1 = r - (if (long) 12 else 6).dp.toPx()
        val from = Offset(centre.x + (sin(a) * r).toFloat(), centre.y - (cos(a) * r).toFloat())
        val to = Offset(centre.x + (sin(a) * r1).toFloat(), centre.y - (cos(a) * r1).toFloat())
        drawLine(if (long) c.ink else c.ink3, from, to, (if (long) 1.6.dp else 1.dp).toPx())
    }
}

private fun DrawScope.drawCardinals(measurer: TextMeasurer, centre: Offset, r: Float, rose: Float, style: TextStyle, c: AtlasColors) {
    listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f).forEach { (t, d) ->
        val a = Math.toRadians((d + rose).toDouble())
        val layout = measurer.measure(t, style.copy(color = if (t == "N") c.rust else c.ink2))
        val x = centre.x + (sin(a) * r).toFloat() - layout.size.width / 2f
        val y = centre.y - (cos(a) * r).toFloat() - layout.size.height / 2f
        drawText(layout, topLeft = Offset(x, y))
    }
}

private fun DrawScope.drawNeedle(centre: Offset, r: Float, c: AtlasColors) {
    val head = Path().apply {
        moveTo(centre.x, centre.y - r + 40.dp.toPx())
        lineTo(centre.x + 13.dp.toPx(), centre.y)
        lineTo(centre.x, centre.y + 18.dp.toPx())
        lineTo(centre.x - 13.dp.toPx(), centre.y)
        close()
    }
    drawPath(head, c.rust)
    val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx()))
    drawLine(c.ink3, Offset(centre.x, centre.y + 18.dp.toPx()), Offset(centre.x, centre.y + r - 50.dp.toPx()), 2.dp.toPx(), pathEffect = dash)
}
