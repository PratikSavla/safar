package app.safar.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FuelUpDao {
    @Query("SELECT * FROM fuel_up ORDER BY atMillis DESC, id DESC")
    fun observeAll(): Flow<List<FuelUpEntity>>

    @Insert
    suspend fun insert(row: FuelUpEntity): Long

    @Update
    suspend fun update(row: FuelUpEntity)

    @Query("DELETE FROM fuel_up WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface TripDao {
    @Query("SELECT * FROM trip ORDER BY startMillis DESC, id DESC")
    fun observeAll(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trip WHERE id = :id")
    fun observe(id: Long): Flow<TripEntity?>

    @Query("SELECT * FROM track_point WHERE tripId = :tripId ORDER BY atMillis, id")
    fun observeTrack(tripId: Long): Flow<List<TrackPointEntity>>

    @Insert
    suspend fun insert(row: TripEntity): Long

    @Update
    suspend fun update(row: TripEntity)

    @Insert
    suspend fun insertPoints(rows: List<TrackPointEntity>)

    /** Track points go with the trip through the foreign key's ON DELETE CASCADE. */
    @Query("DELETE FROM trip WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ServiceItemDao {
    @Query("SELECT * FROM service_item ORDER BY id")
    fun observeAll(): Flow<List<ServiceItemEntity>>

    @Query("SELECT COUNT(*) FROM service_item")
    suspend fun count(): Int

    @Insert
    suspend fun insert(row: ServiceItemEntity): Long

    @Insert
    suspend fun insertAll(rows: List<ServiceItemEntity>)

    @Update
    suspend fun update(row: ServiceItemEntity)

    @Query("UPDATE service_item SET lastDoneOdoKm = :odoKm, lastDoneMillis = :atMillis WHERE id = :id")
    suspend fun markDone(id: Long, odoKm: Int, atMillis: Long)

    @Query("DELETE FROM service_item WHERE id = :id")
    suspend fun delete(id: Long)

    /** Inserts [rows] only when the table is empty. Returns true when it seeded. */
    @Transaction
    suspend fun seedIfEmpty(rows: List<ServiceItemEntity>): Boolean {
        if (count() > 0) return false
        insertAll(rows)
        return true
    }
}

@Dao
interface PlaceDao {
    @Query("SELECT * FROM place ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<PlaceEntity>>

    @Query("SELECT * FROM place ORDER BY sortOrder, id")
    suspend fun all(): List<PlaceEntity>

    @Query("SELECT MAX(sortOrder) FROM place")
    suspend fun maxSortOrder(): Int?

    @Insert
    suspend fun insert(row: PlaceEntity): Long

    @Update
    suspend fun update(row: PlaceEntity)

    @Update
    suspend fun updateAll(rows: List<PlaceEntity>)

    @Query("DELETE FROM place WHERE id = :id")
    suspend fun delete(id: Long)

    /** Appends [row] after the last place. */
    @Transaction
    suspend fun append(row: PlaceEntity): Long = insert(row.copy(id = 0, sortOrder = (maxSortOrder() ?: -1) + 1))

    /**
     * Swaps place [id] with its neighbour in [direction] (-1 up, +1 down) and renumbers the list 0..n-1,
     * so equal or gapped sort orders cannot block a move. No-op at either end or for an unknown id.
     */
    @Transaction
    suspend fun move(id: Long, direction: Int) {
        val changed = reorderPlaces(all(), id, direction)
        if (changed.isNotEmpty()) updateAll(changed)
    }
}

@Dao
interface ParkedSpotDao {
    @Query("SELECT * FROM parked_spot WHERE id = ${ParkedSpotEntity.SINGLE_ID}")
    fun observe(): Flow<ParkedSpotEntity?>

    @Upsert
    suspend fun upsert(row: ParkedSpotEntity)

    @Query("DELETE FROM parked_spot")
    suspend fun clear()
}
