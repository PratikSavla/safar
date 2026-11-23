package app.safar.ui.bike

import app.safar.core.BikeModel
import app.safar.core.BikeStyle
import app.safar.data.Settings

/**
 * What stands for the rider's bike: their [photo] (a [app.safar.data.BikePhotos] file name) when there is one,
 * else the drawing for [style]. [style] is the rider's [chosen] drawing, else the paired model's
 * ([modelStyle]), else a street bike.
 */
data class BikePictureUi(
    val style: BikeStyle = BikeStyle.STANDARD,
    val modelStyle: BikeStyle? = null,
    val chosen: BikeStyle? = null,
    val photo: String? = null,
)

fun bikePicture(model: BikeModel?, settings: Settings): BikePictureUi = BikePictureUi(
    style = pictureStyle(model, settings.bikeStyle),
    modelStyle = model?.style,
    chosen = settings.bikeStyle,
    photo = settings.bikePhoto,
)

/** The drawing to show: the rider's choice, else the model's, else a street bike. */
fun pictureStyle(model: BikeModel?, chosen: BikeStyle?): BikeStyle = chosen ?: model?.style ?: BikeStyle.STANDARD

/** Tapping a drawing: the model's own (or the current choice again) clears the choice, so a new pairing decides. */
fun styleChoice(picture: BikePictureUi, tapped: BikeStyle): BikeStyle? = if (tapped == picture.modelStyle) null else tapped
