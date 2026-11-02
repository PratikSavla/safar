package app.safar.bikesim.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Plain colours for a test tool, in light and dark. */
data class Palette(
    val background: Color,
    val surface: Color,
    val text: Color,
    val muted: Color,
    val line: Color,
    val accent: Color,
    val onAccent: Color,
    val ok: Color,
    val error: Color,
)

private val Light = Palette(
    background = Color(0xFFF4F2EE),
    surface = Color(0xFFFFFFFF),
    text = Color(0xFF1C1B19),
    muted = Color(0xFF6B675F),
    line = Color(0xFFD6D1C7),
    accent = Color(0xFF1F5FAD),
    onAccent = Color(0xFFFFFFFF),
    ok = Color(0xFF2E7D32),
    error = Color(0xFFB3261E),
)

private val Dark = Palette(
    background = Color(0xFF141413),
    surface = Color(0xFF1F1E1C),
    text = Color(0xFFEDEAE4),
    muted = Color(0xFFA29E95),
    line = Color(0xFF3A3834),
    accent = Color(0xFF7DB0F0),
    onAccent = Color(0xFF0B1A2E),
    ok = Color(0xFF81C784),
    error = Color(0xFFF2857D),
)

val LocalPalette = staticCompositionLocalOf { Light }

@Composable
fun SimTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPalette provides if (isSystemInDarkTheme()) Dark else Light, content = content)
}

object Type {
    val title = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    val section = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
    val body = TextStyle(fontSize = 15.sp)
    val small = TextStyle(fontSize = 12.sp)
    val mono = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    val value = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
}
