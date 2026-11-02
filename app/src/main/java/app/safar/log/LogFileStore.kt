package app.safar.log

import android.util.Log
import app.safar.protocol.LogEntry
import app.safar.protocol.LogHeaderInfo
import app.safar.protocol.logHeader
import app.safar.protocol.toJsonLines
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.Writer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val TAG = "SafarLog"

/**
 * Files of the debug log: live session files in [logsDir] (app storage) and exports in [exportsDir]
 * (app cache, shared through the FileProvider). [header] gives app, phone and bike facts for the first line.
 */
class LogFileStore(
    private val logsDir: File,
    private val exportsDir: File,
    private val header: () -> LogHeaderInfo,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val keepSessions: Int = DEFAULT_KEEP_SESSIONS,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val stamp = DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss")

    private fun fileStamp(atMillis: Long): String = stamp.format(Instant.ofEpochMilli(atMillis).atZone(zone))

    /** Starts a session file. Lines are written on [io]; nothing blocks the caller. */
    fun openSession(scope: CoroutineScope, startedAtMillis: Long): SessionWriter {
        val file = File(logsDir, "session-${fileStamp(startedAtMillis)}.jsonl")
        return SessionWriter(file, scope, io) {
            prepareSessionDir()
            logHeader(header())
        }
    }

    /** Writes header + [entries] (oldest first) to a new export file, masked when [hideNames]. */
    suspend fun writeExport(entries: List<LogEntry>, hideNames: Boolean, atMillis: Long): File = withContext(io) {
        prepareDir(exportsDir)
        exportsDir.listFiles()?.forEach { old ->
            if (!old.delete()) Log.w(TAG, "Could not delete old export ${old.name}")
        }
        val info = header().let { if (hideNames) it.copy(bikeName = it.bikeName?.let(LogPrivacy::maskBikeName)) else it }
        val lines = if (hideNames) entries.map(LogPrivacy::maskEntry) else entries
        val file = File(exportsDir, "safar-log-${fileStamp(atMillis)}.jsonl")
        try {
            file.bufferedWriter().use { w ->
                w.write(logHeader(info))
                w.write("\n")
                lines.chunked(EXPORT_CHUNK).forEach { chunk -> w.write(toJsonLines(chunk)) }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Writing log export ${file.name} (${lines.size} entries) failed", e)
            throw e
        }
        file
    }

    private fun prepareSessionDir() {
        prepareDir(logsDir)
        val sessions = logsDir.listFiles { f -> f.name.startsWith("session-") }?.sortedBy { it.name } ?: return
        sessions.dropLast(keepSessions - 1).forEach { old ->
            if (!old.delete()) Log.w(TAG, "Could not delete old log session ${old.name}")
        }
    }

    private fun prepareDir(dir: File) {
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Cannot create log directory ${dir.path}")
    }

    companion object {
        const val DEFAULT_KEEP_SESSIONS = 5
        private const val EXPORT_CHUNK = 500
    }
}

/** Appends JSON Lines to one session file. [close] flushes and ends the writer coroutine. */
class SessionWriter internal constructor(
    val file: File,
    scope: CoroutineScope,
    io: CoroutineDispatcher,
    private val prepare: () -> String,
) {
    private val queue = Channel<LogEntry>(QUEUE_CAPACITY, BufferOverflow.DROP_OLDEST)

    init {
        scope.launch(io) {
            try {
                val headerLine = prepare()
                file.bufferedWriter().use { w ->
                    w.write(headerLine)
                    w.write("\n")
                    w.flush()
                    drain(w)
                }
            } catch (e: IOException) {
                Log.e(TAG, "Log session file ${file.name} failed; the session keeps only the memory buffer", e)
            }
        }
    }

    private suspend fun drain(w: Writer) {
        for (first in queue) {
            w.write(toJsonLines(listOf(first)))
            while (true) {
                val next = queue.tryReceive().getOrNull() ?: break
                w.write(toJsonLines(listOf(next)))
            }
            w.flush()
        }
    }

    fun append(entry: LogEntry) {
        queue.trySend(entry)
    }

    fun close() {
        queue.close()
    }

    private companion object {
        const val QUEUE_CAPACITY = 8192
    }
}
