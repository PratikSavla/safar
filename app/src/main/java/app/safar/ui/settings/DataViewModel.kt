package app.safar.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.safar.core.RideState
import app.safar.data.backup.BackupCounts
import app.safar.data.backup.BackupRejected
import app.safar.data.backup.StagedBackup
import app.safar.data.backup.UserData
import app.safar.data.backup.counts
import app.safar.ui.vm.Notices
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "SafarDataVm"

enum class DataWork { NONE, EXPORTING, READING, RESTORING, WIPING }

/** An export read and checked, waiting for the rider to confirm the replace. */
data class ImportOffer(val counts: BackupCounts, val exportedAtMillis: Long, val missingDocuments: Int)

data class DataUiState(
    val work: DataWork = DataWork.NONE,
    val offer: ImportOffer? = null,
    /** Why export, import and delete are off right now (riding); null when they may run. */
    val locked: String? = null,
)

/**
 * Settings → Your data: export everything, import an export (replacing what's here), delete everything. All
 * three wait while a ride is recording, so nothing is pulled from under the trip recorder.
 */
class DataViewModel(
    private val userData: UserData,
    private val ride: StateFlow<RideState>,
    private val appVersion: String,
    /** Forgets the paired bike and disconnects (the BLE layer). */
    private val forgetBike: () -> Unit,
    /** Clears what lives outside [UserData]: the debug log, its files, notifications on screen. */
    private val afterWipe: suspend () -> Unit,
    private val today: () -> Long,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val base = MutableStateFlow(DataUiState())
    private var staged: StagedBackup? = null
    private val _wiped = MutableStateFlow(false)

    /** True once everything is deleted: the screen goes back to onboarding. */
    val wiped: StateFlow<Boolean> = _wiped.asStateFlow()
    val notices = Notices()

    val state: StateFlow<DataUiState> = combine(base, ride) { s, r -> s.copy(locked = lockReason(r)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DataUiState())

    /** Writes the export to the stream [open] gives (a file the rider chose). */
    fun export(open: () -> OutputStream?) = run(DataWork.EXPORTING, "export", "Couldn’t export. Try again.") {
        val counts = (open() ?: throw IllegalStateException("No stream for the chosen file")).use { userData.export(it, appVersion, now()) }
        notices.post("Exported ${exportSummary(counts)}")
    }

    /** Reads and checks an export; on success the confirm sheet opens ([DataUiState.offer]). */
    fun read(open: () -> InputStream?) = run(DataWork.READING, "import read", "Couldn’t read that file.") {
        val s = (open() ?: throw BackupRejected("Couldn’t open that file.")).use { userData.stage(it) }
        staged = s
        base.update { it.copy(offer = ImportOffer(s.data.counts(), s.manifest.exportedAtMillis, s.missingDocuments)) }
    }

    fun confirmImport() {
        val s = staged ?: return
        base.update { it.copy(offer = null) }
        run(DataWork.RESTORING, "import", "Couldn’t import. Your data is as it was.") {
            userData.restore(s, today())
            staged = null
            notices.post("Imported ${exportSummary(s.data.counts())}")
        }
    }

    fun cancelImport() {
        val s = staged ?: return
        staged = null
        base.update { it.copy(offer = null) }
        viewModelScope.launch {
            try {
                userData.discard(s)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Removing a cancelled import failed", e)
            }
        }
    }

    fun wipe() = run(DataWork.WIPING, "delete everything", "Couldn’t delete everything. Try again.") {
        forgetBike()
        userData.wipe()
        afterWipe()
        _wiped.value = true
    }

    override fun onCleared() {
        // The staging folder is emptied by the next import, or now if the screen goes away mid-offer.
        staged?.stagingDir?.deleteRecursively()
    }

    /** Runs one piece of [work] at a time; logs a failure with [what] and shows [failed] (or the file's reason). */
    private fun run(work: DataWork, what: String, failed: String, block: suspend () -> Unit) {
        if (base.value.work != DataWork.NONE || lockReason(ride.value) != null) return
        base.update { it.copy(work = work) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackupRejected) {
                Log.w(TAG, "$what refused: ${e.message}", e)
                notices.post(e.message ?: failed)
            } catch (e: Exception) {
                Log.e(TAG, "$what failed", e)
                notices.post(failed)
            } finally {
                base.update { it.copy(work = DataWork.NONE) }
            }
        }
    }
}

internal fun lockReason(r: RideState): String? =
    if (r.moving || r.recordingTripId != null) "Finish your ride first. Export, import and delete wait until the trip is saved." else null

/** "12 rides, 8 fuel-ups and 2 papers": only what the file holds. */
internal fun exportSummary(c: BackupCounts): String {
    val parts = listOfNotNull(
        plural(c.trips, "ride"), plural(c.fuelUps, "fuel-up"), plural(c.serviceItems, "service item"),
        plural(c.places, "place"), plural(c.documents, "paper"),
    )
    return when (parts.size) {
        0 -> "your settings"
        1 -> parts[0]
        else -> parts.dropLast(1).joinToString(", ") + " and " + parts.last()
    }
}

private fun plural(n: Int, word: String): String? = when (n) {
    0 -> null
    1 -> "1 $word"
    else -> "$n ${word}s"
}
