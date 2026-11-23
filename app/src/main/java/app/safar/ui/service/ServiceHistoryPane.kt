package app.safar.ui.service

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.safar.core.AppContainer
import app.safar.data.DocumentFiles
import app.safar.ui.components.AtlasToastState
import app.safar.ui.documents.openPaper
import app.safar.ui.documents.pickedSource
import app.safar.ui.vm.NoticeToasts
import kotlinx.coroutines.Dispatchers
import java.io.File

/** What the history part of the logbook can do. */
internal class HistoryActions(
    val onLogPast: (PastForm) -> Unit = {},
    val onEdit: (Long, PastForm) -> Unit = { _, _ -> },
    val onDelete: (Long) -> Unit = {},
    /** The sheet closed without saving. */
    val onDiscard: () -> Unit = {},
    val bills: (List<app.safar.data.BikeDocument>, BillDraftUi) -> SheetBills = { saved, draft -> SheetBills(saved, draft) },
)

/** The history part of the logbook page: its state, the open sheet's bills and its actions. */
internal class HistoryPane(
    val state: HistoryUiState = HistoryUiState(),
    val draft: BillDraftUi = BillDraftUi(),
    val actions: HistoryActions = HistoryActions(),
)

/** Builds the history pane: its ViewModel, the bill picker and opening a bill in another app. */
@Composable
internal fun rememberHistoryPane(toast: AtlasToastState): HistoryPane {
    val files = AppContainer.documentFiles
    val vm: ServiceHistoryViewModel = viewModel {
        ServiceHistoryViewModel(AppContainer.service, AppContainer.settings, AppContainer.documents, files, AppContainer.ride.state, Dispatchers.IO)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val draft by vm.bills.collectAsStateWithLifecycle()
    NoticeToasts(vm.notices, toast)
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.attachBill(pickedSource(context, uri))
    }
    val open = { fileName: String, mime: String ->
        if (!openPaper(context, files.file(fileName), mime)) toast.show("No app on this phone can show this file")
    }
    val actions = HistoryActions(
        onLogPast = vm::logPast, onEdit = vm::editRecord, onDelete = vm::deleteRecord, onDiscard = vm::discardBills,
        bills = { saved, d ->
            SheetBills(
                saved, d, file = { name -> File(files.dir, name) }, onAttach = { picker.launch(DocumentFiles.PICKER_TYPES) },
                onOpen = open, onRemoveSaved = vm::removeBill, onRemovePending = vm::removePendingBill,
            )
        },
    )
    return HistoryPane(state, draft, actions)
}

/**
 * The history sheets: log a past service ([logging]) or change record [editing]. Cancel and the scrim discard
 * what was picked in the sheet; Save keeps it.
 */
@Composable
internal fun HistorySheets(pane: HistoryPane, logging: Boolean, editing: Long?, onClose: () -> Unit) {
    val s = pane.state
    val a = pane.actions
    val dismiss = { a.onDiscard(); onClose() }
    PastServiceSheet(
        PastSheetSpec(logging, s.items, s.today, s.currency, s.lockReason, bills = a.bills(emptyList(), pane.draft)), dismiss,
        onSave = { form -> a.onLogPast(form); onClose() },
    )
    val record = s.records.firstOrNull { it.id == editing }
    PastServiceSheet(
        PastSheetSpec(record != null, s.items, s.today, s.currency, s.lockReason, record, a.bills(s.bills[editing].orEmpty(), pane.draft)),
        dismiss,
        onSave = { form -> record?.let { a.onEdit(it.id, form) }; onClose() },
        onDelete = { record?.let { a.onDelete(it.id) }; onClose() },
    )
}
