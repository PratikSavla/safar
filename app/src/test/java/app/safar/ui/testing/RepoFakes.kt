package app.safar.ui.testing

import app.safar.data.FuelRepository
import app.safar.data.FuelUp
import app.safar.data.ServiceItem
import app.safar.data.ServiceRepository
import app.safar.data.TrackPoint
import app.safar.data.Trip
import app.safar.data.TripRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map

// Read-only in-memory repositories for ViewModel emission tests. Writes are not needed there.

class FakeFuelUps(initial: List<FuelUp> = emptyList()) : FuelRepository {
    override val fuelUps = MutableStateFlow(initial)
    override suspend fun add(fuelUp: FuelUp): Long = error("not used")
    override suspend fun update(fuelUp: FuelUp) = error("not used")
    override suspend fun delete(id: Long) = error("not used")
}

class FakeServiceItems(initial: List<ServiceItem> = emptyList()) : ServiceRepository {
    override val items = MutableStateFlow(initial)
    override suspend fun upsert(item: ServiceItem): Long = error("not used")
    override suspend fun markDone(id: Long, odoKm: Int, atMillis: Long) = error("not used")
    override suspend fun delete(id: Long) = error("not used")
}

class FakeTripList(initial: List<Trip> = emptyList()) : TripRepository {
    override val trips = MutableStateFlow(initial)
    override fun trip(id: Long): Flow<Trip?> = trips.map { list -> list.firstOrNull { it.id == id } }
    override fun track(tripId: Long): Flow<List<TrackPoint>> = emptyFlow()
    override suspend fun start(trip: Trip): Long = error("not used")
    override suspend fun update(trip: Trip) = error("not used")
    override suspend fun addPoints(points: List<TrackPoint>) = error("not used")
    override suspend fun replaceTrack(tripId: Long, points: List<TrackPoint>) = error("not used")
    override suspend fun delete(id: Long) = error("not used")
}
