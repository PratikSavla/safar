package app.safar.ble

import android.util.Log
import app.safar.protocol.NAVIGATION_TYPE
import app.safar.protocol.PHONE_STATUS_TYPE
import app.safar.protocol.messageType
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

/** Dequeue order: events first (calls, messages, rider), then navigation, then status (docs 12.4 R4). */
enum class FramePriority { EVENT, NAVIGATION, STATUS }

fun priorityOf(frame: ByteArray): FramePriority = when (messageType(frame)) {
    PHONE_STATUS_TYPE -> FramePriority.STATUS
    NAVIGATION_TYPE -> FramePriority.NAVIGATION
    else -> FramePriority.EVENT
}

sealed interface WriteOutcome {
    data object Ok : WriteOutcome

    /** A real failure: a GATT error status in the callback, an exception, or the link closed. */
    data class Failed(val reason: String) : WriteOutcome

    /** A real failure: no acknowledgement within [WriteQueue.WRITE_HARD_LIMIT_MS]. */
    data object TimedOut : WriteOutcome

    /** The stack refused to start the write (one still outstanding); retried, not a link failure. */
    data class Busy(val reason: String) : WriteOutcome
}

/** How a write went besides its outcome, for the TX log: time to the last outcome, busy retries, slow ack. */
data class WriteInfo(val elapsedMs: Long = 0, val busyRetries: Int = 0, val slowAck: Boolean = false)

/** One GATT write with response. Suspends until `onCharacteristicWrite()` (or a failure). */
fun interface FrameWriter {
    suspend fun write(frame: ByteArray): WriteOutcome
}

interface WriteListener {
    fun onWritten(frame: ByteArray, outcome: WriteOutcome, info: WriteInfo)
    /** [frame] left the queue unwritten: superseded by a newer frame of its kind, or pushed out when full. */
    fun onDropped(frame: ByteArray, reason: String)
}

/**
 * One write at a time (docs 2.3, 12.4 R4). A newer status or navigation frame replaces a pending one of the
 * same kind, because only the latest value matters. When the queue is full it drops the oldest status frame
 * first, then the oldest navigation frame, then the oldest event. [run] processes frames until [close].
 *
 * A slow acknowledgement is waited for up to [hardLimitMs]: the next write never starts while the stack still
 * holds the last one, which would only be refused as busy. A busy refusal is retried [busyRetries] times,
 * [busyBackoffMs] apart, unless a newer frame of its kind supersedes it meanwhile.
 */
