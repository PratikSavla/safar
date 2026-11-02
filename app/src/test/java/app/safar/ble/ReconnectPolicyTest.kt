package app.safar.ble

import app.safar.ble.ReconnectPolicy.Action
import app.safar.ble.ReconnectPolicy.State
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReconnectPolicyTest {
    private val mac = "00:11:22:33:44:55"
    private val other = "00:11:22:33:44:66"

    private fun connected(): ReconnectPolicy = ReconnectPolicy().apply {
        connect(mac)
        onLinkReady()
    }

    @Test
    fun firstConnectStartsAttemptZero() {
        val p = ReconnectPolicy()
        assertEquals(listOf(Action.CancelTimer, Action.Connect(mac, 0)), p.connect(mac))
        assertEquals(State.Connecting(mac, 0), p.state)
        assertEquals(emptyList(), p.onLinkReady())
        assertEquals(State.Connected(mac), p.state)
    }

    @Test
    fun backOffIs2s5s10sThenEvery30s() {
        val p = connected()
        val delays = mutableListOf<Long>()
        repeat(6) { i ->
            val lost = p.onLinkLost()
            // Every second failure in a row searches by name first; nothing found, the back-off goes on.
            val actions = if (Action.Find(mac) in lost) lost + p.onFindDone(null) else lost
            val timer = actions.filterIsInstance<Action.StartTimer>().single()
            delays += timer.delayMs
            assertTrue(Action.CloseLink in actions)
            assertEquals(State.Waiting(mac, i + 1, timer.delayMs), p.state)
            assertEquals(listOf(Action.Connect(mac, i + 1)), p.onTimer())
            assertEquals(State.Connecting(mac, i + 1), p.state)
        }
        assertEquals(listOf(2_000L, 5_000L, 10_000L, 30_000L, 30_000L, 30_000L), delays)
    }

    @Test
    fun successResetsTheBackOff() {
        val p = connected()
        p.onLinkLost()
        p.onTimer()
        p.onLinkLost() // attempt 1 failed -> attempt 2 waits 5 s
        assertEquals(State.Waiting(mac, 2, 5_000), p.state)
        p.onTimer()
        p.onLinkReady()
        assertEquals(State.Connected(mac), p.state)
        assertEquals(listOf(Action.CloseLink, Action.StartTimer(2_000)), p.onLinkLost())
    }

    @Test
    fun failedFirstConnectRetriesAfter2s() {
        val p = ReconnectPolicy()
        p.connect(mac)
        assertEquals(listOf(Action.CloseLink, Action.StartTimer(2_000)), p.onLinkLost())
        assertEquals(State.Waiting(mac, 1, 2_000), p.state)
    }

    @Test
    fun userDisconnectStopsReconnectAndIgnoresLateEvents() {
        val p = connected()
        p.onLinkLost()
        assertEquals(listOf(Action.CancelTimer, Action.CloseLink), p.disconnect())
        assertEquals(State.Idle, p.state)
        assertNull(p.address)
        assertEquals(emptyList(), p.onTimer())
        assertEquals(emptyList(), p.onLinkLost())
        assertEquals(emptyList(), p.onLinkReady())
        assertEquals(State.Idle, p.state)
    }

    @Test
    fun bluetoothOffStopsAndOnResumesTheSameBike() {
        val p = connected()
        p.onLinkLost()
        assertEquals(listOf(Action.CancelTimer, Action.CloseLink), p.onBluetoothOff())
        assertEquals(State.BluetoothOff(mac), p.state)
        // Callbacks from the dying link do not start a back-off while Bluetooth is off.
        assertEquals(emptyList(), p.onLinkLost())
        assertEquals(emptyList(), p.onTimer())
        assertEquals(emptyList(), p.onBluetoothOff())
        assertEquals(listOf(Action.Connect(mac, 0)), p.onBluetoothOn())
        assertEquals(State.Connecting(mac, 0), p.state)
    }

    @Test
    fun bluetoothOnAfterUserDisconnectStaysIdle() {
        val p = connected()
        p.onBluetoothOff()
        p.disconnect()
        assertEquals(State.BluetoothOff(null), p.state)
        assertEquals(emptyList(), p.onBluetoothOn())
        assertEquals(State.Idle, p.state)
    }

    @Test
    fun connectWhileBluetoothOffWaitsForBluetoothOn() {
        val p = ReconnectPolicy()
        p.onBluetoothOff()
        assertEquals(emptyList(), p.connect(mac))
        assertEquals(State.BluetoothOff(mac), p.state)
        assertEquals(listOf(Action.Connect(mac, 0)), p.onBluetoothOn())
    }

    @Test
    fun connectToAnotherBikeClosesTheCurrentLink() {
        val p = connected()
        assertEquals(emptyList(), p.connect(mac), "same bike: nothing to do")
        assertEquals(listOf(Action.CancelTimer, Action.CloseLink, Action.Connect(other, 0)), p.connect(other))
        assertEquals(State.Connecting(other, 0), p.state)
    }

    @Test
    fun connectDuringBackOffConnectsNow() {
        val p = connected()
        p.onLinkLost()
        assertEquals(listOf(Action.CancelTimer, Action.Connect(mac, 0)), p.connect(mac))
        assertEquals(State.Connecting(mac, 0), p.state)
    }

    @Test
    fun customDelaysAreUsed() {
        val p = ReconnectPolicy(delayFor = { attempt -> attempt * 100L }, findAfter = Int.MAX_VALUE)
        p.connect(mac)
        p.onLinkLost()
        p.onTimer()
        assertEquals(listOf(Action.CloseLink, Action.StartTimer(200)), p.onLinkLost())
    }
}
