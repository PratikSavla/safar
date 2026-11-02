package app.safar.data

import app.safar.data.db.RoomServiceRepository
import kotlinx.coroutines.flow.first

/**
 * Seeds [DefaultServiceItems] counted from [odometerKm] when the rider has no service items yet. The BLE layer
 * calls this once the first cluster frame of a newly paired bike arrives. Returns true when it seeded.
 * Atomic for the Room repository; other implementations check then insert.
 */
suspend fun ServiceRepository.seedDefaultsIfEmpty(odometerKm: Int, atMillis: Long): Boolean {
    val defaults = DefaultServiceItems.create(odometerKm, atMillis)
    if (this is RoomServiceRepository) return seedIfEmpty(defaults)
    if (items.first().isNotEmpty()) return false
    defaults.forEach { upsert(it) }
    return true
}
