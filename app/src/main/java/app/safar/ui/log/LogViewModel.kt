package app.safar.ui.log

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.safar.core.DebugLog
import app.safar.core.LogRecord
import app.safar.data.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "SafarLogScreen"

/** The share sheet: "Hide names" (on by default) and the file written with that choice. */
data class ExportSheet(val hideNames: Boolean, val busy: Boolean = true, val file: File? = null, val preview: ExportPreview? = null, val error: String? = null)

data class LogScreenState(
    val total: Int = 0,
    val matching: Int = 0,
    val rows: List<LogRow> = emptyList(),
    val filter: LogFilter = LogFilter(),
    val sessionActive: Boolean = false,
    val debugLogOn: Boolean = false,
    val selected: LogRecord? = null,
    val export: ExportSheet? = null,
)

private data class LogUi(val filter: LogFilter = LogFilter(), val selectedId: Long? = null, val export: ExportSheet? = null)

class LogViewModel(
    private val log: DebugLog,
    private val settings: SettingsStore,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    background: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val ui = MutableStateFlow(LogUi())
    private var exportJob: Job? = null

    val state: StateFlow<LogScreenState> = combine(log.records, log.sessionActive, settings.settings, ui) { records, session, s, u ->
        val list = filterRecords(records, u.filter)
        LogScreenState(
            total = records.size,
            matching = list.size,
            rows = list.take(MAX_LOG_ROWS).map { rowOf(it) },
            filter = u.filter,
            sessionActive = session,
            debugLogOn = s.debugLog,
            selected = u.selectedId?.let { id -> records.firstOrNull { it.id == id } },
            export = u.export,
        )
    }.flowOn(background).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LogScreenState())

    fun setDir(dir: DirFilter) = ui.update { it.copy(filter = it.filter.copy(dir = dir)) }

    fun setType(type: Char?) = ui.update { it.copy(filter = it.filter.copy(type = type)) }

    fun setErrorsOnly(on: Boolean) = ui.update { it.copy(filter = it.filter.copy(errorsOnly = on)) }

    fun open(id: Long) = ui.update { it.copy(selectedId = id) }

    fun closeDetail() = ui.update { it.copy(selectedId = null) }

    fun toggleSession() = guard("toggle the log session") { if (state.value.sessionActive) log.stopSession() else log.startSession() }

    fun setDebugLog(on: Boolean) {
        viewModelScope.launch {
            try {
                settings.update { it.copy(debugLog = on) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not save the debug log switch", e)
            }
        }
    }

    /** Opens the share sheet with the rider's default and writes the file. */
    fun openExport() = export(settings.settings.value.hideNames)

    fun setHideNames(hide: Boolean) = export(hide)

    fun closeExport() {
        exportJob?.cancel()
        ui.update { it.copy(export = null) }
    }

    private fun export(hide: Boolean) {
        exportJob?.cancel()
        ui.update { it.copy(export = ExportSheet(hideNames = hide)) }
        exportJob = viewModelScope.launch {
            val sheet = try {
                val file = log.export(hide)
                val preview = withContext(io) { file.useLines { previewOf(file.name, file.length(), it) } }
                ExportSheet(hide, busy = false, file = file, preview = preview)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // IOException from the file, or anything the log implementation throws.
                Log.e(TAG, "Could not write the log export (hideNames=$hide)", e)
                ExportSheet(hide, busy = false, error = "Could not write the log file")
            }
            ui.update { u -> if (u.export == null) u else u.copy(export = sheet) }
        }
    }

    private fun guard(what: String, block: () -> Unit) = try {
        block()
    } catch (e: RuntimeException) {
        Log.e(TAG, "Could not $what", e)
    }
}
