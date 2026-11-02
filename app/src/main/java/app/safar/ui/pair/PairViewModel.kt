package app.safar.ui.pair

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.safar.core.AppContainer
import app.safar.core.BikeController
import app.safar.core.LinkState
import app.safar.data.SettingsStore
import app.safar.ui.shell.ShellDeps
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PairViewModel(
    private val bike: BikeController,
    settings: SettingsStore,
    private val pickTimeoutMs: Long = PICK_TIMEOUT_MS,
) : ViewModel() {
    /** One tap on a cluster, with its BLE name. [seq] makes a second tap on the same cluster a new pick. */
    private data class Pick(val address: String, val name: String?, val seq: Long)

    private class Tracked(val pick: Pick?, val inputs: PairInputs)

    private val picked = MutableStateFlow<Pick?>(null)

    /** The [Pick.seq] that waited [pickTimeoutMs] without success. */
    private val timedOutSeq = MutableStateFlow<Long?>(null)
    private var timeout: Job? = null
    private var picks = 0L
    private var autoScanned = false

    val inputs: StateFlow<PairInputs?> = combine(bike.state, settings.settings, picked, timedOutSeq) { b, s, p, t -> Triple(b, s.riderName, p) to t }
        .runningFold(null as Tracked?) { prev, (bsp, t) ->
            val (b, name, p) = bsp
            val attempted = p != null && pickAttempted(prev?.pick == p, prev?.inputs?.attempted == true, b.link)
            Tracked(p, PairInputs(b, name, p?.address, attempted, p?.name, timedOut = p != null && t == p.seq))
        }
        .map { it?.inputs }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** First visit with Bluetooth ready: scan once by itself (pair.js `enter`). */
    fun autoScan() {
        if (autoScanned) return
        autoScanned = true
        val s = bike.state.value
        if (s.link == LinkState.Idle && !s.scanning) bike.startScan()
    }

    fun scan() {
        timeout?.cancel()
        picked.value = null
        bike.startScan()
    }

    fun pick(address: String) {
        picks += 1
        val seq = picks
        val name = bike.state.value.scan.firstOrNull { it.address == address }?.name
        picked.value = Pick(address, name, seq)
        timeout?.cancel()
        timeout = viewModelScope.launch {
            delay(pickTimeoutMs)
            timedOutSeq.value = seq
        }
        if (bike.state.value.scanning) bike.stopScan()
        bike.connect(address)
    }

    override fun onCleared() {
        if (bike.state.value.scanning) bike.stopScan()
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun fromContainer() = PairViewModel(ShellDeps.bike, AppContainer.settings)
    }
}
