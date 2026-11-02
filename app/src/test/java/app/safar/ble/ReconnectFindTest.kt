package app.safar.ble

import app.safar.ble.ReconnectPolicy.Action
import app.safar.ble.ReconnectPolicy.State
import app.safar.protocol.ConnectTiming
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The search-by-name fallback of [ReconnectPolicy] for a bike whose address changed. */
class ReconnectFindTest {
    private val mac = "00:11:22:33:44:55"
    private val moved = "5A:11:22:33:44:77"
    private var clock = 0L
    private val p = ReconnectPolicy(clock = { clock })

    /** Connected, then the link drops and the first reconnect fails: one failure in a row. */
    private fun oneFailure() {
        p.connect(mac)
        p.onLinkReady()
        p.onLinkLost()
        p.onTimer()
        assertEquals(listOf(Action.CloseLink, Action.StartTimer(5_000)), p.onLinkLost())
        p.onTimer()
    }

    @Test
    fun twoFailedConnectsInARowSearchByName() {
        oneFailure()
        assertEquals(listOf(Action.CloseLink, Action.Find(mac)), p.onLinkLost())
        assertEquals(State.Searching(mac, 3), p.state)
        assertEquals(mac, p.address)
    }

    @Test
    fun aFailedFirstConnectCountsToo() {
        p.connect(mac)
        p.onLinkLost()
        p.onTimer()
        assertEquals(listOf(Action.CloseLink, Action.Find(mac)), p.onLinkLost())
    }

    @Test
    fun aDropOfAConnectedLinkIsNotAFailedConnect() {
        p.connect(mac)
        p.onLinkReady()
        assertEquals(listOf(Action.CloseLink, Action.StartTimer(2_000)), p.onLinkLost())
    }

    @Test
    fun foundConnectsToTheNewAddressAtOnce() {
        oneFailure()
        p.onLinkLost()
        assertEquals(listOf(Action.Connect(moved, 3)), p.onFindDone(moved))
        assertEquals(State.Connecting(moved, 3), p.state)
        assertEquals(emptyList(), p.onLinkReady())
        assertEquals(State.Connected(moved), p.state)
    }

    @Test
    fun notFoundGoesOnWithTheBackOff() {
        oneFailure()
        p.onLinkLost()
        assertEquals(listOf(Action.StartTimer(10_000)), p.onFindDone(null))
        assertEquals(State.Waiting(mac, 3, 10_000), p.state)
        assertEquals(listOf(Action.Connect(mac, 3)), p.onTimer())
        assertEquals(listOf(Action.CloseLink, Action.StartTimer(30_000)), p.onLinkLost(), "count restarts")
    }

    @Test
    fun foundAtTheSameAddressGoesOnWithTheBackOff() {
        oneFailure()
        p.onLinkLost()
        assertEquals(listOf(Action.StartTimer(10_000)), p.onFindDone(mac))
        assertEquals(State.Waiting(mac, 3, 10_000), p.state)
    }

    @Test
    fun atMostOneSearchPerMinute() {
        oneFailure()
        p.onLinkLost()
        p.onFindDone(null)
        clock = ConnectTiming.FIND_MIN_INTERVAL_MS - 1
        repeat(3) {
            p.onTimer()
            val lost = p.onLinkLost()
            assertTrue(lost.none { it is Action.Find }, "failure ${it + 1} inside the minute: $lost")
        }
        clock = ConnectTiming.FIND_MIN_INTERVAL_MS
        p.onTimer()
        assertEquals(listOf(Action.CloseLink, Action.Find(mac)), p.onLinkLost(), "two failures and a minute passed")
    }

    @Test
    fun aSuccessfulDirectConnectResetsTheFailureCount() {
        oneFailure()
        p.onLinkReady()
        p.onLinkLost()
        p.onTimer()
        assertEquals(listOf(Action.CloseLink, Action.StartTimer(5_000)), p.onLinkLost(), "one failure, no search")
    }

    @Test
    fun userDisconnectCancelsTheSearch() {
        oneFailure()
        p.onLinkLost()
        assertEquals(listOf(Action.CancelTimer, Action.StopFind, Action.CloseLink), p.disconnect())
        assertEquals(State.Idle, p.state)
        assertEquals(emptyList(), p.onFindDone(moved), "a late result is ignored")
        assertEquals(State.Idle, p.state)
    }

    @Test
    fun bluetoothOffCancelsTheSearchAndResumesTheSavedAddress() {
        oneFailure()
        p.onLinkLost()
        assertEquals(listOf(Action.CancelTimer, Action.StopFind, Action.CloseLink), p.onBluetoothOff())
        assertEquals(State.BluetoothOff(mac), p.state)
        assertEquals(emptyList(), p.onFindDone(moved))
        assertEquals(listOf(Action.Connect(mac, 0)), p.onBluetoothOn())
        clock = ConnectTiming.FIND_MIN_INTERVAL_MS
        assertEquals(listOf(Action.CloseLink, Action.StartTimer(2_000)), p.onLinkLost(), "count restarted")
    }

    @Test
    fun riderConnectDuringTheSearchStopsIt() {
        oneFailure()
        p.onLinkLost()
        assertEquals(listOf(Action.CancelTimer, Action.StopFind, Action.Connect(moved, 0)), p.connect(moved))
        assertEquals(State.Connecting(moved, 0), p.state)
    }
}
