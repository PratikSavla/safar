package app.safar.data.prefs

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import app.safar.data.SavedBikeRecord
import app.safar.data.SavedBikeStore
import app.safar.data.Settings
import app.safar.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.IOException

private const val TAG = "SafarData"

/** Preferences with read errors handled: an [IOException] is logged and read as empty preferences (defaults). */
internal fun Flow<Preferences>.orEmptyOnIoError(what: String): Flow<Preferences> = catch { e ->
    if (e !is IOException) throw e
    Log.w(TAG, "Reading $what failed; using defaults", e)
    emit(emptyPreferences())
}

/** Runs a DataStore edit, logging an [IOException] with context before rethrowing it. */
private suspend fun DataStore<Preferences>.editLogged(what: String, block: (MutablePreferences) -> Unit) {
    try {
        edit { block(it) }
    } catch (e: IOException) {
        Log.e(TAG, "Writing $what failed", e)
        throw e
    }
}

/** [SettingsStore] on a Preferences DataStore. [settings] holds [Settings] defaults until the file is read. */
class DataStoreSettingsStore(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) : SettingsStore {
    override val settings: StateFlow<Settings> = dataStore.data
        .orEmptyOnIoError("settings")
        .map(::readSettings)
        .stateIn(scope, SharingStarted.Eagerly, Settings())

    override suspend fun update(transform: (Settings) -> Settings) =
        dataStore.editLogged("settings") { prefs -> writeSettings(prefs, transform(readSettings(prefs))) }
}

/** [SavedBikeStore] on its own Preferences DataStore. Null until a bike is saved, or when either key is missing. */
class DataStoreSavedBikeStore(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) : SavedBikeStore {
    private val nameKey = stringPreferencesKey("name")
    private val addressKey = stringPreferencesKey("address")

    override val saved: StateFlow<SavedBikeRecord?> = dataStore.data
        .orEmptyOnIoError("saved bike")
        .map { prefs ->
            val name = prefs[nameKey]
            val address = prefs[addressKey]
            if (name != null && address != null) SavedBikeRecord(name, address) else null
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    override suspend fun save(record: SavedBikeRecord) = dataStore.editLogged("saved bike") {
        it[nameKey] = record.name
        it[addressKey] = record.address
    }

    override suspend fun clear() = dataStore.editLogged("saved bike") { it.clear() }
}
