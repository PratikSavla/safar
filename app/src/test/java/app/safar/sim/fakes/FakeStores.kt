package app.safar.sim.fakes

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

/** In-memory [SavedBikeStore] that counts saves. */
class FakeSavedBikeStore(initial: SavedBikeRecord? = null) : SavedBikeStore {
    private val mutable = MutableStateFlow(initial)
    override val saved: StateFlow<SavedBikeRecord?> = mutable
    val saves = mutableListOf<SavedBikeRecord>()

    override suspend fun save(record: SavedBikeRecord) {
        saves += record
        mutable.value = record
    }

    override suspend fun clear() {
        mutable.value = null
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
