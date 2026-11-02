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
import app.safar.core.VehicleFamily
import app.safar.ui.theme.Atlas

/** Side-view ink silhouettes (art.js `silhouette()`), 124 x 66 viewport, 2.6 stroke. */
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

    fun of(family: VehicleFamily): ImageVector = if (family == VehicleFamily.SCOOTER) scooter else motorcycle
}

/** A bike silhouette in ink. Decorative unless [contentDescription] is given. */
@Composable
fun BikeSilhouette(
    family: VehicleFamily,
    modifier: Modifier = Modifier,
    width: Dp = 124.dp,
    color: Color = Atlas.colors.ink,
    contentDescription: String? = null,
) {
    Image(
        imageVector = BikeSilhouettes.of(family),
        contentDescription = contentDescription,
        modifier = modifier.size(width, width * (66f / 124f)),
        colorFilter = ColorFilter.tint(color),
    )
}
