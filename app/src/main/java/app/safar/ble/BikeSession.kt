package app.safar.ble

import app.safar.core.AlertSource
import app.safar.core.AlertsState
import app.safar.core.BikeModel
import app.safar.core.ClusterOutbox
import app.safar.core.ClusterReading
import app.safar.core.DebugLog
import app.safar.core.LogKind
import app.safar.core.NavSnapshot
import app.safar.core.NavState
import app.safar.core.PhoneSnapshot
import app.safar.data.Settings
import app.safar.protocol.CallStatus
import app.safar.protocol.ChecksumMode
import app.safar.protocol.MAX_COUNT
import app.safar.protocol.MessageSource
import app.safar.protocol.NavStatus
import app.safar.protocol.NavigationData
import app.safar.protocol.Timing
import app.safar.protocol.encodeCall
import app.safar.protocol.encodeMessage
import app.safar.protocol.encodeMissedCall
import app.safar.protocol.encodeNavigation
import app.safar.protocol.encodePhoneStatus
import app.safar.protocol.encodeRider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/** What the session reads every tick. Resolved when the session starts (other layers install after BLE). */
class SessionInputs(
    val settings: StateFlow<Settings>,
    val phone: StateFlow<PhoneSnapshot>,
    val alerts: StateFlow<AlertsState>,
    val navigation: StateFlow<NavState>,
)

/** [firstConnection] = the bike differs from the saved one: rider frame 'F', else 'R' (docs 6.2). */
class SessionConfig(val mode: ChecksumMode, val model: BikeModel, val firstConnection: Boolean)

/**
 * Timers of one connection (docs 6.1, 4.5, 5.2), started after notify is on. [send] queues a frame for the
 * write queue. Cancel [scope] to stop everything; frames are only sent while it is active.
 */
