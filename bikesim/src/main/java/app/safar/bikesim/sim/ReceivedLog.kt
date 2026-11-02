package app.safar.bikesim.sim

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Number of received frames the screen keeps. */
const val RECEIVED_LOG_CAPACITY = 200

/** One frame Safar wrote to FFF1, with the time it arrived. */
data class ReceivedEntry(val id: Long, val at: Instant, val frame: FrameDescription)

/** Immutable list of the newest [capacity] received frames, newest first. */
data class ReceivedLog(val entries: List<ReceivedEntry> = emptyList(), val capacity: Int = RECEIVED_LOG_CAPACITY) {
    init {
        require(capacity > 0) { "capacity must be > 0, got $capacity" }
    }

    fun add(entry: ReceivedEntry): ReceivedLog = copy(entries = (listOf(entry) + entries).take(capacity))

    fun clear(): ReceivedLog = copy(entries = emptyList())

    /** One line per frame, oldest first: "HH:mm:ss.SSS  a5 36 ... 7f". */
    fun asHex(zone: ZoneId = ZoneId.systemDefault()): String =
        entries.asReversed().joinToString("\n") { "${timeText(it.at, zone)}  ${it.frame.hex}" }

    companion object {
        private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

        fun timeText(at: Instant, zone: ZoneId = ZoneId.systemDefault()): String = TIME.format(at.atZone(zone))
    }
}
