package app.safar.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * True when the rider turned on Developer mode in Settings: technical details (sources, exact ages, frames,
 * UUIDs, MTU) and tools are shown. Provided once at the shell root from [app.safar.data.Settings.developerMode].
 * Static: it changes only when the rider flips the switch, so readers pay no tracking cost on every frame.
 */
val LocalDeveloperMode = staticCompositionLocalOf { false }

/** Whether Developer mode is on, for a branch inside a composable: `if (developerMode()) ...`. */
@Composable
@ReadOnlyComposable
fun developerMode(): Boolean = LocalDeveloperMode.current

/** [developer] in Developer mode, [normal] otherwise: `Note(byMode("Ready to ride", "MTU 65 · notify on"))`. */
@Composable
@ReadOnlyComposable
fun <T> byMode(normal: T, developer: T): T = if (LocalDeveloperMode.current) developer else normal

/** Shows [content] only in Developer mode. Nothing is composed otherwise. */
@Composable
fun DevOnly(content: @Composable () -> Unit) {
    if (LocalDeveloperMode.current) content()
}
