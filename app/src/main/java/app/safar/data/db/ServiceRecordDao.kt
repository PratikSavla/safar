package app.safar.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** The service history, and the writes that change an item together with it. */
@Dao
interface ServiceRecordDao {
    @Query("SELECT * FROM service_record ORDER BY atMillis DESC, id DESC")
    fun observeAll(): Flow<List<ServiceRecordEntity>>

    @Query("SELECT * FROM service_item WHERE id = :id")
    suspend fun item(id: Long): ServiceItemEntity?

    @Insert
    suspend fun insert(row: ServiceRecordEntity): Long

    @Update
    suspend fun updateItem(row: ServiceItemEntity)

    @Query(
        "UPDATE service_item SET lastDoneOdoKm = :odoKm, lastDoneMillis = :atMillis, firstAtKm = NULL, firstByEpochDay = NULL " +
            "WHERE id = :id",
    )
    suspend fun setItemDone(id: Long, odoKm: Int, atMillis: Long)

    @Update
    suspend fun updateRecord(row: ServiceRecordEntity)

    @Update
    suspend fun updateItems(rows: List<ServiceItemEntity>)

    /** Saves a changed record and the items that count from a different service because of it. */
    @Transaction
    suspend fun editRecord(record: ServiceRecordEntity, items: List<ServiceItemEntity>) {
        updateRecord(record)
        if (items.isNotEmpty()) updateItems(items)
    }

    @Query("DELETE FROM service_record WHERE id = :id")
    suspend fun delete(id: Long)

    /** Marks item [id] done (ending a pending first service) and adds the service to the history. */
    @Transaction
    suspend fun markDone(id: Long, odoKm: Int, atMillis: Long) {
        val item = item(id) ?: return
        setItemDone(id, odoKm, atMillis)
        insert(ServiceRecordEntity(itemId = id, label = item.label, icon = item.icon, atMillis = atMillis, odoKm = odoKm, cost = null, note = null))
    }

    /** Adds an earlier service, and updates [item] to count from it when given. */
    @Transaction
    suspend fun logPast(record: ServiceRecordEntity, item: ServiceItemEntity?): Long {
        val id = insert(record)
        if (item != null) updateItem(item)
        return id
    }
}
