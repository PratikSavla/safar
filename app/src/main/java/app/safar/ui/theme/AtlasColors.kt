package app.safar.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Atlas colour tokens (ux/variant-b/css/base.css).
 * Light = field notebook on warm paper. Dark = night map in blue-ink.
 */
@Immutable
data class AtlasColors(
    val isDark: Boolean,
    val paper: Color,
    val paper2: Color,
    val card: Color,
    val card2: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val rule: Color,
    val rule2: Color,
    val rust: Color,
    val rust2: Color,
    val rustWash: Color,
    val onRust: Color,
    val moss: Color,
    val mossWash: Color,
    val slate: Color,
    val amber: Color,
    val amberWash: Color,
    val contour: Color,
    val contourIdx: Color,
    /** Soft glow behind contour lines (dark theme only; transparent in light). */
    val contourGlow: Color,
    val scrim: Color,
    val lcd: Color,
    val lcdInk: Color,
    val lcdDim: Color,
    /** Long soft shadow under raised cards (the second layer of --shadow). */
    val shadow: Color,
    /** Hairline under raised cards (the first layer of --shadow). */
    val shadowEdge: Color,
)

val LightAtlasColors = AtlasColors(
    isDark = false,
    paper = Color(0xFFF3EEE4),
    paper2 = Color(0xFFEAE3D4),
    card = Color(0xFFFBF8F1),
    card2 = Color(0xFFF6F1E6),
    ink = Color(0xFF1C1B19),
    ink2 = Color(0xFF4B473F),
    ink3 = Color(0xFF857E71),
    rule = Color(0xFFDCD2BF),
    rule2 = Color(0xFFC4B79E),
    rust = Color(0xFFC8462B),
    rust2 = Color(0xFFA63A22),
    rustWash = Color(0x1AC8462B),
    onRust = Color(0xFFFBF3E8),
    moss = Color(0xFF55663D),
    mossWash = Color(0x1F55663D),
    slate = Color(0xFF42586B),
    amber = Color(0xFFB97A12),
    amberWash = Color(0x2EE3A73A),
    contour = Color(60, 48, 30).copy(alpha = 0.085f),
    contourIdx = Color(60, 48, 30).copy(alpha = 0.16f),
    contourGlow = Color.Transparent,
    scrim = Color(40, 30, 18).copy(alpha = 0.38f),
    lcd = Color(0xFFAEB99A),
    lcdInk = Color(0xFF1E2918),
    lcdDim = Color(0x261E2918),
    shadow = Color(80, 52, 24).copy(alpha = 0.45f),
    shadowEdge = Color(28, 27, 25).copy(alpha = 0.05f),
)

val DarkAtlasColors = AtlasColors(
    isDark = true,
    paper = Color(0xFF12161C),
    paper2 = Color(0xFF0D1015),
    card = Color(0xFF18202A),
    card2 = Color(0xFF1C2530),
    ink = Color(0xFFEEE6D6),
    ink2 = Color(0xFFBDB5A5),
    ink3 = Color(0xFF7E8189),
    rule = Color(0xFF262F3A),
    rule2 = Color(0xFF36414E),
    rust = Color(0xFFDA5A3C),
    rust2 = Color(0xFFC8462B),
    rustWash = Color(0x24DA5A3C),
    onRust = Color(0xFFFFF6EC),
    moss = Color(0xFF9CB283),
    mossWash = Color(0x229CB283),
    slate = Color(0xFF8FA8BF),
    amber = Color(0xFFE7A944),
    amberWash = Color(0x26E7A944),
    contour = Color(120, 170, 220).copy(alpha = 0.085f),
    contourIdx = Color(140, 190, 240).copy(alpha = 0.17f),
    contourGlow = Color(110, 170, 230).copy(alpha = 0.10f),
    scrim = Color(4, 6, 10).copy(alpha = 0.6f),
    lcd = Color(0xFF26331F),
    lcdInk = Color(0xFFC7E3A4),
    lcdDim = Color(0x1CC7E3A4),
    shadow = Color.Black.copy(alpha = 0.9f),
    shadowEdge = Color.Black.copy(alpha = 0.35f),
)

/** The cluster bezel around the LCD preview; the same in both themes (it is a physical part). */
val ClusterBezel = Color(0xFF17191C)
