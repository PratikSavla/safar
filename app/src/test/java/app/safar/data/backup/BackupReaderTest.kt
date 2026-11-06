package app.safar.data.backup

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class BackupReaderTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val okManifest = """{"format":"safar-backup","formatVersion":1}"""

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { z -> entries.forEach { (name, bytes) -> z.putNextEntry(ZipEntry(name)); z.write(bytes); z.closeEntry() } }
    }.toByteArray()

    private fun read(bytes: ByteArray, limits: BackupLimits = BackupLimits()): StagedBackup =
        readBackup(ByteArrayInputStream(bytes), staging(), limits)

    private fun staging(): File = File(tmp.root, "staging")

    private fun rejected(bytes: ByteArray, limits: BackupLimits = BackupLimits()): String {
        val e = assertFailsWith<BackupRejected> { read(bytes, limits) }
        assertFalse(staging().exists(), "staging is removed after a refusal")
        return e.message!!
    }

    @Test
    fun aZipWithoutAManifestIsNotAnExport() {
        assertEquals(NOT_SAFAR, rejected(zip("notes.txt" to "hello".toByteArray())))
    }

    @Test
    fun aFileThatIsNotAZipIsNotAnExport() {
        assertEquals(NOT_SAFAR, rejected("just some text".toByteArray()))
    }

    @Test
    fun anotherAppsManifestIsNotAnExport() {
        assertEquals(NOT_SAFAR, rejected(zip(MANIFEST_ENTRY to """{"format":"other","formatVersion":1}""".toByteArray(), DATA_ENTRY to "{}".toByteArray())))
    }

    @Test
    fun aNewerFormatAsksForAnUpdate() {
        val newer = """{"format":"safar-backup","formatVersion":${BACKUP_FORMAT_VERSION + 1}}"""
        assertEquals(TOO_NEW, rejected(zip(MANIFEST_ENTRY to newer.toByteArray(), DATA_ENTRY to "{}".toByteArray())))
    }

    @Test
    fun aPathOutsideTheZipIsRefused() {
        val evil = zip(MANIFEST_ENTRY to okManifest.toByteArray(), DATA_ENTRY to "{}".toByteArray(), "documents/../../evil.pdf" to byteArrayOf(1))
        assertEquals(DAMAGED, rejected(evil))
        assertFalse(File(tmp.root.parentFile, "evil.pdf").exists())
    }

    @Test
    fun aDocumentNameSafarNeverWritesIsRefused() {
        assertEquals(DAMAGED, rejected(zip(MANIFEST_ENTRY to okManifest.toByteArray(), DATA_ENTRY to "{}".toByteArray(), "documents/run.sh" to byteArrayOf(1))))
    }

    @Test
    fun anEntryOverItsCapIsRefused() {
        val big = zip(MANIFEST_ENTRY to okManifest.toByteArray(), DATA_ENTRY to "{}".toByteArray(), "documents/$PDF_NAME" to ByteArray(2_000))
        assertEquals(TOO_BIG, rejected(big, BackupLimits(documentBytes = 1_000)))
    }

    @Test
    fun brokenJsonIsDamaged() {
        assertEquals(DAMAGED, rejected(zip(MANIFEST_ENTRY to okManifest.toByteArray(), DATA_ENTRY to "{\"trips\": [".toByteArray())))
    }

    @Test
    fun duplicateIdsAreDamaged() {
        val data = """{"trips":[{"id":1},{"id":1}]}"""
        assertEquals(DAMAGED, rejected(zip(MANIFEST_ENTRY to okManifest.toByteArray(), DATA_ENTRY to data.toByteArray())))
    }

    @Test
    fun missingAndUnknownFieldsStillRead() {
        val data = """{"trips":[{"id":3,"title":"Ride","futureField":true}],"somethingNew":{"a":1},"settings":{"riderName":"Alex"}}"""
        val staged = read(zip(MANIFEST_ENTRY to """{"format":"safar-backup","formatVersion":1,"extra":"x"}""".toByteArray(), DATA_ENTRY to data.toByteArray(), "later/thing.bin" to byteArrayOf(1)))
        assertEquals(3, staged.data.trips.single().id)
        assertEquals(0.0, staged.data.trips.single().distanceKm)
        assertEquals("Alex", staged.data.settings?.riderName)
        assertNull(staged.data.settings?.theme)
    }

    @Test
    fun aPaperWithoutItsFileIsLeftOutAndCounted() {
        val data = """{"documents":[{"id":1,"title":"PUC","fileName":"$PDF_NAME","mimeType":"application/pdf"},{"id":2,"title":"RC","fileName":"$JPG_NAME","mimeType":"image/jpeg"}]}"""
        val staged = read(zip(MANIFEST_ENTRY to okManifest.toByteArray(), DATA_ENTRY to data.toByteArray(), "documents/$JPG_NAME" to byteArrayOf(1, 2)))
        assertEquals(listOf("RC"), staged.data.documents.map { it.title })
        assertEquals(1, staged.missingDocuments)
    }

    @Test
    fun aStrayFileNoPaperPointsToIsNotKept() {
        val staged = read(zip(MANIFEST_ENTRY to okManifest.toByteArray(), DATA_ENTRY to "{}".toByteArray(), "documents/$PDF_NAME" to byteArrayOf(1)))
        assertEquals(emptyList(), staged.stagingDir.list()!!.toList())
    }
}
