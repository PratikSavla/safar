package app.safar.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Lining, tabular figures: numbers line up and do not jump while they tick. */
const val TABULAR_FIGURES = "lnum, tnum"

/**
 * Atlas text styles (ux/variant-b/css/base.css "Type"). All sizes are sp (1 rem = 16 sp)
 * so the system font scale applies. Colours are applied by the components, not here.
 */
@Immutable
data class AtlasType(
    /** Masthead title on tab pages (`.mast h1`, 2.6 rem). */
    val displayLarge: TextStyle,
    /** Inner page title (`.top h1`, 2 rem). */
    val displayMedium: TextStyle,
    /** Sheet and card titles (`.sheet.paper h2`, 1.6 rem). */
    val displaySmall: TextStyle,
    /** Big ledger number (`.num.big`, 3 rem, tabular). */
    val numberLarge: TextStyle,
    /** Ledger grid number (`.num.sm`, 1.6 rem, tabular). */
    val numberSmall: TextStyle,
    /** Mono uppercase label (`.kicker`). Apply uppercase in the component. */
    val kicker: TextStyle,
    /** Section heading label (`.sec h2`). */
    val section: TextStyle,
    /** Source and age caption (`.src`, 11 sp mono). */
    val src: TextStyle,
    /** Serif introduction paragraph (`.lede`). */
    val lede: TextStyle,
    /** Small explanatory text (`.note`). */
    val note: TextStyle,
    /** Body UI text (Instrument Sans 1 rem). */
    val ui: TextStyle,
    /** Row title (`.row .t b`). */
    val uiStrong: TextStyle,
    /** Row subtitle (`.row .t small`). */
    val uiSmall: TextStyle,
    /** Button label (`.btn`). */
    val button: TextStyle,
    /** Mono value in leader rows (`.lead .v`). */
    val monoValue: TextStyle,
    /** Rubber-stamp label (`.stamp`). */
    val stamp: TextStyle,
    /** Serif list title (`.dest .dn b`, `.tile .nm`). */
    val serifTitle: TextStyle,
    /** Italic serif aside (`.serif.it`). */
    val serifItalic: TextStyle,
    /** Inactive ticket-stub tab label. */
    val tabLabel: TextStyle,
    /** Active ticket-stub tab label (italic serif). */
    val tabLabelActive: TextStyle,
)

private val tight = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)

private fun display(size: Float, weight: Int, tracking: Float, lineHeight: Float) = TextStyle(
    fontFamily = FrauncesDisplay,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    letterSpacing = tracking.em,
    lineHeight = (size * lineHeight).sp,
    lineHeightStyle = tight,
    fontFeatureSettings = TABULAR_FIGURES,
)

private fun mono(size: Float, weight: FontWeight, tracking: Float, lineHeight: Float = 1.3f) = TextStyle(
    fontFamily = PlexMono,
    fontWeight = weight,
    fontSize = size.sp,
    letterSpacing = tracking.em,
    lineHeight = (size * lineHeight).sp,
)

private fun sans(size: Float, weight: Int, lineHeight: Float = 1.4f) = TextStyle(
    fontFamily = InstrumentSans,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = (size * lineHeight).sp,
)

val DefaultAtlasType = AtlasType(
    displayLarge = display(41.6f, 600, -0.03f, 0.98f),
    displayMedium = display(32f, 560, -0.02f, 1.04f),
    displaySmall = display(25.6f, 560, -0.02f, 1.06f),
    numberLarge = display(48f, 500, -0.035f, 1f),
    numberSmall = display(25.6f, 500, -0.02f, 1.05f),
    kicker = mono(11f, FontWeight.Medium, 0.14f, 1.2f),
    section = mono(11f, FontWeight.Medium, 0.16f, 1.2f),
    src = mono(11f, FontWeight.Normal, 0.02f),
    lede = TextStyle(
        fontFamily = FrauncesText,
        fontWeight = FontWeight(380),
        fontSize = 17f.sp,
        lineHeight = (17f * 1.45f).sp,
    ),
    note = sans(13f, 400, 1.45f),
    ui = sans(16f, 400),
    uiStrong = sans(15.2f, 600, 1.3f),
    uiSmall = sans(12.5f, 400, 1.35f),
    button = sans(15.2f, 600, 1f).copy(letterSpacing = 0.01.em),
    monoValue = mono(13f, FontWeight.Medium, 0f),
    stamp = mono(10f, FontWeight.SemiBold, 0.16f, 1f),
    serifTitle = TextStyle(
        fontFamily = FrauncesText,
        fontWeight = FontWeight.Medium,
        fontSize = 19.2f.sp,
        lineHeight = (19.2f * 1.15f).sp,
    ),
    serifItalic = TextStyle(
        fontFamily = FrauncesText,
        fontWeight = FontWeight.Normal,
        fontStyle = FontStyle.Italic,
        fontSize = 16f.sp,
        lineHeight = (16f * 1.35f).sp,
    ),
    tabLabel = mono(9f, FontWeight.Medium, 0.16f, 1f),
    tabLabelActive = TextStyle(
        fontFamily = FrauncesText,
        fontWeight = FontWeight.Medium,
        fontStyle = FontStyle.Italic,
        fontSize = 13f.sp,
        lineHeight = 13f.sp,
    ),
)
