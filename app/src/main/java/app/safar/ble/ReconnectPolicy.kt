package app.safar.ble

import app.safar.protocol.Timing

/**
 * The one reconnect state machine (docs 2.4, 2.5, 12.4 R3). Pure: it returns the [Action]s to run and keeps
 * no timers. A lost link or a failed connect waits 2 s, 5 s, 10 s, then every 30 s, and connects directly to
 * the same address (no scan). A user disconnect stops it. Bluetooth off stops it and remembers the address;
 * Bluetooth on resumes it.
 */
class ReconnectPolicy(private val delayFor: (attempt: Int) -> Long = Timing::reconnectDelayMs) {

    sealed interface State {
        data object Idle : State

        /** Bluetooth is off. [resumeAddress] is connected again when it turns on. */
        data class BluetoothOff(val resumeAddress: String?) : State

        /** A connect is running. [attempt] 0 is the first connect; 1.. are reconnects. */
        data class Connecting(val address: String, val attempt: Int) : State

        /** GATT set up and notify on. */
        data class Connected(val address: String) : State

        /** Back-off before reconnect [attempt] (1-based), which starts after [delayMs]. */
        data class Waiting(val address: String, val attempt: Int, val delayMs: Long) : State
    }

    sealed interface Action {
        data class Connect(val address: String, val attempt: Int) : Action
        data class StartTimer(val delayMs: Long) : Action
        data object CancelTimer : Action
        /** Disconnect and close the GATT of the current link, if any. */
        data object CloseLink : Action
    }

    var state: State = State.Idle
        private set

    /** Address the policy is working on, in any state but Idle. */
    val address: String?
        get() = when (val s = state) {
            State.Idle -> null
            is State.BluetoothOff -> s.resumeAddress
            is State.Connecting -> s.address
            is State.Connected -> s.address
            is State.Waiting -> s.address
        }

    /** Rider taps a bike, or the app starts with a saved bike. */
    fun connect(address: String): List<Action> {
        val s = state
        if (s is State.BluetoothOff) {
            state = State.BluetoothOff(address)
            return emptyList()
        }
        if ((s is State.Connected && s.address == address) || (s is State.Connecting && s.address == address)) {
            return emptyList()
        }
        val close = s is State.Connecting || s is State.Connected
        state = State.Connecting(address, 0)
        return listOfNotNull(Action.CancelTimer, Action.CloseLink.takeIf { close }, Action.Connect(address, 0))
    }

    /** GATT connected, MTU set, characteristics found and notify on. */
    fun onLinkReady(): List<Action> {
        val s = state as? State.Connecting ?: return emptyList()
        state = State.Connected(s.address)
        return emptyList()
    }

    /** A connect failed or timed out, or the connected link dropped. */
    fun onLinkLost(): List<Action> {
        val (address, attempt) = when (val s = state) {
            is State.Connecting -> s.address to s.attempt + 1
            is State.Connected -> s.address to 1
            else -> return emptyList()
        }
        val delay = delayFor(attempt)
        state = State.Waiting(address, attempt, delay)
        return listOf(Action.CloseLink, Action.StartTimer(delay))
    }

    fun onTimer(): List<Action> {
        val s = state as? State.Waiting ?: return emptyList()
        state = State.Connecting(s.address, s.attempt)
        return listOf(Action.Connect(s.address, s.attempt))
    }

    /** User disconnect or forget: no more reconnects until the rider connects again. */
    fun disconnect(): List<Action> {
        state = if (state is State.BluetoothOff) State.BluetoothOff(null) else State.Idle
        return listOf(Action.CancelTimer, Action.CloseLink)
    }

    fun onBluetoothOff(): List<Action> {
        if (state is State.BluetoothOff) return emptyList()
        state = State.BluetoothOff(address)
        return listOf(Action.CancelTimer, Action.CloseLink)
    }

    fun onBluetoothOn(): List<Action> {
        val s = state as? State.BluetoothOff ?: return emptyList()
        val resume = s.resumeAddress
        if (resume == null) {
            state = State.Idle
            return emptyList()
        }
        state = State.Connecting(resume, 0)
        return listOf(Action.Connect(resume, 0))
    }
}
