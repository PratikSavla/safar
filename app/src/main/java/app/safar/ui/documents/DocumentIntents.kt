package app.safar.ui.documents

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.FileProvider
import app.safar.ui.log.LOG_FILE_AUTHORITY
import java.io.File
import java.io.InputStream

private const val TAG = "SafarPapers"

/** A file the rider picked: its type and name as the providing app reports them, and how to read it. */
class PickedSource(val mimeType: String?, val displayName: String?, val open: () -> InputStream?)

/** Wraps a picker [uri] for the ViewModel. Nothing is read until [PickedSource.open]. */
fun pickedSource(context: Context, uri: Uri): PickedSource {
    val resolver = context.contentResolver
    val name = try {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (e: Exception) {
        Log.w(TAG, "Could not read the picked file's name", e)
        null
    }
    return PickedSource(resolver.getType(uri), name) { resolver.openInputStream(uri) }
}

/**
 * Shows [file] in a viewer app with read access to that one file. Returns false (and logs why) when no app
 * on the phone can show it.
 */
fun openPaper(context: Context, file: File, mimeType: String): Boolean {
    val uri = try {
        FileProvider.getUriForFile(context, LOG_FILE_AUTHORITY, file)
    } catch (e: IllegalArgumentException) {
        Log.e(TAG, "No FileProvider path for ${file.name}", e)
        return false
    }
    val view = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, mimeType)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(view)
        true
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "No app can show $mimeType", e)
        false
    }
}