class WriteQueue(
    private val writer: FrameWriter,
    private val listener: WriteListener,
    private val capacity: Int = DEFAULT_CAPACITY,
    private val hardLimitMs: Long = WRITE_HARD_LIMIT_MS,
    private val busyRetries: Int = BUSY_RETRIES,
    private val busyBackoffMs: Long = BUSY_BACKOFF_MS,
    /** Monotonic milliseconds, for [WriteInfo]. */
    private val clock: () -> Long = { System.nanoTime() / NANOS_PER_MS },
) {
    private class Pending(val frame: ByteArray, val priority: FramePriority)

    private val lock = Any()
    private val items = ArrayList<Pending>()
    private val signal = Channel<Unit>(Channel.CONFLATED)
    private var closed = false

    init {
        require(capacity >= 2) { "capacity must be >= 2, got $capacity" }
    }

    val size: Int get() = synchronized(lock) { items.size }

    /** Queues [frame]. Returns false when the queue is closed or the frame itself was dropped. */
    fun offer(frame: ByteArray, priority: FramePriority = priorityOf(frame)): Boolean {
        val dropped = mutableListOf<Pair<ByteArray, String>>()
        val accepted = synchronized(lock) {
            if (closed) return false
            if (priority != FramePriority.EVENT) {
                val old = items.indexOfFirst { it.priority == priority }
                if (old >= 0) dropped += items.removeAt(old).frame to "superseded"
            }
            if (items.size >= capacity) {
                val victim = victimIndex(priority)
                if (victim < 0) {
                    dropped += frame to "queue full"
                    false
                } else {
                    dropped += items.removeAt(victim).frame to "queue full"
                    items += Pending(frame, priority)
                    true
                }
            } else {
                items += Pending(frame, priority)
                true
            }
        }
        dropped.forEach { (f, reason) -> listener.onDropped(f, reason) }
        if (accepted) signal.trySend(Unit)
        return accepted
    }

    /** Oldest status, then oldest navigation, then (only for an incoming event) the oldest event. -1: drop new. */
    private fun victimIndex(incoming: FramePriority): Int {
        val status = items.indexOfFirst { it.priority == FramePriority.STATUS }
        if (status >= 0) return status
        val nav = items.indexOfFirst { it.priority == FramePriority.NAVIGATION }
        if (nav >= 0) return nav
        return if (incoming == FramePriority.EVENT) 0 else -1
    }

    private fun take(): Pending? = synchronized(lock) {
        val best = items.withIndex().minByOrNull { (index, p) -> p.priority.ordinal * (capacity + 1) + index }
        best?.let { items.removeAt(it.index) }
    }

    /** Writes queued frames one at a time until [close]. Run it in the connection's scope. */
    suspend fun run() {
        while (true) {
            val next = take()
            if (next == null) {
                if (signal.receiveCatching().isClosed) return
                continue
            }
            val done = try {
                writeWithRetries(next)
            } catch (e: CancellationException) {
                // The link closed mid-write; report the frame so the TX log still shows it.
                listener.onWritten(next.frame, WriteOutcome.Failed(CANCELLED), WriteInfo())
                throw e
            }
            done?.let { (outcome, info) -> listener.onWritten(next.frame, outcome, info) }
        }
    }

    /** Writes [p], retrying a busy refusal. Null when a newer frame superseded it meanwhile (reported dropped). */
    private suspend fun writeWithRetries(p: Pending): Pair<WriteOutcome, WriteInfo>? {
        val start = clock()
        var retries = 0
        while (true) {
            val attemptStart = clock()
            val outcome = writeOne(p.frame)
            val end = clock()
            if (outcome !is WriteOutcome.Busy || retries >= busyRetries) {
                val slow = outcome == WriteOutcome.Ok && end - attemptStart > SLOW_ACK_MS
                return outcome to WriteInfo(end - start, retries, slow)
            }
            retries++
            delay(busyBackoffMs)
            if (superseded(p)) {
                listener.onDropped(p.frame, "superseded")
                return null
            }
        }
    }

    /** Whether a newer status or navigation frame of [p]'s kind waits in the queue. */
    private fun superseded(p: Pending): Boolean = p.priority != FramePriority.EVENT &&
        synchronized(lock) { items.any { it.priority == p.priority } }

    private suspend fun writeOne(frame: ByteArray): WriteOutcome = withTimeoutOrNull(hardLimitMs) {
        try {
            writer.write(frame)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Write of a '${messageType(frame)}' frame threw", e)
            WriteOutcome.Failed(e.message ?: e.javaClass.simpleName)
        }
    } ?: WriteOutcome.TimedOut

    /** Drops pending frames (without callbacks) and ends [run]. Later [offer] calls return false. */
    fun close() {
        synchronized(lock) {
            closed = true
            items.clear()
        }
        signal.close()
    }

    companion object {
        const val DEFAULT_CAPACITY = 32

        /**
         * A write with response takes one or two connection intervals; an ack after 1.5 s is logged as slow.
         * A loaded phone can take seconds, so the queue keeps waiting up to [WRITE_HARD_LIMIT_MS].
         */
        const val SLOW_ACK_MS = 1_500L

        /** No ack after this long counts as a real failure ([WriteOutcome.TimedOut]). */
        const val WRITE_HARD_LIMIT_MS = 5_000L

        /** Retries of a write the stack refused as busy, [BUSY_BACKOFF_MS] apart. */
        const val BUSY_RETRIES = 3
        const val BUSY_BACKOFF_MS = 150L
        private const val NANOS_PER_MS = 1_000_000L
        /** [WriteOutcome.Failed] reason of a write cut off because the queue's coroutine was cancelled. */
        const val CANCELLED = "cancelled: link closed"
    }
}
