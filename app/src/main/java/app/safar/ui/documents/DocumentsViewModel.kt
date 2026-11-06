package app.safar.ui.documents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.safar.data.BikeDocument
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** The papers list. Null until the first read. [today] is read on each change, so a list left open stays right. */
class DocumentsViewModel(
    documents: Flow<List<BikeDocument>>,
    today: () -> Long = { LocalDate.now().toEpochDay() },
) : ViewModel() {
    val rows: StateFlow<List<PaperRow>?> = documents.map { paperRows(it, today()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
