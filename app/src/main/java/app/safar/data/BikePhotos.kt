package app.safar.data

import java.io.File
import java.io.InputStream

/**
 * The rider's photo of the bike: one image in `filesDir/bike/`, named like a paper (`<uuid>.<ext>`). Left out
 * of Android backup and of exports, like the papers' files. Pure Java I/O; call from the IO dispatcher.
 */
class BikePhotos(dir: File) {
    private val files = DocumentFiles(dir)

    fun file(fileName: String): File = files.file(fileName)

    fun exists(fileName: String?): Boolean = fileName != null && files.exists(fileName)

    /** Copies [input] in as a new photo and returns its file name. Not an image, or too big: [DocumentRejected]. */
    fun save(input: InputStream, mimeType: String?): String {
        if (mimeType?.startsWith("image/") != true) throw DocumentRejected("Choose a photo.")
        return files.save(input, mimeType).fileName
    }

    /** Deletes every photo but [keep] (the one in Settings): old ones replaced, or left by a cut-short save. */
    fun keepOnly(keep: String?) {
        files.list().filter { it.name != keep }.forEach { files.delete(it.name) }
    }

    fun deleteAll() = files.deleteAll()
}
