package app.safar.ble

import android.util.Log
import app.safar.protocol.NAVIGATION_TYPE
import app.safar.protocol.PHONE_STATUS_TYPE
import app.safar.protocol.messageType
import kotlinx.coroutines.channels.Channel
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
    data class Failed(val reason: String) : WriteOutcome
    data object TimedOut : WriteOutcome
}

/** One GATT write with response. Suspends until `onCharacteristicWrite()` (or a failure). */
fun interface FrameWriter {
    suspend fun write(frame: ByteArray): WriteOutcome
}

interface WriteListener {
    fun onWritten(frame: ByteArray, outcome: WriteOutcome)
    /** [frame] left the queue unwritten: superseded by a newer frame of its kind, or pushed out when full. */
    fun onDropped(frame: ByteArray, reason: String)
}

/**
 * One write at a time (docs 2.3, 12.4 R4). A newer status or navigation frame replaces a pending one of the
 * same kind, because only the latest value matters. When the queue is full it drops the oldest status frame
 * first, then the oldest navigation frame, then the oldest event. [run] processes frames until [close].
 */
class WriteQueue(
    private val writer: FrameWriter,
    private val listener: WriteListener,
    private val capacity: Int = DEFAULT_CAPACITY,
    private val timeoutMs: Long = WRITE_TIMEOUT_MS,
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
            listener.onWritten(next.frame, writeOne(next.frame))
        }
    }

    private suspend fun writeOne(frame: ByteArray): WriteOutcome = withTimeoutOrNull(timeoutMs) {
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
        /** A write with response takes one or two connection intervals; 1.5 s means the link is stuck. */
        const val WRITE_TIMEOUT_MS = 1_500L
    }
}
