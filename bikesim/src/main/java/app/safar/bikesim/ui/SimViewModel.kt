package app.safar.bikesim.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.safar.bikesim.ble.PeripheralEvents
import app.safar.bikesim.ble.SimAdvertiser
import app.safar.bikesim.ble.SimGattServer
import app.safar.bikesim.ble.TAG
import app.safar.bikesim.sim.ClusterFrames
import app.safar.bikesim.sim.FrameDescriber
import app.safar.bikesim.sim.Gear
import app.safar.bikesim.sim.MAX_FUEL_BARS
import app.safar.bikesim.sim.MAX_SIM_SPEED_KMH
import app.safar.bikesim.sim.ReceivedEntry
import app.safar.bikesim.sim.Ride
import app.safar.protocol.CLUSTER_PERIOD_MS
import app.safar.protocol.toHex
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Owns the peripheral and the two timers: cluster frames every 5 s and the 1 s ride tick. */
class SimViewModel(app: Application) : AndroidViewModel(app), PeripheralEvents {
    private val _state = MutableStateFlow(SimUiState())
    val state: StateFlow<SimUiState> = _state.asStateFlow()

    private val server = SimGattServer(app, this)
    private val advertiser = SimAdvertiser(app, this)
    private val nextId = AtomicLong()
    private var clusterJob: Job? = null

    /** The start or restart of advertising, cancelled when the simulator is switched off. */
    private var advertiseJob: Job? = null
    private var rideJob: Job? = null

    fun onAccess(permissionsOk: Boolean, bluetoothOn: Boolean) {
        val was = _state.value
        _state.update { it.copy(permissionsOk = permissionsOk, bluetoothOn = bluetoothOn) }
        if (was.wanted && (!bluetoothOn || !permissionsOk)) {
            onEvent(if (!bluetoothOn) "Bluetooth turned off" else "Bluetooth permission removed", error = true)
            switchOff()
        } else if (!was.wanted && bluetoothOn && permissionsOk) {
            // Bluetooth is back on, or the app starts again after a kill: put back a name a stop could not restore.
            advertiser.restorePendingName()
        }
    }

    fun setName(name: String) {
        if (_state.value.wanted) return
        _state.update { it.copy(name = name.take(NAME_MAX)) }
    }

    fun setSimulatorOn(on: Boolean) = if (on) switchOn() else switchOff()

    private fun switchOn() {
        val s = _state.value
        if (s.wanted || !s.permissionsOk || !s.bluetoothOn) return
        _state.update { it.copy(wanted = true) }
        advertiseJob = viewModelScope.launch {
            if ((!server.open() || !advertiser.start(_state.value.name)) && _state.value.wanted) switchOff()
        }
    }

    private fun switchOff() {
        _state.update { it.copy(wanted = false, advertising = false, central = null, mtu = null, notifyOn = false) }
        advertiseJob?.cancel()
        advertiseJob = null
        advertiser.stop()
        server.close()
        syncClusterTimer()
    }

    fun setSpeed(kmh: Int) = _state.update { s -> s.withCluster { it.copy(speedKmh = kmh.coerceIn(0, MAX_SIM_SPEED_KMH)) } }

    fun setGear(gear: Gear) = _state.update { s -> s.withCluster { it.copy(gear = gear) } }

    fun setFuel(bars: Int) = _state.update { s -> s.withCluster { it.copy(fuelBars = bars.coerceIn(0, MAX_FUEL_BARS)) } }

    fun setRaw25(value: Int) = _state.update { s -> s.withCluster { it.copy(raw25 = value.coerceIn(0, 0xFF)) } }

    fun setAutoDrain(on: Boolean) = _state.update { it.copy(autoDrain = on) }

    fun setPaused(paused: Boolean) {
        _state.update { it.copy(framesPaused = paused) }
        onEvent(if (paused) "Frames paused (bike off)" else "Frames resumed (bike on)")
        syncClusterTimer()
    }

    fun setRiding(on: Boolean) {
        _state.update { it.copy(riding = on) }
        rideJob?.cancel()
        rideJob = if (on) viewModelScope.launch { rideLoop() } else null
    }

