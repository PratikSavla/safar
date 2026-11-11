package app.safar.data

import app.safar.core.ClusterReading
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf

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

    /**
     * The saved bike's last cluster reading, kept across app restarts and updates ("last seen on bike").
     * Holds the stored value once [loaded] is true. Null when no reading was kept; dropped with the bike
     * ([clear]) or when a bike of another name is saved. Stores that keep no readings stay null.
     */
    val lastReading: StateFlow<ClusterReading?> get() = NO_READING

    /** Saves the bike [record]. A record with another name than the stored one drops [lastReading]. */
    suspend fun save(record: SavedBikeRecord)

    /** Keeps [reading] as [lastReading]. Ignored while no bike is saved. */
    suspend fun saveReading(reading: ClusterReading) {}

    /** Deletes the saved bike and its [lastReading]. */
    suspend fun clear()
}

private val ALWAYS_LOADED: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
private val NO_READING: StateFlow<ClusterReading?> = MutableStateFlow<ClusterReading?>(null).asStateFlow()

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
    fun stops(tripId: Long): Flow<List<TripStop>> = flowOf(emptyList())
    suspend fun start(trip: Trip): Long
    suspend fun update(trip: Trip)
    suspend fun addPoints(points: List<TrackPoint>)

    /**
     * Replaces trip [tripId]'s stored track with [points] in one transaction (used to thin a finished trip,
     * see [thinTrack]). Ignored when the trip no longer exists.
     */
    suspend fun replaceTrack(tripId: Long, points: List<TrackPoint>)
    suspend fun delete(id: Long)
    suspend fun setNote(id: Long, note: String?) {}
    suspend fun merge(tripIds: List<Long>, title: String): Long = -1L
    /** Reverses a merge: deletes [mergedId] and re-inserts [originals] with their exact original IDs and tracks. */
    suspend fun undoMerge(mergedId: Long, originals: List<Trip>, tracks: Map<Long, List<TrackPoint>>) {}
}

interface ServiceRepository {
    val items: Flow<List<ServiceItem>>
    suspend fun upsert(item: ServiceItem): Long
    /** Logs a service at [odoKm] and [atMillis], ending any pending first service. */
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

/** Bike papers. Rows only: the files are [DocumentFiles]'. */
interface DocumentRepository {
    /** Soonest expiry first, then papers without an expiry, newest first. */
    val documents: Flow<List<BikeDocument>>
    suspend fun get(id: Long): BikeDocument?
    suspend fun add(document: BikeDocument): Long
    suspend fun update(document: BikeDocument)
    suspend fun setRemindedStage(id: Long, stage: Int)
    suspend fun delete(id: Long)
}
