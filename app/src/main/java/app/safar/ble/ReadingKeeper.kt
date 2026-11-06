package app.safar.ble

import android.util.Log
import app.safar.core.ClusterReading
import app.safar.core.DebugLog
import app.safar.data.SavedBikeStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** Least time between two stored readings while connected; the link's end stores the newest one at once. */
internal const val READING_SAVE_EVERY_MS = 30_000L

/**
 * Keeps the saved bike's last cluster reading on disk, so "last seen on bike" survives an app restart or
 * update. Readings arrive several times a second: one is stored at most every [READING_SAVE_EVERY_MS] (bounding
 * the loss when the process is killed mid-ride), and [flush] stores the newest when the link ends. Not
 * thread-safe: call it from the [BikeManager]'s serial dispatcher, whose [scope] runs the writes.
 */
internal class ReadingKeeper(
    private val store: SavedBikeStore,
    private val log: DebugLog,
    private val scope: CoroutineScope,
) {
    private var pending: ClusterReading? = null
    private var lastStoredAt: Long? = null

    /** The reading stored by an earlier run of the app, or null. Read once the store is loaded. */
    fun stored(): ClusterReading? = store.lastReading.value

    fun onReading(reading: ClusterReading) {
        pending = reading
        val last = lastStoredAt
        if (last == null || reading.receivedAtMillis - last >= READING_SAVE_EVERY_MS) flush()
    }

    /** Stores the newest reading not stored yet, if any. */
    fun flush() {
        val reading = pending ?: return
        pending = null
        lastStoredAt = reading.receivedAtMillis
        scope.launch { write(reading) }
    }

    /** Drops the reading not stored yet (the bike is being forgotten). */
    fun forget() {
        pending = null
        lastStoredAt = null
    }

    private suspend fun write(reading: ClusterReading) {
        try {
            store.saveReading(reading)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Storing the last reading failed", e)
            log.event("Storing the last reading failed", mapOf("error" to (e.message ?: e.javaClass.simpleName)), error = true)
        }
    }
}
