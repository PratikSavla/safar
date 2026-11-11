package app.safar.data.db

import app.safar.data.BikeDocument
import app.safar.data.DocumentRepository
import app.safar.data.FuelRepository
import app.safar.data.FuelUp
import app.safar.data.ParkedRepository
import app.safar.data.ParkedSpot
import app.safar.data.Place
import app.safar.data.PlaceRepository
import app.safar.data.ServiceItem
import app.safar.data.ServiceRepository
import app.safar.data.TrackPoint
import app.safar.data.Trip
import app.safar.data.TripRepository
import app.safar.data.TripStop
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Room-backed repositories. Room runs suspend DAO calls on its own executor, so callers need no dispatcher.

class RoomFuelRepository(private val dao: FuelUpDao) : FuelRepository {
    /** Newest first. */
    override val fuelUps: Flow<List<FuelUp>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }
    override suspend fun add(fuelUp: FuelUp): Long = dao.insert(fuelUp.toEntity().copy(id = 0))
    override suspend fun update(fuelUp: FuelUp) = dao.update(fuelUp.toEntity())
    override suspend fun delete(id: Long) = dao.delete(id)
}

class RoomTripRepository(private val dao: TripDao) : TripRepository {
    override val trips: Flow<List<Trip>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }
    override fun trip(id: Long): Flow<Trip?> = dao.observe(id).map { it?.toModel() }
    override fun track(tripId: Long): Flow<List<TrackPoint>> = dao.observeTrack(tripId).map { rows -> rows.map { it.toModel() } }
    override fun stops(tripId: Long): Flow<List<TripStop>> = dao.observeStops(tripId).map { rows -> rows.map { it.toModel() } }
    override suspend fun start(trip: Trip): Long = dao.insert(trip.toEntity().copy(id = 0))
    override suspend fun update(trip: Trip) = dao.update(trip.toEntity())

    override suspend fun addPoints(points: List<TrackPoint>) {
        if (points.isNotEmpty()) dao.insertPoints(points.map { it.toEntity() })
    }

    override suspend fun replaceTrack(tripId: Long, points: List<TrackPoint>) {
        require(points.all { it.tripId == tripId }) { "Every point must belong to trip $tripId" }
        dao.replaceTrack(tripId, points.map { it.toEntity() })
    }

    /** Also deletes the trip's track (foreign key cascade). */
    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun setNote(id: Long, note: String?) = dao.setNote(id, note)

    override suspend fun merge(tripIds: List<Long>, title: String): Long {
        require(tripIds.size >= 2) { "Need at least 2 trips to merge" }
        return dao.mergeTrips(tripIds, title)
    }

    override suspend fun undoMerge(mergedId: Long, originals: List<Trip>, tracks: Map<Long, List<TrackPoint>>) {
        val tripEntities = originals.map { it.toEntity() }
        val trackEntities = tracks.flatMap { (_, pts) -> pts.map { it.toEntity() } }
        dao.undoMerge(mergedId, tripEntities, trackEntities)
    }
}

class RoomServiceRepository(private val dao: ServiceItemDao) : ServiceRepository {
    override val items: Flow<List<ServiceItem>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }

    /** Inserts when [ServiceItem.id] is 0, else updates. Returns the row id. */
    override suspend fun upsert(item: ServiceItem): Long {
        if (item.id == 0L) return dao.insert(item.toEntity())
        dao.update(item.toEntity())
        return item.id
    }

    override suspend fun markDone(id: Long, odoKm: Int, atMillis: Long) = dao.markDone(id, odoKm, atMillis)
    override suspend fun delete(id: Long) = dao.delete(id)

    /** Inserts [items] in one transaction when there are none yet. Returns true when it seeded. */
    suspend fun seedIfEmpty(items: List<ServiceItem>): Boolean = dao.seedIfEmpty(items.map { it.toEntity().copy(id = 0) })
}

class RoomPlaceRepository(private val dao: PlaceDao) : PlaceRepository {
    override val places: Flow<List<Place>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }

    /** Appends to the end of the list: the given [Place.sortOrder] is replaced. */
    override suspend fun add(place: Place): Long = dao.append(place.toEntity())
    override suspend fun update(place: Place) = dao.update(place.toEntity())
    override suspend fun delete(id: Long) = dao.delete(id)

    /** Swaps with the neighbour in one transaction; no-op at either end. */
    override suspend fun move(id: Long, direction: Int) = dao.move(id, direction)
}

class RoomParkedRepository(private val dao: ParkedSpotDao) : ParkedRepository {
    override val parked: Flow<ParkedSpot?> = dao.observe().map { it?.toModel() }
    override suspend fun save(spot: ParkedSpot) = dao.upsert(spot.toEntity())
    override suspend fun clear() = dao.clear()
}

class RoomDocumentRepository(private val dao: DocumentDao) : DocumentRepository {
    override val documents: Flow<List<BikeDocument>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }
    override suspend fun get(id: Long): BikeDocument? = dao.byId(id)?.toModel()
    override suspend fun add(document: BikeDocument): Long = dao.insert(document.toEntity().copy(id = 0))
    override suspend fun update(document: BikeDocument) = dao.update(document.toEntity())
    override suspend fun setRemindedStage(id: Long, stage: Int) = dao.setRemindedStage(id, stage)
    override suspend fun delete(id: Long) = dao.delete(id)
}