    private suspend fun rideLoop() {
        while (currentCoroutineContext().isActive) {
            delay(RIDE_TICK_MS)
            _state.update { it.copy(ride = Ride.advance(it.ride, RIDE_TICK_MS / 1000.0, it.autoDrain)) }
        }
    }

    fun sendBadFraming() = send(ClusterFrames.badFraming(_state.value.cluster, _state.value.mode), "bad framing")

    fun sendBadFields() = send(ClusterFrames.badFields(_state.value.cluster, _state.value.mode), "bad fields")

    fun sendWrongChecksum() = send(ClusterFrames.wrongChecksum(_state.value.cluster, _state.value.mode), "wrong checksum")

    fun dropLink() {
        val address = server.disconnect() ?: return
        viewModelScope.launch {
            delay(DROP_CHECK_MS)
            if (_state.value.central == address) {
                onEvent("Link to $address still up ${DROP_CHECK_MS / 1000} s after Drop link", error = true)
            }
        }
    }

    fun clearLog() = _state.update { it.copy(log = it.log.clear()) }

    fun copyLogAsHex(): Boolean {
        val text = _state.value.log.asHex()
        val clipboard = getApplication<Application>().getSystemService(ClipboardManager::class.java) ?: return false
        clipboard.setPrimaryClip(ClipData.newPlainText("BikeSim received frames", text))
        return true
    }

    /** Runs the 5 s cluster timer while the central listens and the bike is "on". */
    private fun syncClusterTimer() {
        val run = _state.value.sendingFrames
        if (run && clusterJob?.isActive != true) {
            clusterJob = viewModelScope.launch {
                delay(FIRST_FRAME_DELAY_MS)
                while (isActive) {
                    send(ClusterFrames.build(_state.value.cluster, _state.value.mode), "cluster")
                    delay(CLUSTER_PERIOD_MS)
                }
            }
        } else if (!run) {
            clusterJob?.cancel()
            clusterJob = null
        }
    }

    private fun send(frame: ByteArray, label: String) {
        if (server.notify(frame)) {
            _state.update { it.copy(framesSent = it.framesSent + 1, lastSent = "$label · ${toHex(frame)}") }
        }
    }

    override fun onAdvertising(on: Boolean) {
        _state.update { it.copy(advertising = on) }
        onEvent(if (on) "Advertising as ${_state.value.name}" else "Advertising stopped")
        // Advertising failed to start with nobody connected: the simulator is of no use, so switch it off.
        if (!on) viewModelScope.launch { if (_state.value.wanted && _state.value.central == null) switchOff() }
    }

    override fun onCentral(address: String?) {
        _state.update { it.copy(central = address, mtu = null, notifyOn = if (address == null) false else it.notifyOn) }
        if (address != null) {
            onEvent("Central is $address")
        } else if (_state.value.wanted) {
            advertiseJob = viewModelScope.launch { advertiser.restart(_state.value.name) }
        }
    }

    override fun onMtu(mtu: Int) {
        _state.update { it.copy(mtu = mtu) }
        onEvent("MTU $mtu")
    }

    override fun onNotify(enabled: Boolean) {
        _state.update { it.copy(notifyOn = enabled) }
        onEvent(if (enabled) "Notify on FFF2 enabled" else "Notify on FFF2 off")
        viewModelScope.launch { syncClusterTimer() }
    }

    override fun onFrameReceived(bytes: ByteArray) {
        val entry = ReceivedEntry(nextId.incrementAndGet(), Instant.now(), FrameDescriber.describe(bytes, _state.value.mode))
        _state.update { it.copy(log = it.log.add(entry)) }
    }

    override fun onEvent(text: String, error: Boolean) {
        if (error) Log.w(TAG, text)
        val event = SimEvent(LocalTime.now().format(EVENT_TIME), text, error)
        _state.update { it.copy(events = (listOf(event) + it.events).take(EVENT_LINES)) }
    }

    override fun onCleared() {
        rideJob?.cancel()
        clusterJob?.cancel()
        advertiser.stop()
        server.close()
        super.onCleared()
    }

    private companion object {
        const val NAME_MAX = 20
        const val RIDE_TICK_MS = 1000L
        const val FIRST_FRAME_DELAY_MS = 300L
        const val DROP_CHECK_MS = 5000L
        val EVENT_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    }
}
