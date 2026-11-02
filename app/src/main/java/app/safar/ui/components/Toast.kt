package app.safar.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.safar.ui.theme.Atlas
import app.safar.ui.theme.AtlasShapes
import kotlinx.coroutines.delay

/** One message at a time; a new one replaces the old. */
@Stable
class AtlasToastState {
    /** The message on screen, with a serial so the same text twice still restarts the timer. */
    var current: Pair<Long, String>? by mutableStateOf(null)
        private set
    private var serial = 0L

    fun show(message: String) {
        current = ++serial to message
    }

    fun dismiss() {
        current = null
    }
}

@Composable
fun rememberToastState(): AtlasToastState = remember { AtlasToastState() }

/**
 * Short confirmation slip in ink ("Opening Google Maps…", "Ride saved to Trips").
 * Put it in a full-screen Box; [bottomPadding] lifts it above the tabs. TalkBack announces it.
 */
@Composable
fun AtlasToastHost(state: AtlasToastState, modifier: Modifier = Modifier, bottomPadding: Dp = 120.dp, durationMillis: Long = 2600) {
    val c = Atlas.colors
    val shown = state.current
    // Plain holder, not state: keeps the text visible while the slip animates out.
    val last = remember { arrayOf("") }
    if (shown != null) last[0] = shown.second
    LaunchedEffect(shown?.first) {
        if (shown != null) {
            delay(durationMillis)
            state.dismiss()
        }
    }
    Box(modifier.fillMaxWidth().padding(bottom = bottomPadding, start = 24.dp, end = 24.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(shown != null, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }) {
            BasicText(
                last[0],
                Modifier
                    .widthIn(max = 360.dp)
                    .background(c.ink, AtlasShapes.small)
                    .padding(horizontal = 16.dp, vertical = 11.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                style = Atlas.type.note.copy(color = c.paper, textAlign = TextAlign.Center),
            )
        }
    }
}
