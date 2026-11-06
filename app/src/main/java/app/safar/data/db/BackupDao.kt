package app.safar.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

/**
 * Whole-database reads and writes for export, import and "Delete everything". Each write is one transaction,
 * so a failed import leaves the old data as it was.
 */
@Dao
interface BackupDao {
    @Query("SELECT * FROM trip ORDER BY id")
    suspend fun trips(): List<TripEntity>

    @Query("SELECT * FROM track_point ORDER BY tripId, atMillis, id")
    suspend fun trackPoints(): List<TrackPointEntity>

    @Query("SELECT * FROM fuel_up ORDER BY id")
    suspend fun fuelUps(): List<FuelUpEntity>

    @Query("SELECT * FROM service_item ORDER BY id")
    suspend fun serviceItems(): List<ServiceItemEntity>

    @Query("SELECT * FROM place ORDER BY sortOrder, id")
    suspend fun places(): List<PlaceEntity>

    @Query("SELECT * FROM parked_spot WHERE id = ${ParkedSpotEntity.SINGLE_ID}")
    suspend fun parkedSpot(): ParkedSpotEntity?

    @Query("SELECT * FROM document ORDER BY id")
    suspend fun documents(): List<DocumentEntity>

    @Query("DELETE FROM track_point")
    suspend fun clearTrackPoints()

    @Query("DELETE FROM trip")
    suspend fun clearTrips()

    @Query("DELETE FROM fuel_up")
    suspend fun clearFuelUps()

    @Query("DELETE FROM service_item")
    suspend fun clearServiceItems()

    @Query("DELETE FROM place")
    suspend fun clearPlaces()

    @Query("DELETE FROM parked_spot")
    suspend fun clearParkedSpot()

    @Query("DELETE FROM document")
    suspend fun clearDocuments()

    @Insert
    suspend fun insertTrips(rows: List<TripEntity>)

    @Insert
    suspend fun insertTrackPoints(rows: List<TrackPointEntity>)

    @Insert
    suspend fun insertFuelUps(rows: List<FuelUpEntity>)

    @Insert
    suspend fun insertServiceItems(rows: List<ServiceItemEntity>)

    @Insert
    suspend fun insertPlaces(rows: List<PlaceEntity>)

    @Insert
    suspend fun insertParkedSpot(row: ParkedSpotEntity)

    @Insert
    suspend fun insertDocuments(rows: List<DocumentEntity>)

    /** Every row, read in one transaction so an export never mixes two states of the database. */
    @Transaction
    suspend fun snapshot(): DatabaseRows = DatabaseRows(
        trips = trips(),
        trackPoints = trackPoints(),
        fuelUps = fuelUps(),
        serviceItems = serviceItems(),
        places = places(),
        parkedSpot = parkedSpot(),
        documents = documents(),
    )

    /** Empties every table. Track points first, though the trip foreign key would cascade anyway. */
    @Transaction
    suspend fun clearAll() {
        clearTrackPoints()
        clearTrips()
        clearFuelUps()
        clearServiceItems()
        clearPlaces()
        clearParkedSpot()
        clearDocuments()
    }

    /** Replaces everything with [rows], keeping their ids so track points stay with their trips. */
    @Transaction
    suspend fun replaceAll(rows: DatabaseRows) {
        clearAll()
        insertTrips(rows.trips)
        insertTrackPoints(rows.trackPoints)
        insertFuelUps(rows.fuelUps)
        insertServiceItems(rows.serviceItems)
        insertPlaces(rows.places)
        rows.parkedSpot?.let { insertParkedSpot(it) }
        insertDocuments(rows.documents)
    }
}

/** Every row of the database, for [BackupDao.replaceAll]. */
data class DatabaseRows(
    val trips: List<TripEntity>,
    val trackPoints: List<TrackPointEntity>,
    val fuelUps: List<FuelUpEntity>,
    val serviceItems: List<ServiceItemEntity>,
    val places: List<PlaceEntity>,
    val parkedSpot: ParkedSpotEntity?,
    val documents: List<DocumentEntity>,
)
