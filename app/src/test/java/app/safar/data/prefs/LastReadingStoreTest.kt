package app.safar.data.prefs

import app.safar.core.ClusterReading
import app.safar.data.SavedBikeRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The saved bike's last reading in the real file-backed DataStore (JVM only, no device). */
class LastReadingStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bike = SavedBikeRecord("SBM01A1B2C3D", "AA:BB:CC:00:11:22")
    private val reading = ClusterReading(0, 12_305, 128.7, 506.2, 4, 'N', 'N'.code, 0x1D, 1_793_000_000_000L)

    @After
    fun tearDown() = scope.cancel()

    private fun open(storeScope: CoroutineScope = scope) =
        DataStoreSavedBikeStore(createPreferencesStore("saved bike", storeScope) { tmp.root.resolve("saved_bike.preferences_pb") }, storeScope)

    private suspend fun DataStoreSavedBikeStore.awaitReading(expected: ClusterReading?) =
        withTimeout(5_000) { lastReading.first { it == expected } }

    @Test
    fun readingSurvivesARestartAndIsLoadedWithTheBike() = runBlocking {
        val runScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val first = open(runScope)
        first.save(bike)
        first.saveReading(reading)
        runScope.coroutineContext.job.cancelAndJoin()

        val restarted = open()
        withTimeout(5_000) { restarted.loaded.first { it } }
        assertEquals(bike, restarted.saved.value)
        assertEquals(reading, restarted.lastReading.value, "loaded means the stored reading is already there")
    }

    @Test
    fun missingFuelAndGearStayMissing() = runBlocking {
        val store = open()
        store.save(bike)
        store.saveReading(reading)
        store.awaitReading(reading)
        val noFuel = reading.copy(fuelBars = null, gear = null, receivedAtMillis = reading.receivedAtMillis + 1_000)
        store.saveReading(noFuel)
        assertEquals(noFuel, store.awaitReading(noFuel))
    }

    @Test
    fun anotherBikeDropsTheReadingButANewAddressKeepsIt() = runBlocking {
        val store = open()
        store.save(bike)
        store.saveReading(reading)
        store.awaitReading(reading)

        store.save(bike.copy(address = "AA:BB:CC:00:11:33"))
        withTimeout(5_000) { store.saved.first { it?.address == "AA:BB:CC:00:11:33" } }
        assertEquals(reading, store.lastReading.value, "same bike under a rotated address")

        store.save(SavedBikeRecord("SAS01A1B2C3D", "AA:BB:CC:00:11:44"))
        assertNull(store.awaitReading(null))
    }

    @Test
    fun clearDropsTheReadingAndLaterReadingsAreIgnored() = runBlocking {
        val store = open()
        store.saveReading(reading)
        store.save(bike)
        withTimeout(5_000) { store.saved.first { it != null } }
        assertNull(store.lastReading.value, "a reading without a saved bike is not kept")

        store.saveReading(reading)
        store.awaitReading(reading)
        store.clear()
        assertNull(store.awaitReading(null))
        store.saveReading(reading)
        assertNull(withTimeout(5_000) { store.lastReading.first() }, "a write after the bike was forgotten")
    }
}
