package app.safar.bikesim.ui

import app.safar.bikesim.sim.DEFAULT_SIM_NAME
import app.safar.bikesim.sim.NameInfo
import app.safar.bikesim.sim.ReceivedLog
import app.safar.bikesim.sim.RideState
import app.safar.bikesim.sim.SimCluster
import app.safar.bikesim.sim.nameInfo
import app.safar.protocol.ChecksumMode

/** Number of simulator events (connect, notify, errors) the screen shows. */
const val EVENT_LINES = 8

/** One line of the simulator's own event list. */
data class SimEvent(val time: String, val text: String, val error: Boolean)

/** Everything the screen shows. */
data class SimUiState(
    val permissionsOk: Boolean = false,
    val bluetoothOn: Boolean = false,
    val name: String = DEFAULT_SIM_NAME,
    /** The user switched the simulator on; [advertising] is what the stack reports. */
    val wanted: Boolean = false,
    val advertising: Boolean = false,
    val central: String? = null,
    val mtu: Int? = null,
    val notifyOn: Boolean = false,
    /** Bike off: no cluster frames while the link stays up. */
    val framesPaused: Boolean = false,
    val riding: Boolean = false,
    val autoDrain: Boolean = true,
    val ride: RideState = RideState(SimCluster()),
    val framesSent: Int = 0,
    val lastSent: String? = null,
    val events: List<SimEvent> = emptyList(),
    val log: ReceivedLog = ReceivedLog(),
) {
    val cluster: SimCluster get() = ride.cluster
    val nameInfo: NameInfo get() = nameInfo(name)
    val mode: ChecksumMode get() = nameInfo.mode
    val sendingFrames: Boolean get() = notifyOn && !framesPaused

    fun withCluster(change: (SimCluster) -> SimCluster): SimUiState = copy(ride = ride.copy(cluster = change(ride.cluster)))
}
