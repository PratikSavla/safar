package app.safar.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import app.safar.core.AppContainer
import app.safar.data.db.RoomFuelRepository
import app.safar.data.db.RoomParkedRepository
import app.safar.data.db.RoomPlaceRepository
import app.safar.data.db.RoomServiceRepository
import app.safar.data.db.RoomTripRepository
import app.safar.data.db.SafarDatabase
import app.safar.data.prefs.DataStoreSavedBikeStore
import app.safar.data.prefs.DataStoreSettingsStore
import java.util.concurrent.atomic.AtomicBoolean

/** Wires the data layer into [AppContainer]. Call once from `SafarApp.onCreate`. */
object DataModule {
    private const val SETTINGS_FILE = "settings"
    private const val SAVED_BIKE_FILE = "saved_bike"

    private val installed = AtomicBoolean(false)

    /** Idempotent: a second call is ignored, because two DataStores on one file would fail at runtime. */
    fun install(context: Context) {
        if (!installed.compareAndSet(false, true)) return
        val app = context.applicationContext
        val scope = AppContainer.appScope
        // One DataStore per file for the whole process.
        val settingsData = PreferenceDataStoreFactory.create(scope = scope) { app.preferencesDataStoreFile(SETTINGS_FILE) }
        val bikeData = PreferenceDataStoreFactory.create(scope = scope) { app.preferencesDataStoreFile(SAVED_BIKE_FILE) }
        AppContainer.settings = DataStoreSettingsStore(settingsData, scope)
        AppContainer.savedBike = DataStoreSavedBikeStore(bikeData, scope)

        val db = SafarDatabase.get(app)
        AppContainer.fuel = RoomFuelRepository(db.fuelUps())
        AppContainer.trips = RoomTripRepository(db.trips())
        AppContainer.service = RoomServiceRepository(db.serviceItems())
        AppContainer.places = RoomPlaceRepository(db.places())
        AppContainer.parked = RoomParkedRepository(db.parkedSpot())
    }
}
