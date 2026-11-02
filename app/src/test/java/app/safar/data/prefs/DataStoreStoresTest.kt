package app.safar.data.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.safar.data.SavedBikeRecord
import app.safar.data.Settings
import app.safar.data.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Real file-backed DataStores in a temp folder (JVM only, no device). */
class DataStoreStoresTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() = scope.cancel()

    private fun store(name: String) =
        PreferenceDataStoreFactory.create(scope = scope) { tmp.root.resolve("$name.preferences_pb") }

    @Test
    fun settingsUpdatePersistsAndEmits() = runBlocking {
        val store = DataStoreSettingsStore(store("settings"), scope)
        assertEquals(Settings(), store.settings.value)
        store.update { it.copy(riderName = "Rider", theme = ThemeMode.LIGHT, tankLitres = 11.0) }
        val s = withTimeout(5_000) { store.settings.first { it.riderName == "Rider" } }
        assertEquals(ThemeMode.LIGHT, s.theme)
        assertEquals(11.0, s.tankLitres)
        store.update { it.copy(tankLitres = null) }
        assertNull(withTimeout(5_000) { store.settings.first { it.tankLitres == null } }.tankLitres)
    }

    @Test
    fun savedBikeSaveAndClear() = runBlocking {
        val store = DataStoreSavedBikeStore(store("saved_bike"), scope)
        assertNull(store.saved.value)
        val record = SavedBikeRecord("SBM01A1B2C3D", "AA:BB:CC:00:11:22")
        store.save(record)
        assertEquals(record, withTimeout(5_000) { store.saved.first { it != null } })
        store.clear()
        assertNull(withTimeout(5_000) { store.saved.first { it == null } })
    }
}
