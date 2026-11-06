package app.safar.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.preferencesDataStoreFile
import app.safar.core.AppContainer
import app.safar.data.backup.UserDataManager
import app.safar.data.db.RoomDocumentRepository
import app.safar.data.db.RoomFuelRepository
import app.safar.data.db.RoomParkedRepository
import app.safar.data.db.RoomPlaceRepository
import app.safar.data.db.RoomServiceRepository
import app.safar.data.db.RoomTripRepository
import app.safar.data.db.SafarDatabase
import app.safar.data.prefs.DataStoreOneTimeFlags
import app.safar.data.prefs.DataStoreSavedBikeStore
import app.safar.data.prefs.DataStoreSettingsStore
import app.safar.data.prefs.createPreferencesStore
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Wires the data layer into [AppContainer]. Call once from `SafarApp.onCreate`. */
object DataModule {
    private const val TAG = "SafarData"
    private const val SETTINGS_FILE = "settings"
    private const val SAVED_BIKE_FILE = "saved_bike"
    /** Papers' files. Not listed in the backup rules, so Android backup leaves them out. */
    private const val DOCUMENTS_DIR = "documents"

    private val installed = AtomicBoolean(false)

    /** Idempotent: a second call is ignored, because two DataStores on one file would fail at runtime. */
    fun install(context: Context) {
        if (!installed.compareAndSet(false, true)) return
        val app = context.applicationContext
        val scope = AppContainer.appScope
        // File reads and fsync-ed writes run on the IO pool, not the CPU pool BLE work shares; same parent job.
        val ioScope = CoroutineScope(scope.coroutineContext + Dispatchers.IO)
        // One DataStore per file for the whole process.
        val settingsData = createPreferencesStore("settings", ioScope) { app.preferencesDataStoreFile(SETTINGS_FILE) }
        val bikeData = createPreferencesStore("saved bike", ioScope) { app.preferencesDataStoreFile(SAVED_BIKE_FILE) }
        AppContainer.settings = DataStoreSettingsStore(settingsData, scope)
        AppContainer.savedBike = DataStoreSavedBikeStore(bikeData, scope)

        val db = SafarDatabase.get(app)
        AppContainer.fuel = RoomFuelRepository(db.fuelUps())
        val trips = RoomTripRepository(db.trips())
        AppContainer.trips = trips
        AppContainer.service = RoomServiceRepository(db.serviceItems())
        AppContainer.places = RoomPlaceRepository(db.places())
        AppContainer.parked = RoomParkedRepository(db.parkedSpot())
        val documents = RoomDocumentRepository(db.documents())
        val files = DocumentFiles(File(app.filesDir, DOCUMENTS_DIR))
        AppContainer.documents = documents
        AppContainer.documentFiles = files
        AppContainer.userData = UserDataManager(
            dao = db.backup(),
            settings = AppContainer.settings,
            savedBike = AppContainer.savedBike,
            files = files,
            workDir = app.filesDir,
            io = Dispatchers.IO,
        )
        scope.launch { thinOldTracks(trips, DataStoreOneTimeFlags(settingsData)) }
        ioScope.launch { pruneOrphans(documents, files) }
    }

    /** Drops papers whose file is gone (restored from backup without it) and files left by a cut-short save. */
    private suspend fun pruneOrphans(repo: DocumentRepository, files: DocumentFiles) {
        try {
            val r = pruneDocuments(repo, files, System.currentTimeMillis())
            if (r.rowsWithoutFile + r.filesWithoutRow > 0) {
                Log.i(TAG, "Papers tidied: ${r.rowsWithoutFile} without a file, ${r.filesWithoutRow} stray files")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Tidying the papers failed; it runs again on the next start", e)
        }
    }

    /** Thins tracks of trips saved before trips were thinned at their end (keeps backups under 25 MB). */
    private suspend fun thinOldTracks(trips: TripRepository, flags: OneTimeFlags) {
        try {
            val removed = thinFinishedTracks(trips, flags) ?: return
            Log.i(TAG, "Thinned the tracks of saved trips: $removed points removed")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Thinning the tracks of saved trips failed; it runs again on the next start", e)
        }
    }
}
