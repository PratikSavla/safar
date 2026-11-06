package app.safar.data.backup

import app.safar.data.db.BackupDao
import app.safar.data.db.DatabaseRows
import app.safar.data.db.DocumentEntity
import app.safar.data.db.FuelUpEntity
import app.safar.data.db.ParkedSpotEntity
import app.safar.data.db.PlaceEntity
import app.safar.data.db.ServiceItemEntity
import app.safar.data.db.TrackPointEntity
import app.safar.data.db.TripEntity

// Synthetic rows for the export / import tests. No real people, places or plates.

internal const val PDF_NAME = "0b9b5f3e-1111-4c2a-9a59-6f1d2b8e7a10.pdf"
internal const val JPG_NAME = "7e1c2d3f-2222-4b5a-8c6d-9e0f1a2b3c4d.jpg"

internal fun sampleRows(): DatabaseRows = DatabaseRows(
    trips = listOf(
        TripEntity(4, "Morning ride", 1_000, 2_000, 42.5, 98f, 41f, 12_164, 12_207, 5, 4),
        TripEntity(7, "Open ride", 3_000, null, 0.0, 0f, 0f, null, null, null, null),
    ),
    trackPoints = listOf(
        TrackPointEntity(1, 4, 1_100, 10.5, 70.25, 30f),
        TrackPointEntity(2, 4, 1_200, 10.51, 70.26, 42f),
        TrackPointEntity(3, 7, 3_100, 11.0, 71.0, 12f),
    ),
    fuelUps = listOf(FuelUpEntity(3, 1_000, 12_164, 6.7, 712.0, true, "EDITED", 1)),
    serviceItems = listOf(
        ServiceItemEntity(5, "Engine oil", 3000, 9800, null, "OIL"),
        ServiceItemEntity(8, "Battery check", 0, 9800, 1_000, "BATTERY", 6, "MONTHS", null, 20_500),
    ),
    places = listOf(PlaceEntity(6, "Home", "Main Rd", "Main Rd, NH 48", "HOME", "TYPED", 0, 10.5, 70.25)),
    parkedSpot = ParkedSpotEntity(lat = 10.5, lng = 70.25, accuracyM = 8f, atMillis = 3_000, label = "Main Rd"),
    documents = listOf(
        DocumentEntity(1, "PUC", "PUC", PDF_NAME, "application/pdf", 9, 500, 20_400, "Centre 12", 1),
        DocumentEntity(2, "RC", "RC", JPG_NAME, "image/jpeg", 5, 600, null, null, 0),
    ),
)

/** In-memory [BackupDao]: the transaction methods come from the interface and run on these lists. */
internal class FakeBackupDao(rows: DatabaseRows = DatabaseRows(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), null, emptyList())) : BackupDao {
    var trips = rows.trips.toMutableList()
    var points = rows.trackPoints.toMutableList()
    var fuel = rows.fuelUps.toMutableList()
    var service = rows.serviceItems.toMutableList()
    var placeRows = rows.places.toMutableList()
    var parked: ParkedSpotEntity? = rows.parkedSpot
    var docs = rows.documents.toMutableList()

    override suspend fun trips() = trips.toList()
    override suspend fun trackPoints() = points.toList()
    override suspend fun fuelUps() = fuel.toList()
    override suspend fun serviceItems() = service.toList()
    override suspend fun places() = placeRows.toList()
    override suspend fun parkedSpot() = parked
    override suspend fun documents() = docs.toList()
    override suspend fun clearTrackPoints() = points.clear()
    override suspend fun clearTrips() = trips.clear()
    override suspend fun clearFuelUps() = fuel.clear()
    override suspend fun clearServiceItems() = service.clear()
    override suspend fun clearPlaces() = placeRows.clear()
    override suspend fun clearParkedSpot() { parked = null }
    override suspend fun clearDocuments() = docs.clear()
    override suspend fun insertTrips(rows: List<TripEntity>) { trips += rows }
    override suspend fun insertTrackPoints(rows: List<TrackPointEntity>) {
        var next = (points.maxOfOrNull { it.id } ?: 0) + 1
        points += rows.map { it.copy(id = next++) }
    }
    override suspend fun insertFuelUps(rows: List<FuelUpEntity>) { fuel += rows }
    override suspend fun insertServiceItems(rows: List<ServiceItemEntity>) { service += rows }
    override suspend fun insertPlaces(rows: List<PlaceEntity>) { placeRows += rows }
    override suspend fun insertParkedSpot(row: ParkedSpotEntity) { parked = row }
    override suspend fun insertDocuments(rows: List<DocumentEntity>) { docs += rows }
}
