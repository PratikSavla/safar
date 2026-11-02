package app.safar.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.safar.ui.components.SwitchRow

/** Quick taps on the version that flip Developer mode, like Android's build number. */
const val VERSION_TAPS_TO_TOGGLE = 7

/** Longest pause between two taps that still counts as one run. */
const val VERSION_TAP_GAP_MS = 1_500L

/**
 * Counts taps on the version number. [tap] returns true on the tap that completes [needed] taps, each within
 * [maxGapMs] of the one before, and starts counting again. Main thread only.
 */
class VersionTaps(private val needed: Int = VERSION_TAPS_TO_TOGGLE, private val maxGapMs: Long = VERSION_TAP_GAP_MS) {
    private var count = 0
    private var lastAt: Long? = null

    fun tap(atMillis: Long): Boolean {
        val last = lastAt
        count = if (last == null || atMillis - last > maxGapMs) 1 else count + 1
        lastAt = atMillis
        if (count < needed) return false
        count = 0
        lastAt = null
        return true
    }
}

/**
 * The Developer mode switch. Hidden until Developer mode is on (seven taps on the version); once shown it stays
 * on this screen after being switched off, so the rider sees what changed.
 */
@Composable
internal fun DeveloperModeRow(on: Boolean, onChange: (Boolean) -> Unit) {
    var revealed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(on) { if (on) revealed = true }
    if (!on && !revealed) return
    SwitchRow(
        "Developer mode",
        on,
        onChange,
        subtitle = "Shows technical details: where each value comes from, exact ages and the debug tools",
        divider = false,
    )
}
