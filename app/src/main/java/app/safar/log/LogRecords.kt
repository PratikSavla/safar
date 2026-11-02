package app.safar.log

import app.safar.core.LogKind
import app.safar.core.LogRecord
import app.safar.protocol.LogDirection
import app.safar.protocol.LogEntry
import app.safar.protocol.frameEntry
import app.safar.protocol.logDetail
import app.safar.protocol.messageType
import app.safar.protocol.toHex
import java.time.Instant
import java.util.Locale

/** Row title of a frame in the log screen, by message type (docs README "Message types"). */
fun frameLabel(type: Char): String = when (type) {
    '1' -> "Navigation"
    '2' -> "Call"
    '3' -> "Status"
    '4' -> "Missed call"
    '5' -> "Message"
    '6' -> "Rider"
    '7' -> "Cluster"
    else -> "Frame 0x%02x".format(Locale.ROOT, type.code)
}

/** Detail key that marks an error event in the JSON Lines file. */
internal const val ERROR_KEY = "error"
internal const val NOTE_KEY = "note"

/** One log line in both shapes: [record] for the screen, [entry] for the JSON Lines file and the export. */
internal class LogLine(val record: LogRecord, val entry: LogEntry)

internal fun frameLine(id: Long, atMillis: Long, kind: LogKind, bytes: ByteArray, ok: Boolean, note: String?): LogLine {
    val dir = if (kind == LogKind.TX) LogDirection.TX else LogDirection.RX
    val rowKind = if (kind == LogKind.TX) LogKind.TX else LogKind.RX
    val type = messageType(bytes)
    val record = LogRecord(
        id = id,
        atMillis = atMillis,
        kind = rowKind,
        type = type,
        hex = toHex(bytes),
        ok = ok,
        name = frameLabel(type),
        detail = if (note.isNullOrEmpty()) emptyMap() else mapOf(NOTE_KEY to note),
    )
    return LogLine(record, frameEntry(dir, bytes, ok, Instant.ofEpochMilli(atMillis), note))
}

internal fun eventLine(id: Long, atMillis: Long, name: String, detail: Map<String, String>, error: Boolean): LogLine {
    val record = LogRecord(
        id = id,
        atMillis = atMillis,
        kind = if (error) LogKind.ERROR else LogKind.EVENT,
        name = name,
        detail = detail,
    )
    val pairs = detail.entries.map { (key, value) -> key to (value as Any) }.toMutableList()
    if (error) pairs += ERROR_KEY to true
    val json = if (pairs.isEmpty()) null else logDetail(*pairs.toTypedArray())
    return LogLine(record, LogEntry.Event(Instant.ofEpochMilli(atMillis), name, json))
}
