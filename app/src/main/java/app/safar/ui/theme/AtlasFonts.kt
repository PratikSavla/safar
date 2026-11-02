@file:OptIn(ExperimentalTextApi::class)

package app.safar.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import app.safar.R

/*
 * Atlas type families (SIL OFL 1.1, see assets/licenses/OFL.txt):
 * Fraunces (variable: opsz, wght), IBM Plex Mono (static 400/500/600), Instrument Sans (variable: wdth, wght).
 * A variable font gets one Font entry per weight the design uses, each pinned with FontVariation.
 */

/** Weights the prototype uses for Fraunces (CSS font-weight values). */
private val FRAUNCES_WEIGHTS = listOf(330, 360, 380, 400, 450, 500, 560, 600)
private val SANS_WEIGHTS = listOf(400, 500, 600, 700)

/** Optical size for large display numerals and headings (CSS font-optical-sizing: auto at ~40 px). */
private const val OPSZ_DISPLAY = 48f

/** Optical size for running serif text (ledes, list titles). */
private const val OPSZ_TEXT = 16f

private fun fraunces(opsz: Float, weights: List<Int> = FRAUNCES_WEIGHTS): FontFamily = FontFamily(
    weights.flatMap { w ->
        listOf(
            frauncesFont(R.font.fraunces, w, FontStyle.Normal, opsz),
            frauncesFont(R.font.fraunces_italic, w, FontStyle.Italic, opsz),
        )
    },
)

private fun frauncesFont(res: Int, weight: Int, style: FontStyle, opsz: Float) = Font(
    resId = res,
    weight = FontWeight(weight),
    style = style,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.Setting("opsz", opsz),
    ),
)

/** Fraunces tuned for big numerals and titles. */
val FrauncesDisplay: FontFamily = fraunces(OPSZ_DISPLAY)

/** Fraunces tuned for reading sizes. */
val FrauncesText: FontFamily = fraunces(OPSZ_TEXT)

/** Optical size for glanceable numerals (ride.css `"opsz" 30`): sturdier strokes that survive glare. */
private const val OPSZ_GLANCE = 30f

/** Heavy Fraunces for values read at a glance while riding (speed, turn distance, caller). */
val FrauncesGlance: FontFamily = fraunces(OPSZ_GLANCE, listOf(500, 600, 650, 750, 800))

val InstrumentSans: FontFamily = FontFamily(
    SANS_WEIGHTS.map { w ->
        Font(
            resId = R.font.instrument_sans,
            weight = FontWeight(w),
            variationSettings = FontVariation.Settings(FontVariation.weight(w), FontVariation.width(100f)),
        )
    },
)

val PlexMono: FontFamily = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
)