class BikeSession(
    private val scope: CoroutineScope,
    private val config: SessionConfig,
    private val inputs: SessionInputs,
    private val send: (ByteArray) -> Unit,
    private val log: DebugLog,
    private val now: () -> Long,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val onReading: (ClusterReading) -> Unit,
) : ClusterOutbox {
    private val mode = config.mode
    private val cleared = ClearedFlagTracker()
    private val navMapper = NavFrameMapper(zone)
    private var firstCluster = true
    private val jobsLock = Any()
    private var callJob: Job? = null
    private var missedJob: Job? = null
    private var messageJob: Job? = null

    fun start() {
        scope.launch { riderLoop() }
        scope.launch { statusLoop() }
        scope.launch { navigationLoop() }
    }

    private fun emit(frame: ByteArray) {
        if (scope.isActive) send(frame)
    }

    private suspend fun riderLoop() {
        val frame = encodeRider(riderNameFor(inputs.settings.value), config.firstConnection, mode)
        repeatWithGap(RIDER_REPEAT, RIDER_GAP_MS) { emit(frame) }
    }

    private suspend fun statusLoop() {
        delay(STATUS_START_DELAY_MS)
        while (true) {
            emit(statusFrame())
            delay(Timing.STATUS_PERIOD_MS)
        }
    }

    internal fun statusFrame(): ByteArray {
        val alerts = inputs.alerts.value
        val flags = cleared.tick(alerts.messages.count, alerts.missed.count)
        val time = Instant.ofEpochMilli(now()).atZone(zone).toLocalTime()
        val status = buildPhoneStatus(inputs.phone.value, inputs.settings.value, config.model, time, flags)
        return encodePhoneStatus(status, mode)
    }

    /** Sends only after the first parsed update; one "ended" frame when navigation stops (docs 4.5). */
    private suspend fun navigationLoop() {
        var last: NavSnapshot? = null
        while (true) {
            val nav = inputs.navigation.value
            val snapshot = nav.snapshot
            if (nav.active && snapshot != null) {
                emit(encodeNavigation(navMapper.map(snapshot, inputs.phone.value, active = true), mode))
                last = snapshot
            } else if (last != null) {
                emit(encodeNavigation(navMapper.map(last, inputs.phone.value, active = false), mode))
                log.event("Navigation ended frame sent")
                last = null
            }
            delay(Timing.NAVIGATION_PERIOD_MS)
        }
    }

    /** Handles one RX notification. Invalid frames are logged and dropped (docs 3.2). */
    fun onRx(frame: ByteArray) {
        when (val result = decodeRx(frame, mode, firstCluster, now())) {
            is RxResult.Reading -> {
                val note = if (firstCluster) "first frame: fuel ignored" else null
                firstCluster = false
                log.frame(LogKind.RX, frame, ok = true, note = note)
                onReading(result.reading)
            }
            is RxResult.Invalid -> log.frame(LogKind.RX, frame, ok = false, note = result.reason)
            is RxResult.Other -> log.frame(LogKind.RX, frame, ok = true, note = "unexpected type '${result.type}'")
        }
    }

    /** Debug: one navigation frame with [iconCode] (assignment task 4). */
    fun sendIconTest(iconCode: Int) {
        val eta = Instant.ofEpochMilli(now() + ICON_TEST_ETA_MS).atZone(zone).toLocalTime()
        val data = NavigationData(iconCode, ICON_TEST_TURN_M, eta, ICON_TEST_LEFT_M, NavStatus.NORMAL, active = true)
        emit(encodeNavigation(data, mode))
    }

    /** Ringing repeats every 1 s up to 15 times; answered 3 times, 200 ms apart (docs 5.2, Timing). */
    override fun sendCall(name: String, source: AlertSource, answered: Boolean) {
        val status = if (answered) CallStatus.ANSWERED else CallStatus.RINGING
        val frame = encodeCall(name, status, source.toProtocol(), mode)
        val times = if (answered) Timing.EVENT_REPEAT else CALL_RING_REPEAT
        val gap = if (answered) Timing.EVENT_GAP_MS else Timing.CALL_REPEAT_PERIOD_MS
        synchronized(jobsLock) {
            callJob?.cancel()
            callJob = scope.launch { repeatWithGap(times, gap) { emit(frame) } }
        }
    }

    /** 2 writes, 200 ms apart. Also stops a ringing call's repeats: a missed call ended it. */
    override fun sendMissedCalls(name: String, count: Int, source: AlertSource) {
        val frame = encodeMissedCall(name, count.coerceIn(0, MAX_COUNT), source.toProtocol(), mode)
        synchronized(jobsLock) {
            callJob?.cancel()
            missedJob?.cancel()
            missedJob = scope.launch {
                repeatWithGap(Timing.MISSED_CALL_REPEAT, Timing.MISSED_CALL_GAP_MS) { emit(frame) }
            }
        }
    }

    override fun sendMessages(name: String, count: Int, source: AlertSource) {
        val frame = encodeMessage(name, count.coerceIn(0, MAX_COUNT), source.toProtocol(), mode)
        synchronized(jobsLock) {
            messageJob?.cancel()
            messageJob = scope.launch { repeatWithGap(Timing.EVENT_REPEAT, Timing.EVENT_GAP_MS) { emit(frame) } }
        }
    }

    companion object {
        /** App v7.6 sends the rider frame several times after connect (docs 6.2). */
        const val RIDER_REPEAT = 3
        const val RIDER_GAP_MS = 1_000L
        /** First status frame about 2 s after the rider frame, as in the v7.6 capture (docs 2.2 step 8). */
        const val STATUS_START_DELAY_MS = 2_000L
        const val CALL_RING_REPEAT = 15
        private const val ICON_TEST_TURN_M = 100
        private const val ICON_TEST_LEFT_M = 1_200
        private const val ICON_TEST_ETA_MS = 10 * 60_000L
    }
}

private fun AlertSource.toProtocol(): MessageSource = when (this) {
    AlertSource.PHONE -> MessageSource.PHONE
    AlertSource.WHATSAPP -> MessageSource.WHATSAPP
}

private suspend inline fun repeatWithGap(times: Int, gapMs: Long, action: () -> Unit) {
    for (i in 0 until times) {
        action()
        if (i < times - 1) delay(gapMs)
    }
}
