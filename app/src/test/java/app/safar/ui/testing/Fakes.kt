package app.safar.ui.testing

import app.safar.core.AlertCenter
import app.safar.core.AlertsState
import app.safar.core.BikeController
import app.safar.core.BikeState
import app.safar.core.DebugLog
import app.safar.core.LogKind
import app.safar.core.LogRecord
import app.safar.core.NavState
import app.safar.core.NavigationBridge
import app.safar.core.RideState
import app.safar.core.RideTracker
import app.safar.data.ParkedRepository
import app.safar.data.ParkedSpot
import app.safar.data.Place
import app.safar.data.PlaceRepository
import app.safar.data.SavedBikeRecord
import app.safar.data.SavedBikeStore
import app.safar.data.Settings
import app.safar.data.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

// Small in-memory fakes of the shared contracts for screen ViewModel tests. Synthetic data only.

class FakeSettings(initial: Settings = Settings()) : SettingsStore {
    override val settings = MutableStateFlow(initial)
    var writes = 0
    override suspend fun update(transform: (Settings) -> Settings) {
        writes++
        settings.update(transform)
    }
}

class FakeSavedBike(initial: SavedBikeRecord? = null) : SavedBikeStore {
    override val saved = MutableStateFlow(initial)
    override suspend fun save(record: SavedBikeRecord) { saved.value = record }
    override suspend fun clear() { saved.value = null }
}

class FakeBike(initial: BikeState = BikeState()) : BikeController {
    override val state = MutableStateFlow(initial)
    val iconTests = mutableListOf<Int>()
    var forgotten = 0
    override fun startScan() = Unit
    override fun stopScan() = Unit
    override fun connect(address: String) = Unit
    override fun disconnect() = Unit
    override fun forgetBike() { forgotten++ }
    override fun sendIconTest(iconCode: Int) { iconTests += iconCode }
}

class FakeNavigation(initial: NavState = NavState()) : NavigationBridge {
    override val state = MutableStateFlow(initial)
    val opened = mutableListOf<Pair<String, String>>()
    var ended = 0
    override fun openInMaps(label: String, query: String) {
        opened += label to query
        state.value = NavState(waiting = true, destinationLabel = label)
    }
    override fun end() {
        ended++
        state.value = NavState()
    }
}

class FakeAlerts(initial: AlertsState = AlertsState()) : AlertCenter {
    override val state = MutableStateFlow(initial)
    var missedCleared = 0
    var messagesCleared = 0
    override fun clearMissedCalls() { missedCleared++ }
    override fun clearMessages() { messagesCleared++ }
}

class FakeRide(initial: RideState = RideState()) : RideTracker {
    override val state = MutableStateFlow(initial)
}

class FakePlaces(initial: List<Place> = emptyList()) : PlaceRepository {
    override val places = MutableStateFlow(initial)
    val moves = mutableListOf<Pair<Long, Int>>()
    private var nextId = 100L
    override suspend fun add(place: Place): Long {
        val id = nextId++
        places.update { it + place.copy(id = id, sortOrder = it.size) }
        return id
    }
    override suspend fun update(place: Place) = places.update { list -> list.map { if (it.id == place.id) place else it } }
    override suspend fun delete(id: Long) = places.update { list -> list.filterNot { it.id == id } }
    override suspend fun move(id: Long, direction: Int) { moves += id to direction }
}

class FakeParked(initial: ParkedSpot? = null) : ParkedRepository {
    override val parked = MutableStateFlow(initial)
    override suspend fun save(spot: ParkedSpot) { parked.value = spot }
    override suspend fun clear() { parked.value = null }
}

class FakeLog(initial: List<LogRecord> = emptyList(), private val exportDir: File? = null) : DebugLog {
    override val records = MutableStateFlow(initial)
    override val sessionActive = MutableStateFlow(false)
    val exports = mutableListOf<Boolean>()
    override fun frame(kind: LogKind, bytes: ByteArray, ok: Boolean, note: String?) = Unit
    override fun event(name: String, detail: Map<String, String>, error: Boolean) = Unit
    override fun startSession() { sessionActive.value = true }
    override fun stopSession() { sessionActive.value = false }
    override fun clear() { records.value = emptyList() }
    override suspend fun export(hideNames: Boolean): File {
        exports += hideNames
        val dir = exportDir ?: File(System.getProperty("java.io.tmpdir"))
        val file = File.createTempFile("safar-log-test", ".jsonl", dir)
        file.deleteOnExit()
        val name = if (hideNames) "***" else "Alex"
        file.writeText((1..7).joinToString("\n") { """{"line":$it,"name":"$name"}""" } + "\n")
        return file
    }
}
