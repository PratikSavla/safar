package app.safar.data

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.UUID

/** A paper copied into [DocumentFiles]: its file name there and its size. */
data class StoredFile(val fileName: String, val sizeBytes: Long)

/** A paper Safar does not keep: [reason] is shown to the rider as it is. */
class DocumentRejected(val reason: String) : IOException(reason)

/**
 * The papers' files: `filesDir/documents/<uuid>.<ext>`, one per [BikeDocument]. The folder is left out of
 * Android backup on purpose (see `res/xml/data_extraction_rules.xml`); "Export everything" carries it. Pure
 * Java I/O, so it runs in unit tests. Call from the IO dispatcher.
 */
class DocumentFiles(val dir: File) {

    /** The file for [fileName]. Throws for a name this store would never have written (no path tricks). */
    fun file(fileName: String): File {
        require(isSafeName(fileName)) { "Not a document file name: $fileName" }
        return File(dir, fileName)
    }

    fun exists(fileName: String): Boolean = isSafeName(fileName) && File(dir, fileName).isFile

    /**
     * Copies [input] in as a new file of type [mimeType]. A paper over [MAX_BYTES], or not a PDF or an image,
     * is refused with [DocumentRejected] and nothing is left behind.
     */
    fun save(input: InputStream, mimeType: String?): StoredFile {
        val ext = extensionFor(mimeType) ?: throw DocumentRejected("Safar keeps PDFs and photos only.")
        ensureDir()
        val name = "${UUID.randomUUID()}.$ext"
        val temp = File(dir, "$name.part")
        try {
            val size = copyCapped(input, temp)
            if (size == 0L) throw DocumentRejected("That file is empty.")
            if (!temp.renameTo(File(dir, name))) throw IOException("Could not move ${temp.name} into place")
            return StoredFile(name, size)
        } finally {
            temp.delete()
        }
    }

    fun delete(fileName: String) {
        if (!isSafeName(fileName)) return
        val f = File(dir, fileName)
        if (f.exists() && !f.delete()) throw IOException("Could not delete document file $fileName")
    }

    /** Removes the folder and every paper in it. */
    fun deleteAll() {
        if (dir.exists() && !dir.deleteRecursively()) throw IOException("Could not delete ${dir.path}")
    }

    /** Files here (not half-copied ones) with their last change time. */
    fun list(): List<File> = dir.listFiles()?.filter { it.isFile && isSafeName(it.name) }.orEmpty()

    /**
     * Makes [staging] (a folder of papers read from an export) this store's folder. The old folder is moved
     * aside first and deleted once the swap worked; on failure it is put back.
     */
    fun replaceWith(staging: File) {
        val old = File(dir.parentFile, "${dir.name}-old")
        old.deleteRecursively()
        val hadDir = dir.exists()
        if (hadDir && !dir.renameTo(old)) throw IOException("Could not move ${dir.path} aside")
        if (!staging.renameTo(dir)) {
            if (hadDir) old.renameTo(dir)
            throw IOException("Could not move ${staging.path} into place")
        }
        old.deleteRecursively()
    }

    private fun ensureDir() {
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Could not create ${dir.path}")
    }

    private fun copyCapped(input: InputStream, to: File): Long {
        var total = 0L
        to.outputStream().use { out ->
            val buf = ByteArray(BUFFER)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > MAX_BYTES) throw DocumentRejected("That file is over ${MAX_BYTES / MB} MB. Try a smaller copy.")
                out.write(buf, 0, n)
            }
        }
        return total
    }

    companion object {
        /** Largest paper Safar keeps. */
        const val MAX_BYTES = 20L * 1024 * 1024
        private const val MB = 1024 * 1024
        private const val BUFFER = 64 * 1024

        /** MIME types the picker offers. */
        val PICKER_TYPES = arrayOf("application/pdf", "image/*")

        private val EXTENSIONS = mapOf(
            "application/pdf" to "pdf",
            "image/jpeg" to "jpg",
            "image/png" to "png",
            "image/webp" to "webp",
            "image/heic" to "heic",
            "image/heif" to "heif",
            "image/gif" to "gif",
        )

        private val SAFE_NAME = Regex("^[A-Za-z0-9-]{1,64}\\.(pdf|jpg|png|webp|heic|heif|gif)$")

        /** File extension for a paper of [mimeType], or null when Safar does not keep that type. */
        fun extensionFor(mimeType: String?): String? = mimeType?.lowercase(Locale.ROOT)?.let { EXTENSIONS[it] }

        /** True for names this store writes: `<uuid>.<ext>`, no folders. */
        fun isSafeName(name: String): Boolean = SAFE_NAME.matches(name)

        fun isPdf(mimeType: String): Boolean = mimeType.equals("application/pdf", ignoreCase = true)
    }
}
