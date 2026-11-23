package app.safar.ble

import app.safar.core.DebugLog
import app.safar.core.LogKind
import app.safar.protocol.messageType

/**
 * Logs every TX frame with its write result, telling a busy retry, a slow ack, a timeout, a rejection and a
 * failure apart, and marks the link lost when writing has stalled: [STALL_LIMIT] writes in a row with no
 * answer (no ack within the hard limit, or a local failure) and no write answered for [STALL_TIME_MS], or no
 * write answered for [BUSY_STALL_MS] at all. A write the bike answers with a GATT error ([WriteOutcome.Rejected])
 * is an answer: the link is alive, and the original app keeps it (it only logs write errors). A burst of instant
 * busy refusals cannot end a healthy link either.
 */
internal class TxLogger(
    private val log: DebugLog,
    private val client: GattPort,
    private val now: () -> Long,
) : WriteListener {
    private var unansweredInRow = 0
    private var rejectedInRow = 0
    private var lastAnswerAt = now()
    private var stalled = false

    override fun onWritten(frame: ByteArray, outcome: WriteOutcome, info: WriteInfo) {
        log.frame(LogKind.TX, frame, ok = outcome == WriteOutcome.Ok, note = noteFor(outcome, info))
        val at = now()
        when (outcome) {
            WriteOutcome.Ok -> {
                answered(at)
                rejectedInRow = 0
            }
            is WriteOutcome.Rejected -> {
                answered(at)
                rejectedInRow++
                val detail = mapOf("status" to "${outcome.status}", "inRow" to "$rejectedInRow")
                if (rejectedInRow == REJECTED_REPORT) log.event("Bike rejects writes", detail, error = true)
            }
            is WriteOutcome.Busy -> Unit
            WriteOutcome.TimedOut, is WriteOutcome.Failed -> unansweredInRow++
        }
        val quietMs = at - lastAnswerAt
        val stall = outcome != WriteOutcome.Ok && outcome !is WriteOutcome.Rejected &&
            ((unansweredInRow >= STALL_LIMIT && quietMs >= STALL_TIME_MS) || quietMs >= BUSY_STALL_MS)
        if (!stall || stalled) return
        stalled = true
        log.event("Write queue stalled", mapOf("failures" to "$unansweredInRow", "sinceAckMs" to "$quietMs"), error = true)
        client.markLost(GattClient.STATUS_LOCAL, "no write acknowledged for ${quietMs / 1000} s")
    }

    private fun answered(at: Long) {
        unansweredInRow = 0
        lastAnswerAt = at
    }

    override fun onDropped(frame: ByteArray, reason: String) {
        log.event("Frame dropped", mapOf("type" to messageType(frame).toString(), "reason" to reason))
    }

    companion object {
        /** Unanswered writes in a row (no ack within the hard limit, or a local failure). */
        const val STALL_LIMIT = 3

        /** ... and no write acknowledged for this long. */
        const val STALL_TIME_MS = 10_000L

        /** Nothing answered for this long, whatever the outcomes (a stack refusing every write as busy). */
        const val BUSY_STALL_MS = 30_000L

        /** Rejected writes in a row after which "Bike rejects writes" is logged once. */
        const val REJECTED_REPORT = 3

        fun noteFor(outcome: WriteOutcome, info: WriteInfo): String? = when (outcome) {
            WriteOutcome.Ok -> listOfNotNull(
                "slow ack ${info.elapsedMs} ms".takeIf { info.slowAck },
                "ok after ${info.busyRetries} busy retries".takeIf { info.busyRetries > 0 },
            ).joinToString(", ").ifEmpty { null }
            WriteOutcome.TimedOut -> "write timeout: no ack in ${info.elapsedMs} ms"
            is WriteOutcome.Busy -> "write busy: ${outcome.reason}, ${info.busyRetries} retries"
            is WriteOutcome.Failed -> "write failed: ${outcome.reason}"
            is WriteOutcome.Rejected -> "write rejected: GATT status ${outcome.status}"
        }
    }
}
