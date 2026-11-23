package app.safar.ui.service

import android.util.Log
import app.safar.data.BikeDocument
import app.safar.data.DocKind
import app.safar.data.DocumentFiles
import app.safar.data.DocumentRejected
import app.safar.data.DocumentRepository
import app.safar.ui.documents.PickedSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

private const val TAG = "ServiceBills"

/** A bill picked in the history sheet: its file is copied in, its paper is not saved yet. */
data class PendingBill(val fileName: String, val mimeType: String, val sizeBytes: Long, val displayName: String?)

/** What the history sheet shows about bills while it is open. */
data class BillDraftUi(
    val pending: List<PendingBill> = emptyList(),
    /** Papers already linked to the record that the rider took off; they go when the sheet is saved. */
    val removed: Set<Long> = emptySet(),
    /** A picked file is being copied in. */
    val adding: Boolean = false,
)

/**
 * Bills and receipts for one open history sheet. A picked file is copied into [files] at once (so the picker's
 * permission can lapse), and becomes a paper linked to the record only on [commit]. [discard] deletes the
 * copies of a sheet that was cancelled. Papers removed in the sheet are deleted on [commit] too.
 */
class BillDraft(
    private val files: DocumentFiles,
    private val documents: DocumentRepository,
    private val io: CoroutineDispatcher,
) {
    private val _ui = MutableStateFlow(BillDraftUi())
    val ui: StateFlow<BillDraftUi> = _ui.asStateFlow()

    /** Copies [source] in. Throws [DocumentRejected] (with words for the rider) for a file Safar won't keep. */
    suspend fun add(source: PickedSource) {
        _ui.update { it.copy(adding = true) }
        try {
            val stored = withContext(io) {
                val input = source.open() ?: throw DocumentRejected("Couldn’t open that file.")
                input.use { files.save(it, source.mimeType) }
            }
            val mime = source.mimeType.orEmpty()
            _ui.update { it.copy(pending = it.pending + PendingBill(stored.fileName, mime, stored.sizeBytes, source.displayName)) }
        } finally {
            _ui.update { it.copy(adding = false) }
        }
    }

    fun removeSaved(documentId: Long) = _ui.update { it.copy(removed = it.removed + documentId) }

    suspend fun removePending(fileName: String) {
        _ui.update { s -> s.copy(pending = s.pending.filterNot { it.fileName == fileName }) }
        withContext(io) { files.delete(fileName) }
    }

    /** Forgets the open sheet's changes: copies picked in it are deleted, removed papers stay. */
    suspend fun discard() {
        val copies = _ui.value.pending
        _ui.value = BillDraftUi()
        withContext(io) { copies.forEach { deleteQuietly(it.fileName) } }
    }

    /** Saves picked bills as papers linked to [recordId] and deletes the removed ones. */
    suspend fun commit(recordId: Long, label: String, nowMillis: Long) {
        val draft = _ui.value
        _ui.value = BillDraftUi()
        draft.pending.forEachIndexed { i, b ->
            val title = (b.displayName?.substringBeforeLast('.')?.trim()?.ifEmpty { null } ?: "$label bill").take(BILL_TITLE_MAX)
            documents.add(
                BikeDocument(
                    title = title, kind = DocKind.SERVICE_BILL, fileName = b.fileName, mimeType = b.mimeType,
                    sizeBytes = b.sizeBytes, addedMillis = nowMillis + i, serviceRecordId = recordId,
                ),
            )
        }
        draft.removed.forEach { id ->
            val doc = documents.get(id) ?: return@forEach
            documents.delete(id)
            withContext(io) { deleteQuietly(doc.fileName) }
        }
    }

    private fun deleteQuietly(fileName: String) {
        try {
            files.delete(fileName)
        } catch (e: java.io.IOException) {
            // The startup tidy-up removes it later (pruneDocuments).
            Log.w(TAG, "Could not delete bill file $fileName", e)
        }
    }

    private companion object {
        const val BILL_TITLE_MAX = 40
    }
}
