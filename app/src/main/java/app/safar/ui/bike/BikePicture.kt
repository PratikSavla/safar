package app.safar.ui.bike

import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.util.Log
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.safar.core.BikeStyle
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
private const val CACHE_ENTRIES = 4

/** Decoded photos by file name and size: a photo never changes in place (a new one gets a new name). */
private val cache = LruCache<String, ImageBitmap>(CACHE_ENTRIES)

/** The photo [name] decoded off the main thread at most [maxPx] on its long side; null while loading or unreadable. */
@Composable
private fun rememberPhoto(name: String?, photoFile: (String) -> File, maxPx: Int): ImageBitmap? {
    val key = name?.let { "$it@$maxPx" }
    val bitmap by produceState(key?.let { cache.get(it) }, key) {
        if (value == null && name != null && key != null) {
            value = withContext(Dispatchers.IO) { decodePhoto(photoFile(name), maxPx) }?.also { cache.put(key, it) }
        }
    }
    return bitmap
}

/**
 * The rider's bike: their photo, cropped wide, when there is one and it reads; else the drawing for its style.
 * [photoFile] resolves a stored name to its file.
 */
@Composable
fun BikePicture(picture: BikePictureUi, photoFile: (String) -> File, modifier: Modifier = Modifier, drawingWidth: Dp = 160.dp) {
    val b = rememberPhoto(picture.photo, photoFile, PHOTO_PX)
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

/**
 * The bike, small: the rider's [photo] cropped to the drawing's shape, else the drawing for [style]. For the
 * Today card's link line. Decorative there, like the drawing.
 */
@Composable
fun BikeThumb(photo: String?, style: BikeStyle, photoFile: (String) -> File, width: Dp, modifier: Modifier = Modifier) {
    val px = with(LocalDensity.current) { width.roundToPx() } * 2
    val b = rememberPhoto(photo, photoFile, px)
    if (b != null) {
        Image(
            b, contentDescription = null,
            modifier = modifier.size(width, width * (66f / 124f)).clip(AtlasShapes.small)
                .border(Atlas.dimens.outline, Atlas.colors.rule2, AtlasShapes.small),
            contentScale = ContentScale.Crop,
        )
    } else {
        BikeSilhouette(style, modifier, width = width)
    }
}

/** A downsampled, upright copy of the photo, or null when it is gone or unreadable. */
private fun decodePhoto(file: File, maxPx: Int): ImageBitmap? = try {
    when {
        !file.isFile -> null
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> decodeUpright(file, maxPx)
        else -> decodeSampled(file, maxPx)
    }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Log.w(TAG, "Could not read the bike photo ${file.name}", e)
    null
}

/** ImageDecoder applies the camera's rotation; scaled to at most [maxPx] on the long side. */
@androidx.annotation.RequiresApi(Build.VERSION_CODES.P)
private fun decodeUpright(file: File, maxPx: Int): ImageBitmap =
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
        val long = maxOf(info.size.width, info.size.height)
        if (long > maxPx) {
            val scale = maxPx.toFloat() / long
            decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
        }
    }.asImageBitmap()

private fun decodeSampled(file: File, maxPx: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
    return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}
