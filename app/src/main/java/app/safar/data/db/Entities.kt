package app.safar.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Room rows. Enums are stored by name (see Mappers.kt) so reordering an enum never corrupts data.

@Entity(tableName = "fuel_up")
data class FuelUpEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val atMillis: Long,
    val odometerKm: Int,
    val litres: Double,
    val totalPaid: Double,
    val full: Boolean,
    val odoSource: String,
    val barsBefore: Int?,
)

@Entity(tableName = "trip")
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val startMillis: Long,
    val endMillis: Long?,
    val distanceKm: Double,
    val topSpeedKmh: Float,
    val avgSpeedKmh: Float,
    val odoStartKm: Int?,
    val odoEndKm: Int?,
    val fuelBarsStart: Int?,
    val fuelBarsEnd: Int?,
    val note: String? = null,
)

/** GPS track of a trip. Deleting the trip deletes its points. */
@Entity(
    tableName = "track_point",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tripId")],
)
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val atMillis: Long,
    val lat: Double,
    val lng: Double,
    val speedKmh: Float,
)

@Entity(tableName = "service_item")
data class ServiceItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val everyKm: Int,
    val lastDoneOdoKm: Int,
    val lastDoneMillis: Long?,
    val icon: String,
    // Added in version 2. everyKm 0 means "not counted by distance" (see Mappers.kt).
    val everyTimeCount: Int? = null,
    val everyTimeUnit: String? = null,
    val firstAtKm: Int? = null,
    val firstByEpochDay: Long? = null,
)

@Entity(tableName = "place")
data class PlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val detail: String,
    val query: String,
    val kind: String,
    val source: String,
    val sortOrder: Int,
    val lat: Double?,
    val lng: Double?,
)

/** The single parked spot: always row [ParkedSpotEntity.SINGLE_ID]. */
@Entity(tableName = "parked_spot")
data class ParkedSpotEntity(
    @PrimaryKey val id: Int = SINGLE_ID,
    val lat: Double,
    val lng: Double,
    val accuracyM: Float,
    val atMillis: Long,
    val label: String?,
) {
    companion object {
        const val SINGLE_ID = 1
    }
}

/** A bike paper. The file itself lives in `filesDir/documents/`, outside the database and outside backup. */
@Entity(tableName = "document")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val kind: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val addedMillis: Long,
    val expiryEpochDay: Long?,
    val note: String?,
    val remindedStage: Int,
)

/** A GPS waypoint at the join between two merged legs; deleted with the trip via CASCADE. */
@Entity(
    tableName = "trip_stop",
    foreignKeys = [ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")],
)
data class TripStopEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val index: Int,
    val lat: Double,
    val lng: Double,
    val atMillis: Long,
    val label: String? = null,
)
