package app.safar.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import app.safar.ui.format.Formatters
import kotlinx.coroutines.delay

private const val SECOND_MS = 1000L

/**
 * The app-wide clock behind every age ("3 s ago"): one State that ticks on each whole second, provided once at
 * the shell root by [ProvideNow]. Static: the State object never changes, so only the composables that read its
 * value recompose on a tick, never the ones that merely pass it on.
 */
val LocalNow = staticCompositionLocalOf<State<Long>?> { null }

/** Provides [LocalNow] to [content]: one ticker for the whole app instead of one per age text. */
@Composable
fun ProvideNow(content: @Composable () -> Unit) {
    val now = tickingNow(SECOND_MS)
    CompositionLocalProvider(LocalNow provides now, content = content)
}

/**
 * The current time, updated every [periodMillis] (a whole number of seconds). Uses the shared [LocalNow] when the
 * shell provides it, so all ages tick in the same frame; a coarser period only notifies its readers when the
 * period rolls over. Read `.value` in the smallest composable that shows it.
 */
@Composable
fun rememberNow(periodMillis: Long = SECOND_MS): State<Long> {
    val shared = LocalNow.current ?: return tickingNow(periodMillis)
    if (periodMillis <= SECOND_MS) return shared
    return remember(shared, periodMillis) { derivedStateOf { shared.value / periodMillis * periodMillis } }
}

/** "3 s ago" for [atMillis], kept live by the shared clock. Call it from the composable that draws the text. */
@Composable
fun liveAge(atMillis: Long, periodMillis: Long = SECOND_MS): String {
    val now by rememberNow(periodMillis)
    return Formatters.age(atMillis, now)
}

@Composable
private fun tickingNow(periodMillis: Long): State<Long> = produceState(System.currentTimeMillis(), periodMillis) {
    while (true) {
        delay(periodMillis - System.currentTimeMillis() % periodMillis)
        value = System.currentTimeMillis()
    }
}
