package app.safar.ui.documents

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.safar.core.RideState
import app.safar.data.BikeDocument
import app.safar.data.DocKind
import app.safar.data.DocumentFiles
import app.safar.data.DocumentRejected
import app.safar.data.DocumentRepository
import app.safar.data.label
import app.safar.data.reminderStage
import app.safar.ui.format.InputLock
import app.safar.ui.vm.Notices
import app.safar.ui.vm.launchLogged
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "PaperViewModel"
const val PAPER_TITLE_MAX = 40

/** The paper's file in the form: the saved one, or one just picked ([pending], deleted if the form is left). */
data class FormFile(val fileName: String, val mimeType: String, val sizeBytes: Long, val displayName: String?, val pending: Boolean)

data class PaperForm(val kind: DocKind = DocKind.RC, val title: String = "", val expiry: Long? = null, val note: String = "")

data class PaperUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val isNew: Boolean = true,
    val form: PaperForm = PaperForm(),
    val file: FormFile? = null,
    val reading: Boolean = false,
    val saving: Boolean = false,
    val locked: String? = null,
) {
    val canSave: Boolean get() = file != null && !reading && !saving && locked == null
}

/** Add or edit one paper. [docId] null = new. Locked while the phone moves (UX rule 3). */
class DocumentViewModel(
    private val docId: Long?,
    private val repo: DocumentRepository,
    private val files: DocumentFiles,
    private val ride: StateFlow<RideState>,
    private val io: CoroutineDispatcher,
    /** Outlives the screen: deletes a picked file the rider never saved. */
    private val cleanupScope: CoroutineScope,
    private val today: () -> Long,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val base = MutableStateFlow(PaperUiState())
    private var original: BikeDocument? = null
    private val _done = MutableStateFlow<String?>(null)

    /** Set once the paper is saved or deleted: the toast for the list. */
    val done: StateFlow<String?> = _done.asStateFlow()
    val notices = Notices()

    val state: StateFlow<PaperUiState> = combine(base, ride, ::withLock)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PaperUiState())

    /** The state now, read from the sources rather than [state], which only runs while the screen watches. */
    private fun current(): PaperUiState = withLock(base.value, ride.value)

    private fun withLock(s: PaperUiState, r: RideState) = s.copy(locked = InputLock.reason(r, "Stop to edit your papers."))

    init {
        viewModelScope.launch {
            val doc = docId?.let { repo.get(it) }
            original = doc
            base.value = when {
                docId == null -> PaperUiState(loading = false)
                doc == null -> PaperUiState(loading = false, missing = true)
                else -> PaperUiState(
                    loading = false,
                    isNew = false,
                    form = PaperForm(doc.kind, doc.title, doc.expiryEpochDay, doc.note.orEmpty()),
                    file = FormFile(doc.fileName, doc.mimeType, doc.sizeBytes, null, pending = false),
                )
            }
        }
    }

    fun setKind(kind: DocKind) = base.update { it.copy(form = it.form.copy(kind = kind)) }
    fun setTitle(text: String) = base.update { it.copy(form = it.form.copy(title = text.take(PAPER_TITLE_MAX))) }
    fun setNote(text: String) = base.update { it.copy(form = it.form.copy(note = text)) }
    fun setExpiry(epochDay: Long?) = base.update { it.copy(form = it.form.copy(expiry = epochDay)) }

    /** Copies the picked file in. A new pick replaces an earlier one not yet saved. */
    fun pick(source: PickedSource) {
        if (current().locked != null || base.value.reading) return
        base.update { it.copy(reading = true) }
        viewModelScope.launch {
            try {
                val stored = withContext(io) {
                    val input = source.open() ?: throw DocumentRejected("Couldn’t open that file.")
                    input.use { files.save(it, source.mimeType) }
                }
                val mime = source.mimeType?.lowercase().orEmpty()
                val previous = base.value.file
                base.update { it.copy(reading = false, file = FormFile(stored.fileName, mime, stored.sizeBytes, source.displayName, pending = true)) }
                if (previous?.pending == true) withContext(io) { files.delete(previous.fileName) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: DocumentRejected) {
                base.update { it.copy(reading = false) }
                notices.post(e.reason)
            } catch (e: Exception) {
                Log.e(TAG, "Copying the picked paper failed", e)
                base.update { it.copy(reading = false) }
                notices.post("Couldn’t add that file. Try again.")
            }
        }
    }

    fun save() {
        val s = current()
        val file = s.file ?: return
        if (!s.canSave) return
        base.update { it.copy(saving = true) }
        val f = s.form
        val title = f.title.trim().ifEmpty { f.kind.label() }
        val note = f.note.trim().ifEmpty { null }
        val old = original
        launchLogged(TAG, "save of paper ${old?.id ?: "new"}", { base.update { it.copy(saving = false) }; notices.post("Couldn’t save the paper. Try again.") }) {
            val stage = if (old != null && old.expiryEpochDay == f.expiry) old.remindedStage else reminderStage(today(), f.expiry)
            val doc = BikeDocument(
                id = old?.id ?: 0, title = title, kind = f.kind, fileName = file.fileName, mimeType = file.mimeType,
                sizeBytes = file.sizeBytes, addedMillis = old?.addedMillis ?: now(), expiryEpochDay = f.expiry, note = note,
                remindedStage = stage,
            )
            if (old == null) repo.add(doc) else repo.update(doc)
            base.update { it.copy(file = file.copy(pending = false)) }
            if (old != null && old.fileName != file.fileName) withContext(io) { files.delete(old.fileName) }
            _done.value = if (old == null) "Paper saved" else "Changes saved"
        }
    }

    fun delete() {
        val doc = original ?: return
        if (base.value.saving) return
        base.update { it.copy(saving = true) }
        launchLogged(TAG, "delete of paper ${doc.id}", { base.update { it.copy(saving = false) }; notices.post("Couldn’t delete the paper. Try again.") }) {
            repo.delete(doc.id)
            withContext(io) { files.delete(doc.fileName) }
            _done.value = "Paper deleted"
        }
    }

    override fun onCleared() {
        val left = base.value.file?.takeIf { it.pending } ?: return
        cleanupScope.launch(io) {
            try {
                files.delete(left.fileName)
            } catch (e: Exception) {
                Log.w(TAG, "Removing an unsaved paper failed; it is tidied on the next start", e)
            }
        }
    }
}
