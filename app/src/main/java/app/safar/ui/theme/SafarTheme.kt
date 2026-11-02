package app.safar.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import app.safar.data.ThemeMode

val LocalAtlasColors = staticCompositionLocalOf { LightAtlasColors }
val LocalAtlasType = staticCompositionLocalOf { DefaultAtlasType }
val LocalAtlasDimens = staticCompositionLocalOf { AtlasDimens() }

/** Read the current Atlas tokens: `Atlas.colors.rust`, `Atlas.type.kicker`, `Atlas.dimens.pad`. */
object Atlas {
    val colors: AtlasColors
        @Composable @ReadOnlyComposable
        get() = LocalAtlasColors.current

    val type: AtlasType
        @Composable @ReadOnlyComposable
        get() = LocalAtlasType.current

    val dimens: AtlasDimens
        @Composable @ReadOnlyComposable
        get() = LocalAtlasDimens.current
}

/** True when [mode] resolves to the dark (night map) theme. */
@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * Atlas theme root. Provides colours, type and dimensions, the flat press indication
 * (no ripple) and selection colours, and sets the system bar icon colours to match.
 */
@Composable
fun SafarTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = themeMode.isDark()
    val colors = if (dark) DarkAtlasColors else LightAtlasColors
    val indication = remember(colors) { AtlasIndication(colors.ink.copy(alpha = 0.06f)) }
    val selection = remember(colors) { TextSelectionColors(colors.rust, colors.rustWash) }
    SystemBarIcons(darkTheme = dark)
    CompositionLocalProvider(
        LocalAtlasColors provides colors,
        LocalAtlasType provides DefaultAtlasType,
        LocalAtlasDimens provides AtlasDimens(),
        LocalIndication provides indication,
        LocalTextSelectionColors provides selection,
        content = content,
    )
}

/** Dark icons on warm paper, light icons on the night map. Bars stay transparent (edge-to-edge). */
@Composable
private fun SystemBarIcons(darkTheme: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
