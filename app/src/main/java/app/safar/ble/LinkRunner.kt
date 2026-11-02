package app.safar.ble

import android.util.Log
import app.safar.core.BikeCatalog
import app.safar.core.ClusterReading
import app.safar.core.DebugLog
import app.safar.core.LogKind
import app.safar.protocol.GattUuids
import app.safar.protocol.checksumModeFor
import app.safar.protocol.messageType
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

/** What to connect to. [firstConnection] = not the saved bike: rider frame 'F' (docs 6.2). */
class LinkTarget(val address: String, val name: String?, val firstConnection: Boolean)

/**
 * Runs one connection from connectGatt() to the drop: setup, write queue, RX, session timers. Everything it
 * starts lives in a child scope that ends with [run]; cancelling the caller closes the GATT.
 */
internal class LinkRunner(
    private val radio: BleRadio,
    private val log: DebugLog,
    private val now: () -> Long,
) {
    interface Listener {
        fun onReady(name: String, ready: LinkReady, session: BikeSession)
        fun onReading(reading: ClusterReading)
    }

    private val errors = CoroutineExceptionHandler { _, e ->
        Log.e(TAG, "Uncaught error in a bike connection coroutine", e)
        log.event("Connection error", mapOf("error" to (e.message ?: e.javaClass.simpleName)), error = true)
    }

    /** Returns the reason the link ended (setup failure or drop). */
    suspend fun run(target: LinkTarget, inputs: () -> SessionInputs, listener: Listener): String {
        val client = radio.open(target.address) ?: return "invalid address"
        val parent = currentCoroutineContext()
        val linkScope = CoroutineScope(parent + SupervisorJob(parent.job) + errors)
        try {
            val ready = try {
                setUpLink(client, log)
            } catch (e: LinkSetupException) {
                Log.w(TAG, "Link setup to the bike failed: ${e.message}", e)
                log.event("Connect failed", mapOf("reason" to (e.message ?: "unknown")), error = true)
                return e.message ?: "setup failed"
            }
            val name = target.name ?: client.deviceName ?: return "unknown bike name"
            val session = startSession(linkScope, client, ready, name, target, inputs(), listener)
            listener.onReady(name, ready, session)
            val lost = client.link.first { it is GattLink.Disconnected } as GattLink.Disconnected
            return lost.reason
        } finally {
            linkScope.cancel()
            client.close()
        }
    }

    private fun startSession(
        scope: CoroutineScope,
        client: GattPort,
        ready: LinkReady,
        name: String,
        target: LinkTarget,
        inputs: SessionInputs,
        listener: Listener,
    ): BikeSession {
        val write = FrameWriter { client.write(GattUuids.SERVICE, GattUuids.TX_CHARACTERISTIC, it) }
        val queue = WriteQueue(write, TxLogger(log, client))
        scope.launch { queue.run() }
        scope.coroutineContext.job.invokeOnCompletion { queue.close() }
        val config = SessionConfig(checksumModeFor(name), BikeCatalog.forBleName(name), target.firstConnection)
        val session = BikeSession(scope, config, inputs, { queue.offer(it) }, log, now, onReading = listener::onReading)
        scope.launch { for (frame in client.notifications) session.onRx(frame) }
        session.start()
        log.event("Session started", mapOf("bike" to name, "riderFrame" to if (target.firstConnection) "F" else "R"))
        return session
    }
}

/** Logs every TX frame with its write result; [STALL_LIMIT] failed writes in a row mark the link lost. */
private class TxLogger(private val log: DebugLog, private val client: GattPort) : WriteListener {
    private var failuresInRow = 0

    override fun onWritten(frame: ByteArray, outcome: WriteOutcome) {
        val note = when (outcome) {
            WriteOutcome.Ok -> null
            WriteOutcome.TimedOut -> "write timeout"
            is WriteOutcome.Failed -> "write failed: ${outcome.reason}"
        }
        log.frame(LogKind.TX, frame, ok = outcome == WriteOutcome.Ok, note = note)
        failuresInRow = if (outcome == WriteOutcome.Ok) 0 else failuresInRow + 1
        if (failuresInRow == STALL_LIMIT) {
            log.event("Write queue stalled", mapOf("failures" to "$STALL_LIMIT"), error = true)
            client.markLost(GattClient.STATUS_LOCAL, "$STALL_LIMIT writes failed in a row")
        }
    }

    override fun onDropped(frame: ByteArray, reason: String) {
        log.event("Frame dropped", mapOf("type" to messageType(frame).toString(), "reason" to reason))
    }

    companion object {
        const val STALL_LIMIT = 5
    }
}
