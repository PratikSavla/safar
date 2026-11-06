package app.safar.data.backup

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.encodeToStream
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes an export to [out]: manifest.json first (so a reader learns the version before the rest), then
 * data.json, then each paper as documents/<fileName> read from [documentFile]. Streams; nothing is held whole
 * in memory. [out] is finished but not closed: the caller owns it.
 */
@OptIn(ExperimentalSerializationApi::class)
fun writeBackup(out: OutputStream, manifest: BackupManifest, data: BackupData, documentFile: (String) -> File) {
    val zip = ZipOutputStream(out.buffered())
    zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
    BackupJson.encodeToStream(BackupManifest.serializer(), manifest, zip)
    zip.closeEntry()
    zip.putNextEntry(ZipEntry(DATA_ENTRY))
    BackupJson.encodeToStream(BackupData.serializer(), data, zip)
    zip.closeEntry()
    for (doc in data.documents) {
        zip.putNextEntry(ZipEntry(DOCUMENTS_PREFIX + doc.fileName))
        documentFile(doc.fileName).inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }
    zip.finish()
    zip.flush()
}
