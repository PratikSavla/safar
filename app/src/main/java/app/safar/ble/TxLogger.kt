package app.safar.ble

import app.safar.core.DebugLog
import app.safar.core.LogKind
import app.safar.protocol.messageType

/**
 * Logs every TX frame with its write result, telling a busy retry, a slow ack, a timeout and a GATT error
 * apart, and marks the link lost when writing has stalled: [STALL_LIMIT] real failures in a row (GATT error
 * or no ack within the hard limit) and no write acknowledged for [STALL_TIME_MS], or nothing acknowledged
 * for [BUSY_STALL_MS] at all. A burst of instant refusals cannot end a healthy link.
 */
internal class TxLogger(
    private val log: DebugLog,
    private val client: GattPort,
    private val now: () -> Long,
) : WriteListener {
    private var failuresInRow = 0
    private var lastOkAt = now()
    private var stalled = false

    override fun onWritten(frame: ByteArray, outcome: WriteOutcome, info: WriteInfo) {
        log.frame(LogKind.TX, frame, ok = outcome == WriteOutcome.Ok, note = noteFor(outcome, info))
        val at = now()
        when (outcome) {
            WriteOutcome.Ok -> {
                failuresInRow = 0
                lastOkAt = at
            }
            is WriteOutcome.Busy -> Unit
            WriteOutcome.TimedOut, is WriteOutcome.Failed -> failuresInRow++
        }
        val quietMs = at - lastOkAt
        val stall = outcome != WriteOutcome.Ok &&
            ((failuresInRow >= STALL_LIMIT && quietMs >= STALL_TIME_MS) || quietMs >= BUSY_STALL_MS)
        if (!stall || stalled) return
        stalled = true
        log.event("Write queue stalled", mapOf("failures" to "$failuresInRow", "sinceAckMs" to "$quietMs"), error = true)
        client.markLost(GattClient.STATUS_LOCAL, "no write acknowledged for ${quietMs / 1000} s")
    }

    override fun onDropped(frame: ByteArray, reason: String) {
        log.event("Frame dropped", mapOf("type" to messageType(frame).toString(), "reason" to reason))
    }

    companion object {
        /** Real write failures in a row (GATT error status, or no ack within the hard limit). */
        const val STALL_LIMIT = 3

        /** ... and no write acknowledged for this long. */
        const val STALL_TIME_MS = 10_000L

        /** Nothing acknowledged for this long, whatever the outcomes (a stack refusing every write as busy). */
        const val BUSY_STALL_MS = 30_000L

        fun noteFor(outcome: WriteOutcome, info: WriteInfo): String? = when (outcome) {
            WriteOutcome.Ok -> listOfNotNull(
                "slow ack ${info.elapsedMs} ms".takeIf { info.slowAck },
                "ok after ${info.busyRetries} busy retries".takeIf { info.busyRetries > 0 },
            ).joinToString(", ").ifEmpty { null }
            WriteOutcome.TimedOut -> "write timeout: no ack in ${info.elapsedMs} ms"
            is WriteOutcome.Busy -> "write busy: ${outcome.reason}, ${info.busyRetries} retries"
            is WriteOutcome.Failed -> "write failed: ${outcome.reason}"
        }
    }
}
