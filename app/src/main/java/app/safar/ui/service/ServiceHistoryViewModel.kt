package app.safar.ui.service

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.safar.core.RideState
import app.safar.data.BikeDocument
import app.safar.data.DocumentFiles
import app.safar.data.DocumentRejected
import app.safar.data.DocumentRepository
import app.safar.data.ServiceItem
import app.safar.data.ServiceRecord
import app.safar.data.ServiceRepository
import app.safar.data.SettingsStore
import app.safar.ui.documents.PickedSource
import app.safar.ui.format.LockText
import app.safar.ui.vm.Notices
import app.safar.ui.vm.launchLogged
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val TAG = "ServiceHistoryVM"

/** The service history and what its sheet needs: the items to choose from and each record's bills. */
data class HistoryUiState(
    val rows: List<HistoryRowUi> = emptyList(),
    val records: List<ServiceRecord> = emptyList(),
    val items: List<ServiceItem> = emptyList(),
    /** Bills and receipts by service record id. */
    val bills: Map<Long, List<BikeDocument>> = emptyMap(),
    val currency: String = "₹",
    val today: LocalDate = LocalDate.MIN,
    val lockReason: String? = null,
)

/** Services done: log one done earlier, change or remove a line, and keep its bills (as papers). */
class ServiceHistoryViewModel(
    private val service: ServiceRepository,
    settings: SettingsStore,
    documents: DocumentRepository,
    files: DocumentFiles,
    ride: StateFlow<RideState>,
    io: CoroutineDispatcher,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    private val draft = BillDraft(files, documents, io)

    /** Bills picked or removed in the open sheet. */
    val bills: StateFlow<BillDraftUi> = draft.ui
    val notices = Notices()

    val state: StateFlow<HistoryUiState> = combine(service.items, service.records, documents.documents, settings.settings, ride) { items, records, docs, s, r ->
        val bills = docs.filter { it.serviceRecordId != null }.groupBy { it.serviceRecordId ?: 0L }
        HistoryUiState(
            rows = historyRows(records, s.currencySymbol, zone, bills.mapValues { it.value.size }),
            records = records, items = items, bills = bills, currency = s.currencySymbol,
            today = Instant.ofEpochMilli(now()).atZone(zone).toLocalDate(),
            lockReason = LockText.whileMoving(r.moving, r.speedKmh),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    /** Adds a service done earlier, with the bills picked for it; the item counts from it when it is newer. */
    fun logPast(form: PastForm) {
        val s = state.value
        if (s.lockReason != null) return
        val record = pastRecord(form, s.items, s.today, zone) ?: return
        val item = s.items.first { it.id == record.itemId }
        val moved = itemAfterPast(item, record)
        launchLogged(TAG, "log of a past service for item ${item.id}", { notices.post("Couldn’t log the service. Try again.") }) {
            val id = service.logPast(record, moved)
            draft.commit(id, record.label, now())
            notices.post(pastLoggedText(record, moved != null, zone))
        }
    }

    /** Saves the changes to record [id], its bills included, and moves the reminders that counted from it. */
    fun editRecord(id: Long, form: PastForm) {
        val s = state.value
        if (s.lockReason != null) return
        val original = s.records.firstOrNull { it.id == id } ?: return
        val edited = editedRecord(form, s.items, s.today, zone, original) ?: return
        val moved = itemsAfterEdit(s.items, s.records, original, edited)
        launchLogged(TAG, "edit of service record $id", { notices.post("Couldn’t save the changes. Try again.") }) {
            service.updateRecord(edited, moved)
            draft.commit(id, edited.label, now())
            notices.post(if (moved.isEmpty()) "${edited.label} changed" else "${edited.label} changed. Reminder updated.")
        }
    }

    /** Removes record [id] from the history. Its bills stay in Papers. */
    fun deleteRecord(id: Long) {
        val row = state.value.rows.firstOrNull { it.id == id } ?: return
        launchLogged(TAG, "delete of service record $id", { notices.post("Couldn’t remove it. Try again.") }) {
            draft.discard()
            service.deleteRecord(id)
            notices.post("${row.label} on ${row.date} removed from the history")
        }
    }

    fun attachBill(source: PickedSource) {
        launchLogged(TAG, "attaching a service bill", { notices.post("Couldn’t add that file. Try again.") }) {
            try {
                draft.add(source)
            } catch (e: DocumentRejected) {
                notices.post(e.reason)
            }
        }
    }

    fun removeBill(documentId: Long) = draft.removeSaved(documentId)

    fun removePendingBill(fileName: String) {
        launchLogged(TAG, "removing a picked bill", { notices.post("Couldn’t remove that file. Try again.") }) { draft.removePending(fileName) }
    }

    /** The sheet closed without saving: forget what was picked or removed in it. */
    fun discardBills() {
        viewModelScope.launch { draft.discard() }
    }
}
