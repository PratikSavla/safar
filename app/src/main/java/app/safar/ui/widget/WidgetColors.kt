package app.safar.ui.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider
import androidx.glance.color.ColorProviders
import androidx.glance.color.colorProviders
import androidx.glance.unit.ColorProvider
import app.safar.ui.theme.AtlasColors
import app.safar.ui.theme.DarkAtlasColors
import app.safar.ui.theme.LightAtlasColors

/** Atlas tokens for Glance: paper by day, night map by night, switched by the launcher's own dark mode. */
object WidgetColors {
    private fun pick(token: (AtlasColors) -> Color): ColorProvider = ColorProvider(day = token(LightAtlasColors), night = token(DarkAtlasColors))

    val paper = pick { it.paper }
    val ink = pick { it.ink }
    val ink2 = pick { it.ink2 }
    val ink3 = pick { it.ink3 }
    val rule2 = pick { it.rule2 }
    val rust = pick { it.rust }
    val onRust = pick { it.onRust }
    val moss = pick { it.moss }
    val amber = pick { it.amber }
    val slate = pick { it.slate }

    fun tone(t: WidgetTone): ColorProvider = when (t) {
        WidgetTone.MOSS -> moss
        WidgetTone.AMBER -> amber
        WidgetTone.SLATE -> slate
        WidgetTone.RUST -> rust
    }

    /** GlanceTheme roles mapped from Atlas: rust is primary, moss secondary, amber tertiary, paper the background. */
    val theme: ColorProviders = colorProviders(
        primary = rust, onPrimary = onRust, primaryContainer = pick { it.rustWash }, onPrimaryContainer = ink,
        secondary = moss, onSecondary = paper, secondaryContainer = pick { it.mossWash }, onSecondaryContainer = ink,
        tertiary = amber, onTertiary = paper, tertiaryContainer = pick { it.amberWash }, onTertiaryContainer = ink,
        error = rust, errorContainer = pick { it.rustWash }, onError = onRust, onErrorContainer = ink,
        background = paper, onBackground = ink, surface = pick { it.card }, onSurface = ink,
        surfaceVariant = pick { it.card2 }, onSurfaceVariant = ink2, outline = rule2,
        inverseOnSurface = paper, inverseSurface = ink, inversePrimary = pick { it.rust2 }, widgetBackground = paper,
    )
}
