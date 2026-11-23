package app.safar.ui.art

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.safar.core.BikeStyle
import app.safar.core.VehicleFamily
import app.safar.ui.theme.Atlas

/**
 * Side-view ink silhouettes (art.js `silhouette()`), 124 x 66 viewport, 2.6 stroke, front to the right. One per
 * [BikeStyle]: the motorcycle and scooter of the prototype, plus a maxi-scooter, a sport scooter, a faired
 * sport bike and an adventure bike drawn in the same hand.
 */
object BikeSilhouettes {
    val motorcycle: ImageVector by lazy {
        lineArt("atlas.motorcycle", w = 124f, h = 66f, stroke = 2.6f) {
            circle(24f, 48f, 14f); circle(24f, 48f, 4f); circle(100f, 48f, 14f); circle(100f, 48f, 4f)
            path("M24 48 44 28h26l14 6 16 14"); path("M44 28 38 20h14l8 8"); path("M58 28c4-8 14-10 22-6l4 12")
            path("M84 34 92 14l6-2 4 6"); path("M92 14l-6-4"); path("M50 40h20l8-6"); path("M28 30h14")
        }
    }

    val scooter: ImageVector by lazy {
        lineArt("atlas.scooter", w = 124f, h = 66f, stroke = 2.6f) {
            circle(26f, 52f, 10f); circle(26f, 52f, 3f); circle(96f, 52f, 10f); circle(96f, 52f, 3f)
            path("M16 46c0-12 10-18 22-18h18l6 10h16c8 0 14 6 16 14"); path("M40 28c0-6 6-8 16-8h12")
            path("M78 38 88 12h8"); path("M84 12l-4-4"); path("M60 46h22")
        }
    }

    val maxiScooter: ImageVector by lazy {
        lineArt("atlas.maxiScooter", w = 124f, h = 66f, stroke = 2.6f) {
            circle(26f, 52f, 10f); circle(26f, 52f, 3f); circle(98f, 52f, 10f); circle(98f, 52f, 3f)
            path("M10 44c0-14 12-22 28-22h20l6 14h14c10 0 18 8 20 16"); path("M36 22c2-6 8-9 16-9h10l4 6")
            path("M78 36 90 10l8-4"); path("M90 12l-6-6"); path("M58 46h26"); path("M12 34l-4-2")
        }
    }

    val sportScooter: ImageVector by lazy {
        lineArt("atlas.sportScooter", w = 124f, h = 66f, stroke = 2.6f) {
            circle(26f, 52f, 10f); circle(26f, 52f, 3f); circle(96f, 52f, 10f); circle(96f, 52f, 3f)
            path("M14 44l6-14h28l6 10h22l10 6 6 8"); path("M38 30l4-8h18l4 8")
            path("M80 40 90 14h8"); path("M86 14l-4-4"); path("M58 46h22"); path("M18 32l-8-6")
        }
    }

    val sport: ImageVector by lazy {
        lineArt("atlas.sport", w = 124f, h = 66f, stroke = 2.6f) {
            circle(24f, 48f, 14f); circle(24f, 48f, 4f); circle(100f, 48f, 14f); circle(100f, 48f, 4f)
            path("M24 48 46 32h22l16 8 16 8"); path("M12 26l14 2h18"); path("M44 28l6-8h16l8 6")
            path("M74 26l14-10h8l4 6-4 12-14 6"); path("M90 16l4-8"); path("M50 40h18l8-6")
        }
    }

    val adventure: ImageVector by lazy {
        lineArt("atlas.adventure", w = 124f, h = 66f, stroke = 2.6f) {
            circle(24f, 48f, 13f); circle(24f, 48f, 4f); circle(100f, 47f, 15f); circle(100f, 47f, 4f)
            path("M24 48 44 26h24l16 6 16 15"); path("M40 26 34 16h16l8 10"); path("M58 26c4-8 14-10 22-6l4 10")
            path("M84 30 92 6l8-2"); path("M92 10l-8-4"); path("M92 22l14 6"); path("M8 20h16l4 6"); path("M50 40h20l8-6")
        }
    }

    fun of(family: VehicleFamily): ImageVector = if (family == VehicleFamily.SCOOTER) scooter else motorcycle

    fun of(style: BikeStyle): ImageVector = when (style) {
        BikeStyle.SCOOTER -> scooter
        BikeStyle.MAXI_SCOOTER -> maxiScooter
        BikeStyle.SPORT_SCOOTER -> sportScooter
        BikeStyle.STANDARD -> motorcycle
        BikeStyle.SPORT -> sport
        BikeStyle.ADVENTURE -> adventure
    }
}

/** The rider's words for a drawing, in the picture sheet and for TalkBack. */
fun BikeStyle.label(): String = when (this) {
    BikeStyle.SCOOTER -> "Scooter"
    BikeStyle.MAXI_SCOOTER -> "Maxi-scooter"
    BikeStyle.SPORT_SCOOTER -> "Sport scooter"
    BikeStyle.STANDARD -> "Street bike"
    BikeStyle.SPORT -> "Sport bike"
    BikeStyle.ADVENTURE -> "Adventure bike"
}

/** A bike silhouette in ink. Decorative unless [contentDescription] is given. */
@Composable
fun BikeSilhouette(
    family: VehicleFamily,
    modifier: Modifier = Modifier,
    width: Dp = 124.dp,
    color: Color = Atlas.colors.ink,
    contentDescription: String? = null,
) = BikeArt(BikeSilhouettes.of(family), modifier, width, color, contentDescription)

/** The drawing for [style] in ink. Decorative unless [contentDescription] is given. */
@Composable
fun BikeSilhouette(
    style: BikeStyle,
    modifier: Modifier = Modifier,
    width: Dp = 124.dp,
    color: Color = Atlas.colors.ink,
    contentDescription: String? = null,
) = BikeArt(BikeSilhouettes.of(style), modifier, width, color, contentDescription)

@Composable
private fun BikeArt(art: ImageVector, modifier: Modifier, width: Dp, color: Color, contentDescription: String?) {
    Image(
        imageVector = art,
        contentDescription = contentDescription,
        modifier = modifier.size(width, width * (66f / 124f)),
        colorFilter = ColorFilter.tint(color),
    )
}
