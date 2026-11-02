package app.safar.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Atlas spacing, sizes and corner shapes (base.css `--pad`, components.css radii). */
@Immutable
data class AtlasDimens(
    /** Page side gutter (`--pad`). */
    val pad: Dp = 22.dp,
    /** Space above a section header (`.sec` margin-top). */
    val sectionGap: Dp = 30.dp,
    /** Minimum touch target for anything tappable. */
    val touchTarget: Dp = 48.dp,
    /** Button height (`.btn`). */
    val buttonHeight: Dp = 52.dp,
    /** Small button height (`.btn.sm`); the touch target stays 48 dp. */
    val buttonHeightSmall: Dp = 40.dp,
    /** Setting row minimum height (`.row`). */
    val rowHeight: Dp = 64.dp,
    /** Default icon size (`.ico`). */
    val icon: Dp = 22.dp,
    /** Hairline width. */
    val hairline: Dp = 1.dp,
    /** Ink outline width (ghost buttons, segmented control). */
    val outline: Dp = 1.5.dp,
)

/** Corner radii: Atlas keeps corners tight, like cut paper. */
object AtlasShapes {
    /** Buttons, switches, tiles (4 px). */
    val small = RoundedCornerShape(4.dp)

    /** Cards and tickets (6 px). */
    val card = RoundedCornerShape(6.dp)

    /** Stamps and chips (3 px). */
    val stamp = RoundedCornerShape(3.dp)

    /** Fuel bars and LCD details. */
    val tiny = RoundedCornerShape(2.dp)
}
