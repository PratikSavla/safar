package app.safar.data.backup

import android.util.Log
import app.safar.data.DocumentFiles
import app.safar.data.SavedBikeStore
import app.safar.data.Settings
import app.safar.data.SettingsStore
import app.safar.data.db.BackupDao
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** Export, import and "Delete everything": the rider's whole data at once. */
interface UserData {
    /** Writes everything to [out] (an export zip). Returns what it holds. */
    suspend fun export(out: OutputStream, appVersion: String, nowMillis: Long): BackupCounts

    /** Reads and checks an export without changing anything. Throws [BackupRejected] for a file Safar won't take. */
    suspend fun stage(input: InputStream): StagedBackup

    /** Replaces everything on the phone with [staged]. The phone's paired bike stays as it is. */
    suspend fun restore(staged: StagedBackup, todayEpochDay: Long)

    /** Throws away a staged import the rider cancelled. */
    suspend fun discard(staged: StagedBackup)

    /** Deletes every trip, fuel-up, service item, place, parked spot, paper and setting (back to onboarding). */
    suspend fun wipe()
}

/**
 * [UserData] over the database ([dao]), settings, the saved bike and the papers' [files]. File work runs on
 * [io]; imports are staged in [workDir]/import-staging first. Android-free apart from logging.
 */
class UserDataManager(
    private val dao: BackupDao,
    private val settings: SettingsStore,
    private val savedBike: SavedBikeStore,
    private val files: DocumentFiles,
    private val workDir: File,
    private val io: CoroutineDispatcher,
) : UserData {

    override suspend fun export(out: OutputStream, appVersion: String, nowMillis: Long): BackupCounts = coroutineScope {
        val rows = async { dao.snapshot() }
        val bike = async { savedBike.saved.value }
        val snapshot = UserSnapshot(rows.await(), settings.settings.value, bike.await())
        withContext(io) {
            // A paper whose file went missing is left out rather than failing the whole export.
            val present = snapshot.rows.documents.filter { files.exists(it.fileName) }
            val data = snapshot.copy(rows = snapshot.rows.copy(documents = present)).toBackupData()
            val counts = data.counts()
            val manifest = BackupManifest(BACKUP_FORMAT, BACKUP_FORMAT_VERSION, appVersion, nowMillis, counts)
            writeBackup(out, manifest, data, files::file)
            counts
        }
    }

    override suspend fun stage(input: InputStream): StagedBackup = withContext(io) { readBackup(input, File(workDir, STAGING_DIR)) }

    override suspend fun restore(staged: StagedBackup, todayEpochDay: Long) {
        val rows = staged.data.toRows(todayEpochDay)
        dao.replaceAll(rows)
        withContext(io) {
            try {
                files.replaceWith(staged.stagingDir)
            } catch (e: Exception) {
                // The rows are in; papers without a file are tidied on the next start.
                Log.e(TAG, "Moving imported papers into place failed", e)
                staged.stagingDir.deleteRecursively()
                throw e
            }
        }
        val imported = staged.data.settings
        if (imported != null) settings.update { current -> imported.applyTo(Settings(onboarded = current.onboarded, dismissedPermissions = current.dismissedPermissions)) }
    }

    override suspend fun discard(staged: StagedBackup) {
        withContext(io) { staged.stagingDir.deleteRecursively() }
    }

    override suspend fun wipe() = coroutineScope {
        val db = async { dao.clearAll() }
        val papers = async(io) { files.deleteAll() }
        val prefs = async { settings.update { Settings() } }
        val bike = async { savedBike.clear() }
        db.await()
        papers.await()
        prefs.await()
        bike.await()
    }

    private companion object {
        const val TAG = "SafarUserData"
        const val STAGING_DIR = "import-staging"
    }
}
