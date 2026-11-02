package app.safar.ble

import android.util.Log
import app.safar.core.DebugLog
import app.safar.data.SavedBikeRecord
import app.safar.data.SavedBikeStore
import app.safar.data.ServiceRepository
import app.safar.data.seedDefaultsIfEmpty
import kotlin.coroutines.cancellation.CancellationException

/**
 * What [BikeManager] stores when a link comes up: the saved bike (name + address) and the default service
 * reminders. Each write is logged; a failure is logged and reported, never thrown.
 */
internal class BikeRecords(
    private val log: DebugLog,
    private val savedStore: SavedBikeStore,
    private val serviceItems: () -> ServiceRepository,
    private val now: () -> Long,
) {
    /** Whether the bike [name] at [address] differs from the saved one, so [save] has something to do. */
    fun needsSave(name: String, address: String): Boolean = savedStore.saved.value != SavedBikeRecord(name, address)

    /**
     * Saves [name] at [address]. The saved bike under a new address (a rotating private address found by the
     * reconnect scan) is logged as an address change, anything else as a new bike.
     */
    suspend fun save(name: String, address: String) = guardedIo("Saving the bike") {
        val old = savedStore.saved.value
        savedStore.save(SavedBikeRecord(name, address))
        if (old != null && old.name == name && old.address != address) {
            log.event(
                "Saved bike address changed",
                mapOf("bike" to name, "oldAddress" to old.address, "newAddress" to address),
            )
        } else {
            log.event("Bike saved", mapOf("bike" to name, "address" to address))
        }
    }

    /** Seeds the default service reminders at [odometerKm]; returns whether it completed. */
    suspend fun seedService(odometerKm: Int) = guardedIo("Seeding service reminders") {
        val seeded = serviceItems().seedDefaultsIfEmpty(odometerKm, now())
        log.event("Service reminders", mapOf("seeded" to "$seeded", "odometerKm" to "$odometerKm"))
    }

    /** Runs [block], logging a failure; returns whether it completed. */
    private suspend fun guardedIo(what: String, block: suspend () -> Unit): Boolean {
        try {
            block()
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "$what failed", e)
            log.event("$what failed", mapOf("error" to (e.message ?: e.javaClass.simpleName)), error = true)
            return false
        }
    }
}
