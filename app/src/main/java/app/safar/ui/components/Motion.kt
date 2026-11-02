package app.safar.ui.components

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State

/**
 * A 0 → 1 phase that loops every [periodMillis] while it is composed. Read `.value` only inside a draw or
 * `graphicsLayer { }` lambda of a node that has its own layer (`Modifier.graphicsLayer()`): each frame then
 * redraws that small layer, never recomposes, and never re-records the scrolling page around it.
 * Compose's frame clock follows the system animator duration scale; with animations off the phase rests at 1.
 * Only call this while the motion is wanted: a composable that is idle should not create the loop at all.
 */
@Composable
fun rememberLoopPhase(periodMillis: Int, label: String, easing: Easing = LinearEasing, delayMillis: Int = 0): State<Float> =
    rememberInfiniteTransition(label).animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMillis, easing = easing), RepeatMode.Restart, StartOffset(delayMillis)),
        label = label,
    )

/** A blink: shown for the first half of each period, and shown for good when animations are off (phase 1). */
fun blinkShown(phase: Float): Boolean = phase < 0.5f || phase >= 1f
