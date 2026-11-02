package app.safar.sim.fakes

import app.safar.core.DebugLog
import app.safar.core.LogKind
import app.safar.core.LogRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** A [DebugLog] that keeps every frame and event with its virtual time, for assertions. */
class RecordingLog(private val now: () -> Long) : DebugLog {
    class Frame(val atMillis: Long, val kind: LogKind, val bytes: ByteArray, val ok: Boolean, val note: String?)

    data class Event(val atMillis: Long, val name: String, val detail: Map<String, String>, val error: Boolean)

    val frames = mutableListOf<Frame>()
    val events = mutableListOf<Event>()

    override val records: StateFlow<List<LogRecord>> = MutableStateFlow(emptyList())
    override val sessionActive: StateFlow<Boolean> = MutableStateFlow(false)

    fun tx(): List<Frame> = frames.filter { it.kind == LogKind.TX }
    fun rx(): List<Frame> = frames.filter { it.kind == LogKind.RX }
    fun named(name: String): List<Event> = events.filter { it.name == name }

    override fun frame(kind: LogKind, bytes: ByteArray, ok: Boolean, note: String?) {
        frames += Frame(now(), kind, bytes.copyOf(), ok, note)
    }

    override fun event(name: String, detail: Map<String, String>, error: Boolean) {
        events += Event(now(), name, detail, error)
    }

    override fun startSession() = Unit
    override fun stopSession() = Unit

    override fun clear() {
        frames.clear()
        events.clear()
    }

    override suspend fun export(hideNames: Boolean): File =
        throw UnsupportedOperationException("RecordingLog keeps no file; use AppLog for export scenarios")
}
