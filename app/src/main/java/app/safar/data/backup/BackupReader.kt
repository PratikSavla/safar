package app.safar.data.backup

import app.safar.data.DocumentFiles
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.decodeFromStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/** An export Safar will not import: [message] is shown to the rider as it is. */
class BackupRejected(message: String) : IOException(message)

/** Caps that keep a damaged or hostile file from filling the phone. */
data class BackupLimits(
    val manifestBytes: Long = 1L * 1024 * 1024,
    val dataBytes: Long = 256L * 1024 * 1024,
    val documentBytes: Long = DocumentFiles.MAX_BYTES,
    val totalDocumentBytes: Long = 2L * 1024 * 1024 * 1024,
    val documentCount: Int = 2_000,
)

/**
 * An export read and checked in full, its papers copied to [stagingDir]. Nothing on the phone has changed yet.
 * [missingDocuments] papers were listed without their file and are left out.
 */
data class StagedBackup(val manifest: BackupManifest, val data: BackupData, val stagingDir: File, val missingDocuments: Int)

internal const val NOT_SAFAR = "This file isn’t a Safar export."
internal const val DAMAGED = "This export is damaged and can’t be read."
internal const val TOO_NEW = "This export is from a newer Safar. Update Safar, then try again."
internal const val TOO_BIG = "This export is larger than Safar can import."

/**
 * Reads an export from [input] into [stagingDir] (emptied first). Throws [BackupRejected] for a file that is
 * not a Safar export, is damaged, is from a newer format, or breaks [limits]; [stagingDir] is then removed.
 */
fun readBackup(input: InputStream, stagingDir: File, limits: BackupLimits = BackupLimits()): StagedBackup {
    stagingDir.deleteRecursively()
    if (!stagingDir.mkdirs()) throw IOException("Could not create ${stagingDir.path}")
    try {
        return ZipBackupReader(stagingDir, limits).read(input)
    } catch (e: Exception) {
        stagingDir.deleteRecursively()
        throw when (e) {
            is BackupRejected -> e
            is SerializationException, is IllegalArgumentException -> BackupRejected(DAMAGED).apply { initCause(e) }
            is ZipException -> BackupRejected(NOT_SAFAR).apply { initCause(e) }
            else -> e
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
private class ZipBackupReader(private val staging: File, private val limits: BackupLimits) {
    private var manifest: BackupManifest? = null
    private var data: BackupData? = null
    private val staged = mutableSetOf<String>()
    private var docBytes = 0L

    fun read(input: InputStream): StagedBackup {
        val zip = ZipInputStream(input.buffered())
        while (true) {
            val entry = zip.nextEntry ?: break
            if (!entry.isDirectory) readEntry(entry.name, zip)
            zip.closeEntry()
        }
        return finish()
    }

    private fun readEntry(name: String, zip: ZipInputStream) {
        if (name.contains("..") || name.startsWith("/") || name.contains('\\')) throw BackupRejected(DAMAGED)
        when {
            name == MANIFEST_ENTRY -> manifest = BackupJson.decodeFromStream(BackupManifest.serializer(), Capped(zip, limits.manifestBytes))
            name == DATA_ENTRY -> {
                checkManifest(manifest ?: throw BackupRejected(NOT_SAFAR))
                data = BackupJson.decodeFromStream(BackupData.serializer(), Capped(zip, limits.dataBytes))
            }
            name.startsWith(DOCUMENTS_PREFIX) -> stageDocument(name.removePrefix(DOCUMENTS_PREFIX), zip)
            else -> Unit // Something a newer Safar added: skipped.
        }
    }

    private fun stageDocument(fileName: String, zip: ZipInputStream) {
        if (!DocumentFiles.isSafeName(fileName)) throw BackupRejected(DAMAGED)
        if (staged.size >= limits.documentCount) throw BackupRejected(TOO_BIG)
        val target = File(staging, fileName)
        val copied = target.outputStream().use { Capped(zip, limits.documentBytes).copyTo(it) }
        docBytes += copied
        if (docBytes > limits.totalDocumentBytes) throw BackupRejected(TOO_BIG)
        staged += fileName
    }

    private fun finish(): StagedBackup {
        val m = manifest ?: throw BackupRejected(NOT_SAFAR)
        checkManifest(m)
        val d = data ?: throw BackupRejected(DAMAGED)
        checkUniqueIds(d)
        val (kept, missing) = d.documents.partition { it.fileName in staged }
        val used = kept.map { it.fileName }.toSet()
        staged.filter { it !in used }.forEach { File(staging, it).delete() }
        return StagedBackup(m, d.copy(documents = kept), staging, missing.size)
    }

    private fun checkManifest(m: BackupManifest) {
        if (m.format != BACKUP_FORMAT || m.formatVersion < 1) throw BackupRejected(NOT_SAFAR)
        if (m.formatVersion > BACKUP_FORMAT_VERSION) throw BackupRejected(TOO_NEW)
    }

    private fun checkUniqueIds(d: BackupData) {
        val lists = listOf(d.trips.map { it.id }, d.fuelUps.map { it.id }, d.serviceItems.map { it.id }, d.places.map { it.id }, d.documents.map { it.id })
        if (lists.any { ids -> ids.any { it <= 0 } || ids.toSet().size != ids.size }) throw BackupRejected(DAMAGED)
        if (d.documents.map { it.fileName }.toSet().size != d.documents.size) throw BackupRejected(DAMAGED)
    }
}

/** Reads at most [max] bytes of the current entry, then refuses; never closes the zip under it. */
private class Capped(input: InputStream, private val max: Long) : FilterInputStream(input) {
    private var count = 0L

    override fun read(): Int {
        val b = super.read()
        if (b >= 0) add(1)
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = super.read(b, off, len)
        if (n > 0) add(n.toLong())
        return n
    }

    override fun close() = Unit

    private fun add(n: Long) {
        count += n
        if (count > max) throw BackupRejected(TOO_BIG)
    }
}
