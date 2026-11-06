package app.safar.ble

import app.safar.ble.ReconnectPolicy.Action
import app.safar.ble.ReconnectPolicy.State
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Reconnects stop 2 minutes after the link was lost (ConnectTiming.RECONNECT_WINDOW_MS). */
class ReconnectWindowTest {
    private val mac = "00:11:22:33:44:55"
    private val other = "00:11:22:33:44:66"
    private var t = 0L

    private fun connected(findAfter: Int = Int.MAX_VALUE, delayFor: ((Int) -> Long)? = null): ReconnectPolicy {
        val p = if (delayFor == null) {
            ReconnectPolicy(clock = { t }, findAfter = findAfter, findIntervalMs = 0)
        } else {
            ReconnectPolicy(clock = { t }, findAfter = findAfter, findIntervalMs = 0, delayFor = delayFor)
        }
        p.connect(mac)
        p.onLinkReady()
        return p
    }

    @Test
    fun connectsThatTimeOutGiveUpOnceTheNextWouldStartAfterTwoMinutes() {
        val p = connected()
        assertEquals(listOf(Action.CloseLink, Action.StartTimer(2_000)), p.onLinkLost()) // lost at 0
        // Each attempt starts when its wait ends and times out 20 s later.
        val waits = listOf(2_000L, 5_000L, 10_000L, 30_000L)
        var start = 0L
        waits.zipWithNext().forEach { (wait, next) ->
            start += wait
            t = start
            p.onTimer()
            t = start + 20_000
            assertEquals(listOf(Action.CloseLink, Action.StartTimer(next)), p.onLinkLost(), "fail at $t")
            start = t
        }
        // Attempt 4 started at 107 s and fails at 127 s: attempt 5 would start at 157 s.
        t = start + 30_000
        assertEquals(listOf(Action.Connect(mac, 4)), p.onTimer())
        t += 20_000
        assertEquals(listOf(Action.CloseLink, Action.GiveUp(mac, 127_000)), p.onLinkLost())
        assertEquals(State.Idle, p.state)
        assertNull(p.address)
        assertEquals(emptyList(), p.onTimer())
        assertEquals(emptyList(), p.onLinkLost())
    }

    @Test
    fun aReconnectMayStartAtExactlyTwoMinutesButNotLater() {
        val p = connected(delayFor = { 2_000L })
        p.onLinkLost()
        t = 118_000
        p.onTimer()
        assertEquals(listOf(Action.CloseLink, Action.StartTimer(2_000)), p.onLinkLost(), "starts at 120 s")
        t = 120_000
        p.onTimer()
        t = 120_001
        assertEquals(listOf(Action.CloseLink, Action.GiveUp(mac, 120_001)), p.onLinkLost())
    }

    @Test
    fun noScanByNameStartsAfterTheWindow() {
        val p = connected(findAfter = 1)
        p.onLinkLost()
        p.onTimer()
        t = 125_000
        assertEquals(listOf(Action.CloseLink, Action.GiveUp(mac, 125_000)), p.onLinkLost())
    }

    @Test
    fun bikeFoundAtANewAddressAfterTheWindowIsNotConnected() {
        val p = connected(findAfter = 1)
        p.onLinkLost()
        p.onTimer()
        t = 115_000
        assertEquals(listOf(Action.CloseLink, Action.Find(mac)), p.onLinkLost())
        t = 123_000
        assertEquals(listOf(Action.GiveUp(mac, 123_000)), p.onFindDone(other))
        assertEquals(State.Idle, p.state)
    }

    @Test
    fun aSuccessfulReconnectStartsANewWindow() {
        val p = connected()
        p.onLinkLost()
        t = 100_000
        p.onTimer()
        p.onLinkReady()
        t = 110_000
        p.onLinkLost() // lost again: the window now runs from 110 s
        p.onTimer()
        t = 200_000
        assertEquals(listOf(Action.CloseLink, Action.StartTimer(5_000)), p.onLinkLost())
    }

    @Test
    fun bluetoothBackOnStartsANewWindow() {
        val p = connected()
        p.onLinkLost()
        t = 100_000
        p.onBluetoothOff()
        t = 300_000
        assertEquals(listOf(Action.Connect(mac, 0)), p.onBluetoothOn())
        t = 320_000
        assertEquals(listOf(Action.CloseLink, Action.StartTimer(2_000)), p.onLinkLost())
    }

    @Test
    fun riderConnectAfterGivingUpStartsOver() {
        val p = connected()
        p.onLinkLost()
        p.onTimer()
        t = 130_000
        p.onLinkLost()
        assertEquals(State.Idle, p.state)
        assertEquals(listOf(Action.CancelTimer, Action.Connect(mac, 0)), p.connect(mac))
        t = 150_000
        assertEquals(listOf(Action.CloseLink, Action.StartTimer(2_000)), p.onLinkLost())
    }
}
