package app.safar.data.prefs

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import app.safar.data.OneTimeFlags
import app.safar.data.SavedBikeRecord
import app.safar.data.SavedBikeStore
import app.safar.data.Settings
import app.safar.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

private const val TAG = "SafarData"

internal const val READ_RETRY_FIRST_MS = 500L
internal const val READ_RETRY_MAX_MS = 30_000L

/**
 * A Preferences DataStore whose corrupt file is replaced by empty preferences (defaults) instead of failing
 * every read and write until the rider clears app data.
 */
fun createPreferencesStore(what: String, scope: CoroutineScope, produceFile: () -> File): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { e ->
            Log.w(TAG, "Stored $what is corrupt; resetting it to defaults", e)
            emptyPreferences()
        },
        scope = scope,
        produceFile = produceFile,
    )

/**
 * Preferences that survive read errors: an [IOException] is logged and the read is retried with a doubling
 * delay (capped at [maxDelayMs]), so the flow never ends. Holders keep their last value meanwhile; a
 * [StateFlow] built on it starts at defaults. Other exceptions propagate.
 */
internal fun Flow<Preferences>.retryOnIoError(
    what: String,
    firstDelayMs: Long = READ_RETRY_FIRST_MS,
    maxDelayMs: Long = READ_RETRY_MAX_MS,
): Flow<Preferences> = retryWhen { e, attempt ->
    if (e !is IOException) return@retryWhen false
    val wait = (firstDelayMs shl attempt.coerceAtMost(16L).toInt()).coerceAtMost(maxDelayMs)
    Log.w(TAG, "Reading $what failed (attempt ${attempt + 1}); retrying in $wait ms", e)
    delay(wait)
    true
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
        .retryOnIoError("settings")
        .map(::readSettings)
        .stateIn(scope, SharingStarted.Eagerly, Settings())

    override suspend fun update(transform: (Settings) -> Settings) =
        dataStore.editLogged("settings") { prefs -> writeSettings(prefs, transform(readSettings(prefs))) }
}

/**
 * [OneTimeFlags] as boolean keys in a Preferences DataStore. Safar keeps them in the settings file, beside
 * (never inside) the [Settings] keys, so they are backed up and restored with the settings.
 */
class DataStoreOneTimeFlags(private val dataStore: DataStore<Preferences>) : OneTimeFlags {
    override suspend fun isSet(name: String): Boolean = dataStore.data.first()[booleanPreferencesKey(name)] == true

    override suspend fun set(name: String) = dataStore.editLogged("flag $name") { it[booleanPreferencesKey(name)] = true }
}

/** [SavedBikeStore] on its own Preferences DataStore. Null until a bike is saved, or when either key is missing. */
class DataStoreSavedBikeStore(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) : SavedBikeStore {
    private val nameKey = stringPreferencesKey("name")
    private val addressKey = stringPreferencesKey("address")

    private val savedState = MutableStateFlow<SavedBikeRecord?>(null)
    private val loadedState = MutableStateFlow(false)
    override val saved: StateFlow<SavedBikeRecord?> = savedState.asStateFlow()
    override val loaded: StateFlow<Boolean> = loadedState.asStateFlow()

    init {
        // Not stateIn: [loaded] must turn true only after [saved] holds the value read from disk.
        scope.launch {
            dataStore.data.retryOnIoError("saved bike").collect { prefs ->
                val name = prefs[nameKey]
                val address = prefs[addressKey]
                savedState.value = if (name != null && address != null) SavedBikeRecord(name, address) else null
                loadedState.value = true
            }
        }
    }

    override suspend fun save(record: SavedBikeRecord) = dataStore.editLogged("saved bike") {
        it[nameKey] = record.name
        it[addressKey] = record.address
    }

    override suspend fun clear() = dataStore.editLogged("saved bike") { it.clear() }
}
