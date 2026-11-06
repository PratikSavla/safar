package app.safar.ble

import app.safar.protocol.ConnectTiming
import app.safar.protocol.Timing

/**
 * The one reconnect state machine (docs 2.4, 2.5, 12.4 R3). Pure: it returns the [Action]s to run and keeps
 * no timers. A lost link or a failed connect waits 2 s, 5 s, 10 s, then every 30 s, and connects directly to
 * the same address (no scan). After [findAfter] failed connects in a row it runs one scan for the bike by
 * name ([Action.Find]), at most once per [findIntervalMs] on [clock], because an address may rotate: a bike
 * found there is connected at once, else the back-off goes on. Reconnects stop for good once [windowMs] has
 * passed since the link was lost or the first connect failed: no connect or scan starts later than that, and
 * the policy goes [State.Idle] with [Action.GiveUp]. A user disconnect stops it. Bluetooth off stops it and
 * remembers the address; Bluetooth on resumes it with a fresh window.
 */
class ReconnectPolicy(
    private val clock: () -> Long = { 0L },
    private val findAfter: Int = ConnectTiming.FIND_AFTER_FAILURES,
    private val findIntervalMs: Long = ConnectTiming.FIND_MIN_INTERVAL_MS,
    private val windowMs: Long = ConnectTiming.RECONNECT_WINDOW_MS,
    /** Last, so `ReconnectPolicy { attempt -> ... }` sets it. */
    private val delayFor: (attempt: Int) -> Long = Timing::reconnectDelayMs,
) {

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

        /** Direct connects to [address] kept failing: a scan looks for its bike by name before [attempt]. */
        data class Searching(val address: String, val attempt: Int) : State
    }

    sealed interface Action {
        data class Connect(val address: String, val attempt: Int) : Action
        data class StartTimer(val delayMs: Long) : Action
        data object CancelTimer : Action

        /** Disconnect and close the GATT of the current link, if any. */
        data object CloseLink : Action

        /** Scan for the bike of [address] by its name; report the result to [onFindDone]. */
        data class Find(val address: String) : Action

        /** Stop the running [Find] scan; no result is expected any more. */
        data object StopFind : Action

        /** Reconnects to [address] stopped [afterMs] after the link was lost; the policy is now Idle. */
        data class GiveUp(val address: String, val afterMs: Long) : Action
    }

    var state: State = State.Idle
        private set

    /** Failed connects in a row since the last success, scan or new connect. */
    private var failures = 0
    private var lastFindAt: Long? = null

    /** When the link was lost or the first connect failed; null while connected or not reconnecting. */
    private var lostAt: Long? = null

    /** Address the policy is working on, in any state but Idle. */
    val address: String?
        get() = when (val s = state) {
            State.Idle -> null
            is State.BluetoothOff -> s.resumeAddress
            is State.Connecting -> s.address
            is State.Connected -> s.address
            is State.Waiting -> s.address
            is State.Searching -> s.address
        }

    /** [Action.StopFind] when leaving [State.Searching], else nothing. */
    private fun stopFind(): Action? = Action.StopFind.takeIf { state is State.Searching }

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
        val stop = stopFind()
        failures = 0
        lostAt = null
        state = State.Connecting(address, 0)
        return listOfNotNull(Action.CancelTimer, stop, Action.CloseLink.takeIf { close }, Action.Connect(address, 0))
    }

    /** GATT connected, MTU set, characteristics found and notify on. */
    fun onLinkReady(): List<Action> {
        val s = state as? State.Connecting ?: return emptyList()
        failures = 0
        lostAt = null
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
        failures = if (state is State.Connecting) failures + 1 else 0
        if (lostAt == null) lostAt = clock()
        if (failures >= findAfter && findAllowed() && !windowOver(0)) {
            failures = 0
            lastFindAt = clock()
            state = State.Searching(address, attempt)
            return listOf(Action.CloseLink, Action.Find(address))
        }
        return listOf(Action.CloseLink, backOff(address, attempt))
    }

    /**
     * The [Action.Find] scan ended: [found] is the address the bike advertised from, or null. Found at the
     * same address, it did not move (a setup failure, say): the back-off goes on as if not found.
     */
    fun onFindDone(found: String?): List<Action> {
        val s = state as? State.Searching ?: return emptyList()
        if (found == null || found == s.address || windowOver(0)) return listOf(backOff(s.address, s.attempt))
        state = State.Connecting(found, s.attempt)
        return listOf(Action.Connect(found, s.attempt))
    }

    private fun findAllowed(): Boolean = lastFindAt.let { it == null || clock() - it >= findIntervalMs }

    /** True when a connect or scan starting [inMs] from now would start after the reconnect window. */
    private fun windowOver(inMs: Long): Boolean = lostAt.let { it != null && clock() + inMs - it > windowMs }

    /** Waits before reconnect [attempt], or gives up when that reconnect would start after the window. */
    private fun backOff(address: String, attempt: Int): Action {
        val delay = delayFor(attempt)
        if (windowOver(delay)) return giveUp(address)
        state = State.Waiting(address, attempt, delay)
        return Action.StartTimer(delay)
    }

    private fun giveUp(address: String): Action {
        val afterMs = clock() - (lostAt ?: clock())
        failures = 0
        lostAt = null
        state = State.Idle
        return Action.GiveUp(address, afterMs)
    }

    fun onTimer(): List<Action> {
        val s = state as? State.Waiting ?: return emptyList()
        state = State.Connecting(s.address, s.attempt)
        return listOf(Action.Connect(s.address, s.attempt))
    }

    /** User disconnect or forget: no more reconnects until the rider connects again. */
    fun disconnect(): List<Action> {
        val stop = stopFind()
        failures = 0
        lostAt = null
        state = if (state is State.BluetoothOff) State.BluetoothOff(null) else State.Idle
        return listOfNotNull(Action.CancelTimer, stop, Action.CloseLink)
    }

    fun onBluetoothOff(): List<Action> {
        if (state is State.BluetoothOff) return emptyList()
        val stop = stopFind()
        failures = 0
        lostAt = null
        state = State.BluetoothOff(address)
        return listOfNotNull(Action.CancelTimer, stop, Action.CloseLink)
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
