package app.safar.ui.documents

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.createBitmap
import app.safar.data.DocumentFiles
import app.safar.ui.art.AtlasIcons
import app.safar.ui.components.AtlasIcon
import app.safar.ui.theme.Atlas
import app.safar.ui.theme.AtlasShapes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "SafarPapers"
private const val CACHE_ENTRIES = 32

/** Decoded thumbnails by file name and size in pixels; papers never change in place (a new file gets a new name). */
private val cache = LruCache<String, ImageBitmap>(CACHE_ENTRIES)

/**
 * A square preview of a paper: a downsampled photo, or the first page of a PDF. Decoded off the main thread;
 * the papers icon shows meanwhile, and stays when the file can't be read.
 */
@Composable
fun PaperThumb(file: File, mimeType: String, size: Dp, modifier: Modifier = Modifier) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val key = "${file.name}@$px"
    val bitmap by produceState(cache.get(key), key) {
        if (value == null) value = withContext(Dispatchers.IO) { decode(file, mimeType, px) }?.also { cache.put(key, it) }
    }
    val c = Atlas.colors
    Box(
        modifier.size(size).clip(AtlasShapes.small).background(c.card).border(Atlas.dimens.outline, c.rule2, AtlasShapes.small),
        contentAlignment = Alignment.Center,
    ) {
        val b = bitmap
        if (b != null) {
            Image(b, contentDescription = null, Modifier.size(size), contentScale = ContentScale.Crop)
        } else {
            AtlasIcon(AtlasIcons.papers, tint = c.ink3)
        }
    }
}

private fun decode(file: File, mimeType: String, px: Int): ImageBitmap? = try {
    if (!file.isFile) null else if (DocumentFiles.isPdf(mimeType)) pdfPage(file, px) else photo(file, px)
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Log.w(TAG, "No preview for ${file.name}", e)
    null
}

private fun photo(file: File, px: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= px) sample *= 2
    return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}

private fun pdfPage(file: File, px: Int): ImageBitmap? =
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
        PdfRenderer(fd).use { pdf ->
            if (pdf.pageCount == 0) return null
            pdf.openPage(0).use { page ->
                val scale = px.toFloat() / minOf(page.width, page.height).coerceAtLeast(1)
                val w = (page.width * scale).toInt().coerceAtLeast(1)
                val h = (page.height * scale).toInt().coerceAtLeast(1)
                val bmp = createBitmap(w, h)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp.asImageBitmap()
            }
        }
    }
