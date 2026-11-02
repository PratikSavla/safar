package app.safar.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.safar.protocol.Timing
import app.safar.ui.components.DashedRule
import app.safar.ui.components.rememberNow
import app.safar.ui.theme.Atlas
import app.safar.ui.theme.AtlasShapes
import kotlin.math.ceil

/** One step of the reconnect schedule: "2 s", "5 s", ... with whether it is done or current. */
data class BackoffStep(val label: String, val past: Boolean, val now: Boolean)

/** The schedule 2 s → 5 s → 10 s → 30 s for upcoming [attempt] (1-based), from [Timing]. */
internal fun backoffSteps(attempt: Int): List<BackoffStep> {
    val steps = Timing.RECONNECT_BACKOFF_MS
    val current = (attempt - 1).coerceIn(0, steps.lastIndex)
    return steps.mapIndexed { i, ms -> BackoffStep("${ms / 1000} s", past = i < current, now = i == current) }
}

/** Whole seconds until [nextAtMillis], never negative. */
internal fun secondsLeft(nextAtMillis: Long, nowMillis: Long): Int = ceil((nextAtMillis - nowMillis).coerceAtLeast(0) / 1000.0).toInt()

/** Fraction of the wait still ahead, for the countdown ring. */
internal fun waitFraction(attempt: Int, nextAtMillis: Long, nowMillis: Long): Float {
    val total = Timing.reconnectDelayMs(attempt.coerceAtLeast(1)).coerceAtLeast(1L)
    return ((nextAtMillis - nowMillis).toFloat() / total).coerceIn(0f, 1f)
}

/** Reconnect back-off (home.css `.backoff`): amber countdown ring, the reason, and the schedule. */
@Composable
internal fun Backoff(attempt: Int, nextAtMillis: Long) {
    val c = Atlas.colors
    val now by rememberNow()
    val left = secondsLeft(nextAtMillis, now)
    Column(Modifier.fillMaxWidth()) {
        DashedRule(Modifier.padding(bottom = 12.dp), thickness = 1.dp, dash = 4.dp, gap = 3.dp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CountdownRing(left, waitFraction(attempt, nextAtMillis, now))
            Column(Modifier.weight(1f)) {
                BasicText(
                    buildAnnotatedString {
                        append("Bike went quiet (ignition off or out of range). Next try in ")
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("$left s") }
                        append(", attempt ")
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("$attempt") }
                        append(".")
                    },
                    style = Atlas.type.note.copy(color = c.ink2),
                )
                Schedule(attempt)
            }
        }
    }
}

@Composable
private fun CountdownRing(seconds: Int, fraction: Float) {
    val c = Atlas.colors
    val sweep by animateFloatAsState(fraction, tween(1000), label = "countdown")
    Box(Modifier.size(64.dp).clearAndSetSemantics { contentDescription = "Next try in $seconds seconds" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 2.5.dp.toPx()
            drawArc(c.amber, -90f, 360f * sweep, false, topLeft = Offset(w, w), size = Size(size.width - 2 * w, size.height - 2 * w), style = Stroke(w))
        }
        BasicText("$seconds", style = Atlas.type.numberSmall.copy(color = c.amber, fontWeight = FontWeight(600)))
    }
}

@Composable
private fun Schedule(attempt: Int) {
    val c = Atlas.colors
    val style = Atlas.type.src
    FlowRow(
        Modifier.padding(top = 6.dp).semantics(mergeDescendants = true) { },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        backoffSteps(attempt).forEach { s ->
            val tone = when {
                s.now -> Modifier.background(c.amber, AtlasShapes.tiny).padding(horizontal = 4.dp)
                else -> Modifier
            }
            BasicText(
                s.label, tone,
                style = style.copy(
                    color = if (s.now) c.paper else c.ink3,
                    textDecoration = if (s.past) TextDecoration.LineThrough else null,
                ),
            )
            BasicText("→", style = style.copy(color = c.ink3.copy(alpha = 0.6f)))
        }
        BasicText("every 30 s", style = style.copy(color = c.ink3))
    }
}
