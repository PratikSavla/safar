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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onVisibilityChanged

/**
 * A 0 → 1 phase that loops every [periodMillis] while [running]. Read `.value` only inside a draw or
 * `graphicsLayer { }` lambda of a node that has its own layer (`Modifier.graphicsLayer()`): each frame then
 * redraws that small layer, never recomposes, and never re-records the scrolling page around it.
 * Pass [OnScreen.visible] as [running] so the loop stops while the drawing is scrolled away; when not running
 * the phase rests at 0 and no frames are requested. Compose's frame clock follows the system animator
 * duration scale; with animations off the phase rests at 1.
 */
@Composable
fun rememberLoopPhase(
    periodMillis: Int,
    label: String,
    easing: Easing = LinearEasing,
    delayMillis: Int = 0,
    running: Boolean = true,
): State<Float> {
    if (!running) return Resting
    return rememberInfiniteTransition(label).animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMillis, easing = easing), RepeatMode.Restart, StartOffset(delayMillis)),
        label = label,
    )
}

private object Resting : State<Float> {
    override val value: Float = 0f
}

/** A blink: shown for the first half of each period, and shown for good when animations are off (phase 1). */
fun blinkShown(phase: Float): Boolean = phase < 0.5f || phase >= 1f

/** Whether a looping drawing has any pixel inside the window; put [modifier] on the drawing's node. */
@Stable
class OnScreen internal constructor() {
    var visible by mutableStateOf(false)
        private set

    val modifier: Modifier = Modifier.onVisibilityChanged(minFractionVisible = 0f) { visible = it }
}

/** Starts out not visible; the first layout inside the window reports it (Compose `onVisibilityChanged`). */
@Composable
fun rememberOnScreen(): OnScreen = remember { OnScreen() }
