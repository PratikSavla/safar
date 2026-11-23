package app.safar.ui.service

import app.safar.data.BikeDocument
import app.safar.data.DocKind
import app.safar.data.DocumentFiles
import app.safar.data.DocumentRejected
import app.safar.data.DocumentRepository
import app.safar.ui.documents.PickedSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BillDraftTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private class Docs : DocumentRepository {
        val list = MutableStateFlow<List<BikeDocument>>(emptyList())
        override val documents: Flow<List<BikeDocument>> = list
        override suspend fun get(id: Long) = list.value.firstOrNull { it.id == id }
        override suspend fun add(document: BikeDocument): Long {
            val id = (list.value.maxOfOrNull { it.id } ?: 0) + 1
            list.value = list.value + document.copy(id = id)
            return id
        }
        override suspend fun update(document: BikeDocument) = Unit
        override suspend fun setRemindedStage(id: Long, stage: Int) = Unit
        override suspend fun delete(id: Long) { list.value = list.value.filterNot { it.id == id } }
    }

    private fun source(name: String, mime: String) = PickedSource(mime, name) { ByteArrayInputStream(byteArrayOf(1, 2, 3)) }

    @Test
    fun `picked bills become papers linked to the record on commit`() = runTest {
        val files = DocumentFiles(File(tmp.root, "documents"))
        val docs = Docs()
        val draft = BillDraft(files, docs, Dispatchers.Unconfined)
        draft.add(source("oil-receipt.jpg", "image/jpeg"))
        draft.add(source("invoice.pdf", "application/pdf"))
        assertEquals(2, draft.ui.value.pending.size)
        draft.commit(recordId = 7, label = "Engine oil", nowMillis = 1_000)
        val saved = docs.list.value
        assertEquals(listOf("oil-receipt", "invoice"), saved.map { it.title })
        assertTrue(saved.all { it.serviceRecordId == 7L && it.kind == DocKind.SERVICE_BILL && files.exists(it.fileName) })
        assertEquals(BillDraftUi(), draft.ui.value)
    }

    @Test
    fun `a cancelled sheet deletes its copies, a removed paper goes on commit`() = runTest {
        val files = DocumentFiles(File(tmp.root, "documents"))
        val docs = Docs()
        val draft = BillDraft(files, docs, Dispatchers.Unconfined)
        draft.add(source("a.jpg", "image/jpeg"))
        val copy = draft.ui.value.pending.single().fileName
        draft.discard()
        assertFalse(files.exists(copy))
        draft.add(source("b.jpg", "image/jpeg"))
        draft.commit(3, "Chain", 1)
        val paper = docs.list.value.single()
        draft.removeSaved(paper.id)
        draft.commit(3, "Chain", 2)
        assertTrue(docs.list.value.isEmpty())
        assertFalse(files.exists(paper.fileName))
    }

    @Test
    fun `a file Safar does not keep is refused and nothing is pending`() = runTest {
        val draft = BillDraft(DocumentFiles(File(tmp.root, "documents")), Docs(), Dispatchers.Unconfined)
        assertFailsWith<DocumentRejected> { draft.add(source("notes.txt", "text/plain")) }
        assertEquals(BillDraftUi(), draft.ui.value)
    }
}
