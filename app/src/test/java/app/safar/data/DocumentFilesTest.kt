package app.safar.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DocumentFilesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun store() = DocumentFiles(File(tmp.root, "documents"))

    @Test
    fun aPdfIsCopiedInUnderANewName() {
        val files = store()
        val stored = files.save(ByteArrayInputStream("%PDF-1.4".toByteArray()), "application/pdf")
        assertTrue(stored.fileName.endsWith(".pdf") && DocumentFiles.isSafeName(stored.fileName))
        assertEquals(8, stored.sizeBytes)
        assertEquals("%PDF-1.4", files.file(stored.fileName).readText())
    }

    @Test
    fun otherTypesEmptyAndOversizeFilesAreRefusedWithoutLeftovers() {
        val files = store()
        assertFailsWith<DocumentRejected> { files.save(ByteArrayInputStream(byteArrayOf(1)), "application/zip") }
        assertFailsWith<DocumentRejected> { files.save(ByteArrayInputStream(ByteArray(0)), "image/png") }
        val huge = object : InputStream() {
            var left = DocumentFiles.MAX_BYTES + 1
            override fun read(): Int = if (left-- > 0) 0 else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (left <= 0) return -1
                val n = minOf(len.toLong(), left).toInt()
                left -= n
                return n
            }
        }
        assertFailsWith<DocumentRejected> { files.save(huge, "image/jpeg") }
        assertEquals(emptyList(), files.dir.list()!!.toList())
    }

    @Test
    fun namesWithFoldersAreNeverUsed() {
        assertFalse(DocumentFiles.isSafeName("../x.pdf"))
        assertFalse(DocumentFiles.isSafeName("a/b.pdf"))
        assertFalse(DocumentFiles.isSafeName("x.exe"))
        assertFailsWith<IllegalArgumentException> { store().file("../settings.pdf") }
    }

    @Test
    fun replaceWithSwapsInTheStagedFolder() {
        val files = store()
        files.save(ByteArrayInputStream(byteArrayOf(1)), "image/png")
        val staging = tmp.newFolder("staging").also { File(it, "a.pdf").writeBytes(byteArrayOf(2)) }
        files.replaceWith(staging)
        assertEquals(listOf("a.pdf"), files.dir.list()!!.toList())
        assertFalse(staging.exists())
    }

    @Test
    fun pruneDropsRowsWithoutFilesAndOldStrayFiles() = runTest {
        val files = store()
        val kept = files.save(ByteArrayInputStream(byteArrayOf(1)), "image/png")
        val stray = files.save(ByteArrayInputStream(byteArrayOf(2)), "image/png")
        files.file(stray.fileName).setLastModified(0)
        val repo = ListDocuments(
            BikeDocument(1, "RC", DocKind.RC, kept.fileName, "image/png", 1, 0),
            BikeDocument(2, "PUC", DocKind.PUC, "0000-gone.pdf", "application/pdf", 1, 0),
        )
        val result = pruneDocuments(repo, files, nowMillis = 10L * 60 * 60 * 1000)
        assertEquals(PruneResult(rowsWithoutFile = 1, filesWithoutRow = 1), result)
        assertEquals(listOf(1L), repo.documents.value.map { it.id })
        assertEquals(listOf(kept.fileName), files.list().map { it.name })
    }

    private class ListDocuments(vararg docs: BikeDocument) : DocumentRepository {
        override val documents = MutableStateFlow(docs.toList())
        override suspend fun get(id: Long) = documents.value.firstOrNull { it.id == id }
        override suspend fun add(document: BikeDocument): Long = document.id.also { documents.value += document }
        override suspend fun update(document: BikeDocument) { documents.value = documents.value.map { if (it.id == document.id) document else it } }
        override suspend fun setRemindedStage(id: Long, stage: Int) = Unit
        override suspend fun delete(id: Long) { documents.value = documents.value.filter { it.id != id } }
    }
}
