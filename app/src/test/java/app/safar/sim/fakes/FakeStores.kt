package app.safar.sim.fakes

import app.safar.core.ClusterReading
import app.safar.data.SavedBikeRecord
import app.safar.data.SavedBikeStore
import app.safar.data.ServiceItem
import app.safar.data.ServiceRepository
import app.safar.data.Settings
import app.safar.data.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** In-memory [SettingsStore]; [set] changes a value as the settings screen would. */
class FakeSettingsStore(initial: Settings = Settings()) : SettingsStore {
    private val mutable = MutableStateFlow(initial)
    override val settings: StateFlow<Settings> = mutable

    override suspend fun update(transform: (Settings) -> Settings) = mutable.update(transform)

    fun set(transform: (Settings) -> Settings) = mutable.update(transform)
}

/** In-memory [SavedBikeStore] that counts saves and records every stored reading, like the DataStore one. */
class FakeSavedBikeStore(initial: SavedBikeRecord? = null) : SavedBikeStore {
    private val mutable = MutableStateFlow(initial)
    private val reading = MutableStateFlow<ClusterReading?>(null)
    override val saved: StateFlow<SavedBikeRecord?> = mutable
    override val lastReading: StateFlow<ClusterReading?> = reading
    val saves = mutableListOf<SavedBikeRecord>()

    /** Every reading stored while a bike was saved, oldest first. */
    val readings = mutableListOf<ClusterReading>()

    /** A reading kept by an earlier run of the app; set it before the manager starts. */
    fun keep(stored: ClusterReading) {
        reading.value = stored
    }

    override suspend fun save(record: SavedBikeRecord) {
        saves += record
        if (mutable.value?.name != record.name) reading.value = null
        mutable.value = record
    }

    override suspend fun saveReading(reading: ClusterReading) {
        if (mutable.value == null) return
        readings += reading
        this.reading.value = reading
    }

    override suspend fun clear() {
        mutable.value = null
        reading.value = null
    }
}

/** In-memory [ServiceRepository]; ids are assigned from 1. */
class FakeServiceRepository : ServiceRepository {
    private val mutable = MutableStateFlow<List<ServiceItem>>(emptyList())
    override val items: StateFlow<List<ServiceItem>> = mutable
    private var nextId = 1L

    override suspend fun upsert(item: ServiceItem): Long {
        val id = if (item.id == 0L) nextId++ else item.id
        mutable.update { list -> list.filterNot { it.id == id } + item.copy(id = id) }
        return id
    }

    override suspend fun markDone(id: Long, odoKm: Int, atMillis: Long) = mutable.update { list ->
        list.map { if (it.id == id) it.copy(lastDoneOdoKm = odoKm, lastDoneMillis = atMillis, firstAtKm = null, firstByDate = null) else it }
    }

    override suspend fun delete(id: Long) = mutable.update { list -> list.filterNot { it.id == id } }
}
