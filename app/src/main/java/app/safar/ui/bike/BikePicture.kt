package app.safar.ui.bike

import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.safar.ui.art.BikeSilhouette
import app.safar.ui.art.label
import app.safar.ui.theme.Atlas
import app.safar.ui.theme.AtlasShapes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "SafarBike"
private const val PHOTO_PX = 1280

/**
 * The rider's bike: their photo, cropped wide, when there is one and it reads; else the drawing for its style.
 * [photoFile] resolves a stored name to its file.
 */
@Composable
fun BikePicture(picture: BikePictureUi, photoFile: (String) -> File, modifier: Modifier = Modifier, drawingWidth: Dp = 160.dp) {
    val name = picture.photo
    val bitmap by produceState<ImageBitmap?>(null, name) {
        value = name?.let { n -> withContext(Dispatchers.IO) { decodePhoto(photoFile(n)) } }
    }
    val b = bitmap
    if (b != null) {
        Image(
            b, contentDescription = "Your bike",
            modifier = modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(AtlasShapes.small)
                .border(Atlas.dimens.outline, Atlas.colors.rule2, AtlasShapes.small),
            contentScale = ContentScale.Crop,
        )
    } else {
        BikeSilhouette(picture.style, modifier, width = drawingWidth, color = Atlas.colors.ink2, contentDescription = picture.style.label())
    }
}

/** A downsampled, upright copy of the photo, or null when it is gone or unreadable. */
private fun decodePhoto(file: File): ImageBitmap? = try {
    when {
        !file.isFile -> null
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> decodeUpright(file)
        else -> decodeSampled(file)
    }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Log.w(TAG, "Could not read the bike photo ${file.name}", e)
    null
}

/** ImageDecoder applies the camera's rotation; scaled to at most [PHOTO_PX] on the long side. */
@androidx.annotation.RequiresApi(Build.VERSION_CODES.P)
private fun decodeUpright(file: File): ImageBitmap =
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
        val long = maxOf(info.size.width, info.size.height)
        if (long > PHOTO_PX) {
            val scale = PHOTO_PX.toFloat() / long
            decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
        }
    }.asImageBitmap()

private fun decodeSampled(file: File): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= PHOTO_PX) sample *= 2
    return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}
