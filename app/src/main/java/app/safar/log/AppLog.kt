package app.safar.log

import app.safar.core.DebugLog
import app.safar.core.LogKind
import app.safar.core.LogRecord
import app.safar.protocol.LogBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.io.File

/**
 * [DebugLog] on the protocol [LogBuffer] (docs 12.6). Always keeps the last [BASE_CAPACITY] lines; with the
 * "Debug log" setting on it keeps [DEBUG_CAPACITY] and, while a session is active, also appends JSON Lines to a
 * session file. [records] is newest first and published at most every [publishIntervalMs].
 */
class AppLog(
    private val scope: CoroutineScope,
    debugEnabled: Flow<Boolean>,
    private val files: LogFileStore,
    private val now: () -> Long = System::currentTimeMillis,
    private val publishIntervalMs: Long = PUBLISH_INTERVAL_MS,
) : DebugLog {
    private val lock = Any()
    private var capacity = BASE_CAPACITY
    private var buffer = LogBuffer(capacity)
    private val rows = ArrayDeque<LogRecord>()
    private var nextId = 1L
    private var debugOn = false
    private var writer: SessionWriter? = null

    private val _records = MutableStateFlow<List<LogRecord>>(emptyList())
    override val records: StateFlow<List<LogRecord>> = _records.asStateFlow()

    private val _sessionActive = MutableStateFlow(false)
    override val sessionActive: StateFlow<Boolean> = _sessionActive.asStateFlow()

    private val dirty = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            for (signal in dirty) {
                publish()
                delay(publishIntervalMs)
            }
        }
        scope.launch { debugEnabled.distinctUntilChanged().collect(::onDebugChanged) }
    }

    /** Current ring size: [BASE_CAPACITY] or [DEBUG_CAPACITY]. */
    val currentCapacity: Int get() = synchronized(lock) { capacity }

    override fun frame(kind: LogKind, bytes: ByteArray, ok: Boolean, note: String?) {
        val at = now()
        add { id -> frameLine(id, at, kind, bytes, ok, note) }
    }

    override fun event(name: String, detail: Map<String, String>, error: Boolean) {
        val at = now()
        add { id -> eventLine(id, at, name, detail, error) }
    }

    private inline fun add(make: (Long) -> LogLine) {
        synchronized(lock) {
            val line = make(nextId++)
            buffer.add(line.entry)
            rows.addLast(line.record)
            while (rows.size > capacity) rows.removeFirst()
            writer?.append(line.entry)
        }
        dirty.trySend(Unit)
    }

    override fun startSession() {
        synchronized(lock) {
            if (_sessionActive.value) return
            _sessionActive.value = true
            syncWriter()
        }
        event("Log session started", mapOf("file" to (synchronized(lock) { writer?.file?.name } ?: "memory only")))
    }

    override fun stopSession() {
        synchronized(lock) {
            if (!_sessionActive.value) return
        }
        event("Log session stopped")
        synchronized(lock) {
            _sessionActive.value = false
            syncWriter()
        }
    }

    override fun clear() {
        synchronized(lock) {
            buffer.clear()
            rows.clear()
        }
        dirty.trySend(Unit)
    }

    override suspend fun export(hideNames: Boolean): File {
        val entries = synchronized(lock) { buffer.entries() }
        return files.writeExport(entries, hideNames, now())
    }

    private fun onDebugChanged(on: Boolean) {
        synchronized(lock) {
            debugOn = on
            resize(if (on) DEBUG_CAPACITY else BASE_CAPACITY)
            syncWriter()
        }
        dirty.trySend(Unit)
    }

    /** Caller holds [lock]. Keeps the newest lines that fit. */
    private fun resize(newCapacity: Int) {
        if (newCapacity == capacity) return
        val next = LogBuffer(newCapacity)
        buffer.entries().takeLast(newCapacity).forEach(next::add)
        buffer = next
        capacity = newCapacity
        while (rows.size > capacity) rows.removeFirst()
    }

    /** Caller holds [lock]. A session file is open exactly while the debug log is on and a session is active. */
    private fun syncWriter() {
        val shouldWrite = debugOn && _sessionActive.value
        val current = writer
        if (shouldWrite && current == null) {
            writer = files.openSession(scope, now())
        } else if (!shouldWrite && current != null) {
            current.close()
            writer = null
        }
    }

    private fun publish() {
        _records.value = synchronized(lock) { rows.reversed() }
    }

    companion object {
        const val BASE_CAPACITY = 500
        const val DEBUG_CAPACITY = LogBuffer.DEFAULT_CAPACITY
        const val PUBLISH_INTERVAL_MS = 250L
    }
}
