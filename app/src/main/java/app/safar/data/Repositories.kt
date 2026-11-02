package app.safar.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** DataStore-backed settings. [settings] is hot and always has a value (defaults until loaded). */
interface SettingsStore {
    val settings: StateFlow<Settings>
    suspend fun update(transform: (Settings) -> Settings)
}

/** Saved cluster for direct reconnect (docs 2.4). Kept apart from [Settings] because the BLE layer owns it. */
interface SavedBikeStore {
    val saved: StateFlow<SavedBikeRecord?>

    /**
     * True once [saved] holds the stored value, so a null [saved] then means "no bike", not "not read yet".
     * Stores that load synchronously keep the default (always loaded).
     */
    val loaded: StateFlow<Boolean> get() = ALWAYS_LOADED

    suspend fun save(record: SavedBikeRecord)
    suspend fun clear()
}

private val ALWAYS_LOADED: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()

data class SavedBikeRecord(val name: String, val address: String)

interface FuelRepository {
    val fuelUps: Flow<List<FuelUp>>
    suspend fun add(fuelUp: FuelUp): Long
    suspend fun update(fuelUp: FuelUp)
    suspend fun delete(id: Long)
}

interface TripRepository {
    /** Newest first. */
    val trips: Flow<List<Trip>>
    fun trip(id: Long): Flow<Trip?>
    fun track(tripId: Long): Flow<List<TrackPoint>>
    suspend fun start(trip: Trip): Long
    suspend fun update(trip: Trip)
    suspend fun addPoints(points: List<TrackPoint>)

    /**
     * Replaces trip [tripId]'s stored track with [points] in one transaction (used to thin a finished trip,
     * see [thinTrack]). Ignored when the trip no longer exists.
     */
    suspend fun replaceTrack(tripId: Long, points: List<TrackPoint>)
    suspend fun delete(id: Long)
}

interface ServiceRepository {
    val items: Flow<List<ServiceItem>>
    suspend fun upsert(item: ServiceItem): Long
    suspend fun markDone(id: Long, odoKm: Int, atMillis: Long)
    suspend fun delete(id: Long)
}

interface PlaceRepository {
    /** In rider order ([Place.sortOrder]). */
    val places: Flow<List<Place>>
    suspend fun add(place: Place): Long
    suspend fun update(place: Place)
    suspend fun delete(id: Long)
    /** Move a place up (-1) or down (+1). */
    suspend fun move(id: Long, direction: Int)
}

interface ParkedRepository {
    val parked: Flow<ParkedSpot?>
    suspend fun save(spot: ParkedSpot)
    suspend fun clear()
}
