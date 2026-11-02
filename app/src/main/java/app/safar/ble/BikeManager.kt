package app.safar.ble

import android.content.Context
import android.util.Log
import app.safar.ble.ReconnectPolicy.Action
import app.safar.core.AlertSource
import app.safar.core.BikeCatalog
import app.safar.core.BikeController
import app.safar.core.BikeState
import app.safar.core.ClusterOutbox
import app.safar.core.ClusterReading
import app.safar.core.DebugLog
import app.safar.core.SavedBike
import app.safar.data.SavedBikeRecord
import app.safar.data.SavedBikeStore
import app.safar.data.ServiceRepository
import app.safar.data.seedDefaultsIfEmpty
import app.safar.protocol.MAX_ICON_CODE
import app.safar.protocol.MIN_ICON_CODE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * [BikeController] and [ClusterOutbox] of the app: scanner, reconnect policy, one [LinkRunner] at a time and
 * the session of the live link. All state changes run on one serial dispatcher, so the policy needs no lock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BikeManager(
    private val context: Context,
    private val log: DebugLog,
    private val savedStore: SavedBikeStore,
    private val serviceItems: () -> ServiceRepository,
    private val inputs: () -> SessionInputs,
    parent: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) : BikeController, ClusterOutbox {
    private val scope = CoroutineScope(
        parent.coroutineContext + SupervisorJob(parent.coroutineContext.job) + Dispatchers.Default.limitedParallelism(1),
    )
    private val policy = ReconnectPolicy()
    private val scanner = BleScanner(context, log, scope)
    private val runner = LinkRunner(context, log, now)
    private val bluetooth = BluetoothMonitor(context)

    private val _state = MutableStateFlow(BikeState())
    override val state: StateFlow<BikeState> = _state.asStateFlow()

    private var linkJob: Job? = null
    private var timerJob: Job? = null
    private var generation = 0
    private var nextAttemptAt = 0L
    private var autoConnectDone = false
    private var seedOnFirstReading = false

    @Volatile private var session: BikeSession? = null

    /** Starts observing the saved bike, the scan and Bluetooth. Connects to the saved bike once at start. */
    fun start() {
        bluetooth.register { on -> scope.launch { onBluetooth(on) } }
        scope.launch {
            if (!bluetooth.isOn) onBluetooth(false)
            savedStore.saved.collect(::onSaved)
        }
        scope.launch { scanner.results.collect { list -> _state.update { it.copy(scan = list) } } }
        scope.launch {
            scanner.scanning.collect { on ->
                _state.update { it.copy(scanning = on) }
                publishLink()
            }
        }
    }

    private fun onSaved(record: SavedBikeRecord?) {
        _state.update { it.copy(saved = record?.let { r -> SavedBike(r.name, r.address, BikeCatalog.forBleName(r.name)) }) }
        // Idle, or Bluetooth off with nothing to resume: the policy connects now or when Bluetooth turns on.
        if (record != null && !autoConnectDone && policy.address == null) {
            autoConnectDone = true
            log.event("Connecting to saved bike", mapOf("bike" to record.name, "address" to record.address))
            apply(policy.connect(record.address))
        }
    }

    private fun onBluetooth(on: Boolean) {
        log.event(if (on) "Bluetooth on" else "Bluetooth off")
        if (!on) scanner.stop()
        apply(if (on) policy.onBluetoothOn() else policy.onBluetoothOff())
    }

    override fun startScan() {
        scope.launch {
            if (scanner.start()) publishLink()
        }
    }

    override fun stopScan() {
        scope.launch { scanner.stop() }
    }

    override fun connect(address: String) {
        scope.launch {
            autoConnectDone = true
            scanner.stop()
            apply(policy.connect(address))
        }
    }

    override fun disconnect() {
        scope.launch {
            autoConnectDone = true
            log.event("User disconnect")
            apply(policy.disconnect())
        }
    }

    override fun forgetBike() {
        scope.launch {
            autoConnectDone = true
            log.event("Forget bike")
            apply(policy.disconnect())
            _state.update { it.copy(saved = null, lastCluster = null, deviceInfo = null, mtu = null) }
            try {
                savedStore.clear()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Clearing the saved bike failed", e)
                log.event("Forget bike failed", mapOf("error" to (e.message ?: "")), error = true)
            }
        }
    }

    override fun sendIconTest(iconCode: Int) {
        val s = session
        if (s == null || iconCode !in MIN_ICON_CODE..MAX_ICON_CODE) {
            log.event("Icon test ignored", mapOf("icon" to "$iconCode", "connected" to "${s != null}"))
            return
        }
        s.sendIconTest(iconCode)
    }

    override fun sendCall(name: String, source: AlertSource, answered: Boolean) {
        session?.sendCall(name, source, answered)
    }

    override fun sendMissedCalls(name: String, count: Int, source: AlertSource) {
        session?.sendMissedCalls(name, count, source)
    }

    override fun sendMessages(name: String, count: Int, source: AlertSource) {
        session?.sendMessages(name, count, source)
    }

    private fun apply(actions: List<Action>) {
        for (action in actions) {
            when (action) {
                is Action.Connect -> startLink(action.address, action.attempt)
                is Action.StartTimer -> startTimer(action.delayMs)
                Action.CancelTimer -> timerJob?.cancel()
                Action.CloseLink -> closeLink()
            }
        }
        publishLink()
    }

    private fun startTimer(delayMs: Long) {
        timerJob?.cancel()
        nextAttemptAt = now() + delayMs
        log.event("Reconnect scheduled", mapOf("inMs" to "$delayMs", "attempt" to "${attempt()}"))
        timerJob = scope.launch {
            delay(delayMs)
            apply(policy.onTimer())
        }
    }

    private fun attempt(): Int = (policy.state as? ReconnectPolicy.State.Waiting)?.attempt ?: 0

    private fun closeLink() {
        generation++
        linkJob?.cancel()
        linkJob = null
        session = null
        _state.update { it.copy(cluster = null) }
    }

    private fun nameFor(address: String): String? =
        scanner.nameOf(address) ?: savedStore.saved.value?.takeIf { it.address == address }?.name

    private fun startLink(address: String, attempt: Int) {
        closeLink()
        if (!BlePermissions.canConnect(context)) {
            log.event("Connect needs permission", mapOf("permission" to "BLUETOOTH_CONNECT"), error = true)
            apply(policy.disconnect())
            return
        }
        val gen = generation
        val name = nameFor(address)
        val target = LinkTarget(address, name, firstConnection = savedStore.saved.value?.name != name)
        log.event("Connecting", mapOf("bike" to (name ?: "?"), "address" to address, "attempt" to "$attempt"))
        linkJob = scope.launch {
            val reason = runner.run(target, inputs, Listener(gen, target))
            if (gen != generation) return@launch
            session = null
            _state.update { it.copy(cluster = null) }
            log.event("Link ended", mapOf("reason" to reason))
            apply(policy.onLinkLost())
        }
    }

    private inner class Listener(private val gen: Int, private val target: LinkTarget) : LinkRunner.Listener {
        override fun onReady(name: String, ready: LinkReady, session: BikeSession) {
            if (gen != generation) return
            this@BikeManager.session = session
            apply(policy.onLinkReady())
            _state.update { it.copy(mtu = ready.mtu, deviceInfo = ready.deviceInfo, framesReceived = 0) }
            if (target.firstConnection || savedStore.saved.value?.address != target.address) {
                seedOnFirstReading = target.firstConnection
                scope.launch { saveBike(SavedBikeRecord(name, target.address)) }
            }
        }

        override fun onReading(reading: ClusterReading) {
            if (gen != generation) return
            _state.update {
                it.copy(cluster = reading, lastCluster = reading, framesReceived = it.framesReceived + 1)
            }
            if (seedOnFirstReading) {
                seedOnFirstReading = false
                scope.launch { seedService(reading.odometerKm) }
            }
        }
    }

    private suspend fun saveBike(record: SavedBikeRecord) = guardedIo("Saving the bike") {
        savedStore.save(record)
        log.event("Bike saved", mapOf("bike" to record.name, "address" to record.address))
    }

    private suspend fun seedService(odometerKm: Int) = guardedIo("Seeding service reminders") {
        val seeded = serviceItems().seedDefaultsIfEmpty(odometerKm, now())
        log.event("Service reminders", mapOf("seeded" to "$seeded", "odometerKm" to "$odometerKm"))
    }

    private suspend fun guardedIo(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "$what failed", e)
            log.event("$what failed", mapOf("error" to (e.message ?: e.javaClass.simpleName)), error = true)
        }
    }

    private fun publishLink() {
        val name = policy.address?.let { nameFor(it) ?: it } ?: ""
        val link = linkStateFor(policy.state, _state.value.scanning, name, nextAttemptAt)
        _state.update { it.copy(link = link) }
    }
}
