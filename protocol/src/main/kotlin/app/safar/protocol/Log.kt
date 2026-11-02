package app.safar.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/*
 * Debug log format for the in-app log export (port of docs/helper/src/log.ts).
 * One JSON object per line (JSON Lines), with the same keys, key order and timestamp format as the helper.
 */

const val LOG_FORMAT = "suzuki-ble-log/1"

/** Same text as JavaScript `Date.prototype.toISOString()`: UTC, always 3 fraction digits. */
private val ISO_MILLIS: DateTimeFormatter =
    DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

fun isoTimestamp(at: Instant): String = ISO_MILLIS.format(at)

enum class LogDirection(val json: String) { TX("tx"), RX("rx") }

sealed interface LogEntry {
    val at: Instant

    /** The JSON object of one log line. */
    fun toJson(): JsonObject

    data class Frame(
        override val at: Instant,
        val dir: LogDirection,
        /** Message type character (byte 1). */
        val type: Char,
        /** Space-separated lower-case hex of the whole frame. */
        val hex: String,
        val ok: Boolean,
        val note: String? = null,
    ) : LogEntry {
        override fun toJson(): JsonObject = buildJsonObject {
            put("t", isoTimestamp(at))
            put("kind", "frame")
            put("dir", dir.json)
            put("type", type.toString())
            put("hex", hex)
            put("ok", ok)
            if (note != null) put("note", note)
        }
    }

    /** [detail] values are strings, numbers or booleans. Null omits the key; an empty map writes `{}`. */
    data class Event(
        override val at: Instant,
        val name: String,
        val detail: Map<String, JsonPrimitive>? = null,
    ) : LogEntry {
        override fun toJson(): JsonObject = buildJsonObject {
            put("t", isoTimestamp(at))
            put("kind", "event")
            put("name", name)
            if (detail != null) put("detail", JsonObject(detail))
        }
    }
}

/** Log entry for one frame. An empty [note] is dropped, as in the helper. */
fun frameEntry(dir: LogDirection, frame: ByteArray, ok: Boolean, at: Instant, note: String? = null): LogEntry =
    LogEntry.Frame(at, dir, messageType(frame), toHex(frame), ok, note?.takeIf { it.isNotEmpty() })

fun eventEntry(name: String, detail: Map<String, JsonPrimitive>?, at: Instant): LogEntry =
    LogEntry.Event(at, name, detail)

fun eventEntry(name: String, at: Instant): LogEntry = LogEntry.Event(at, name)

/** Build an event detail map from strings, numbers and booleans. */
fun logDetail(vararg pairs: Pair<String, Any>): Map<String, JsonPrimitive> = pairs.associate { (key, value) ->
    key to when (value) {
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        else -> throw IllegalArgumentException("detail \"$key\" must be a string, number or boolean")
    }
}

/** App and phone facts for the first line of each exported file. */
data class LogHeaderInfo(
    val app: String,
    val appVersion: String,
    val phone: String,
    val android: String,
    val bikeName: String? = null,
)

/** Header line with app and phone facts. Put it first in each exported file. */
fun logHeader(info: LogHeaderInfo): String = Json.encodeToString(
    JsonObject.serializer(),
    buildJsonObject {
        put("kind", "header")
        put("format", LOG_FORMAT)
        put("app", info.app)
        put("appVersion", info.appVersion)
        put("phone", info.phone)
        put("android", info.android)
        if (info.bikeName != null) put("bikeName", info.bikeName)
    },
)

/** One JSON object per line, each line ending with '\n'. */
fun toJsonLines(entries: List<LogEntry>): String =
    entries.joinToString("\n") { Json.encodeToString(JsonObject.serializer(), it.toJson()) } + "\n"

/**
 * Fixed-size ring buffer, safe to use from several threads. Keeps the last [capacity] entries in memory;
 * write them to a file on export. 20 000 entries hold about 1 hour of traffic at app v7.6 rates.
 */
class LogBuffer(val capacity: Int = DEFAULT_CAPACITY) {
    init {
        require(capacity > 0) { "capacity must be > 0, got $capacity" }
    }

    private val items = ArrayDeque<LogEntry>(minOf(capacity, INITIAL_SLOTS))
    private val lock = Any()

    /** Adds [entry]; when full, drops the oldest entry in O(1). */
    fun add(entry: LogEntry) = synchronized(lock) {
        if (items.size == capacity) items.removeFirst()
        items.addLast(entry)
    }

    /** Snapshot, oldest first. */
    fun entries(): List<LogEntry> = synchronized(lock) { items.toList() }

    val size: Int get() = synchronized(lock) { items.size }

    fun clear() = synchronized(lock) { items.clear() }

    companion object {
        const val DEFAULT_CAPACITY = 20_000
        private const val INITIAL_SLOTS = 1024
    }
}
