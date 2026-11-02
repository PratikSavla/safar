package app.safar.ui.log

import app.safar.core.LogKind
import app.safar.core.LogRecord
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Rows drawn at most; the rest are counted, not drawn. */
const val MAX_LOG_ROWS = 150

/** Direction chip. EVT also shows errors, like the prototype. */
enum class DirFilter(val label: String) { ALL("All"), TX("TX"), RX("RX"), EVT("EVT") }

data class LogFilter(val dir: DirFilter = DirFilter.ALL, val type: Char? = null, val errorsOnly: Boolean = false)

/** Badge on the left of a row. */
enum class LogBadge(val label: String) { TX("TX"), RX("RX"), EVT("EVT"), ERR("ERR") }

data class LogRow(
    val id: Long,
    val badge: LogBadge,
    val time: String,
    val title: String,
    val summary: String,
    val hexPreview: String?,
)

private val TS = DateTimeFormatter.ofPattern("HH:mm:ss.SSS", Locale.UK)

fun logTime(atMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String = TS.format(Instant.ofEpochMilli(atMillis).atZone(zone))

/** An error event, or a frame that failed validation. */
fun LogRecord.isError(): Boolean = kind == LogKind.ERROR || !ok

fun LogRecord.badge(): LogBadge = when {
    isError() -> LogBadge.ERR
    kind == LogKind.TX -> LogBadge.TX
    kind == LogKind.RX -> LogBadge.RX
    else -> LogBadge.EVT
}

fun matches(r: LogRecord, f: LogFilter): Boolean {
    val dirOk = when (f.dir) {
        DirFilter.ALL -> true
        DirFilter.TX -> r.kind == LogKind.TX
        DirFilter.RX -> r.kind == LogKind.RX
        DirFilter.EVT -> r.kind == LogKind.EVENT || r.kind == LogKind.ERROR
    }
    return dirOk && (f.type == null || r.type == f.type) && (!f.errorsOnly || r.isError())
}

/** Newest first. */
fun filterRecords(records: List<LogRecord>, f: LogFilter): List<LogRecord> = records.filter { matches(it, f) }.sortedByDescending { it.id }

/** One line of a row: event detail, or the first three decoded frame fields. */
fun summaryOf(r: LogRecord): String {
    val hex = r.hex
    if (hex == null) {
        return if (r.detail.isEmpty()) r.name else r.detail.entries.take(3).joinToString(" · ") { "${it.key} ${it.value}" }
    }
    return decodeFrame(hex).fields.take(3).joinToString(" · ") { "${it.first} ${it.second}" }
}

fun rowOf(r: LogRecord, zone: ZoneId = ZoneId.systemDefault()): LogRow {
    val hex = r.hex
    val title = if (hex != null) "${frameTypeName(r.type)} · type ${r.type ?: '?'}" else r.name
    return LogRow(r.id, r.badge(), logTime(r.atMillis, zone), title, summaryOf(r), hex?.take(41)?.let { "$it…" })
}

/** Detail sheet title: "Navigation frame" for frames, "Error" / "Event" otherwise. */
fun detailTitle(r: LogRecord): Pair<String, String?> = when {
    r.hex != null -> "${frameTypeName(r.type)} frame" to " type ${r.type ?: '?'}"
    r.kind == LogKind.ERROR -> "Error" to " ${r.name}"
    else -> "Event" to " ${r.name}"
}

/** Fields for the detail sheet: decoded frame fields plus the note, or the event detail. */
fun detailFields(r: LogRecord): List<Pair<String, String>> {
    val hex = r.hex ?: return r.detail.entries.map { it.key to it.value }
    val frame = decodeFrame(hex).fields
    val valid = if (r.ok) emptyList() else listOf("Validation" to "failed")
    val note = if (r.name.isNotBlank() && r.name != frameTypeName(r.type)) listOf("Note" to r.name) else emptyList()
    return frame + valid + note + r.detail.entries.map { it.key to it.value }
}

/** What the export sheet shows about the written file. */
data class ExportPreview(val fileName: String, val lineCount: Int, val sizeBytes: Long, val firstLines: List<String>)

const val PREVIEW_LINES = 5
private const val PREVIEW_WIDTH = 120

/** First [PREVIEW_LINES] lines, each cut at 120 characters, and the line count. */
fun previewOf(fileName: String, sizeBytes: Long, lines: Sequence<String>): ExportPreview {
    val first = mutableListOf<String>()
    var count = 0
    for (line in lines) {
        if (line.isEmpty()) continue
        count++
        if (first.size < PREVIEW_LINES) first += if (line.length > PREVIEW_WIDTH) line.take(PREVIEW_WIDTH) + "…" else line
    }
    return ExportPreview(fileName, count, sizeBytes, first)
}

/** "12 lines · 3.4 KB". */
fun ExportPreview.subtitle(): String = String.format(Locale.US, "%d lines · %.1f KB", lineCount, sizeBytes / 1024.0)
