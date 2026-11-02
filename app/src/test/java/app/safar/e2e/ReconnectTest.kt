package app.safar.e2e

import app.safar.ble.WriteQueue
import app.safar.core.LinkState
import app.safar.data.SavedBikeRecord
import app.safar.protocol.ConnectTiming
import app.safar.protocol.Timing
import app.safar.sim.BIKE_ADDRESS
import app.safar.sim.BikeRig
import app.safar.sim.CLOCK_BASE
import app.safar.sim.MOTO_NAME
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** MTU check, link drops and the reconnect back-off against the simulated bike (docs 2.2, 2.4, 2.5, 12.4 R3). */
@OptIn(ExperimentalCoroutinesApi::class)
class ReconnectTest {
    private fun TestScope.connectedRig(): BikeRig {
        val rig = BikeRig(this, savedBike = SavedBikeRecord(MOTO_NAME, BIKE_ADDRESS))
        rig.start()
        rig.awaitConnected()
        rig.advance(3_000)
        return rig
    }

    @Test
    fun mtu23FailsTheSetupCleanlyAndStartsReconnecting() = runTest {
        val rig = BikeRig(this, savedBike = SavedBikeRecord(MOTO_NAME, BIKE_ADDRESS))
        rig.bike.mtu = 23
        rig.start()
        rig.advance(1_000)

        val failed = rig.recording.named("Connect failed").single()
        assertEquals(mapOf("reason" to "MTU 23 is below 33"), failed.detail)
        val failedAt = failed.atMillis - CLOCK_BASE
        assertEquals(LinkState.Reconnecting(1, CLOCK_BASE + failedAt + 2_000), rig.link)
        assertEquals(emptyList(), rig.recording.tx(), "no write attempted")
        assertEquals(emptyList(), rig.bike.notifyEnabledAt, "notify never enabled")
        assertTrue(rig.radio.links.single().closed, "GATT closed")
        assertEquals(true, rig.manager.serviceWanted.value)

        rig.advance(2_000)
        assertEquals(listOf(0L, failedAt + 2_000), rig.bike.connectRequests)
        rig.advance(1_000)
        assertEquals(LinkState.Reconnecting(2, CLOCK_BASE + 2 * failedAt + 2_000 + 5_000), rig.link)
        assertEquals(emptyList(), rig.recording.tx())
    }

    @Test
    fun dropBacksOffTwoFiveTenThenThirtySecondsToTheSavedAddress() = runTest {
        val rig = connectedRig()
        val dropAt = rig.time
        rig.bike.powerOff()
        rig.advance(0)

        assertEquals(LinkState.Reconnecting(1, CLOCK_BASE + dropAt + 2_000), rig.link)
        val disconnected = rig.recording.named("Disconnected").single()
        assertEquals(mapOf("status" to "8", "reason" to "supervision timeout"), disconnected.detail)
        assertEquals(mapOf("reason" to "supervision timeout"), rig.recording.named("Link ended").single().detail)

        // Each attempt to the switched-off bike ends with the 20 s connect timeout, then the next back-off.
        val timeout = ConnectTiming.CONNECT_TIMEOUT_MS
        var at = dropAt
        val expectedRequests = mutableListOf<Long>()
        Timing.RECONNECT_BACKOFF_MS.plus(30_000L).forEachIndexed { index, backoff ->
            at += backoff
            expectedRequests += at
            rig.advance(at - rig.time - 1)
            assertTrue(rig.link is LinkState.Reconnecting, "waiting before attempt ${index + 1}")
            rig.advance(1)
            assertEquals(LinkState.Connecting(MOTO_NAME), rig.link, "attempt ${index + 1}")
            at += timeout
            rig.advance(timeout)
            val next = Timing.reconnectDelayMs(index + 2)
            assertEquals(LinkState.Reconnecting(index + 2, CLOCK_BASE + at + next), rig.link)
        }
        assertEquals(listOf(0L) + expectedRequests, rig.bike.connectRequests)
        assertTrue(rig.radio.opened.all { it == BIKE_ADDRESS }, "direct connect to the saved address")
        assertEquals(0, rig.radio.scanner.starts, "no scan while reconnecting")
        assertEquals(emptyList(), rig.txAfter(dropAt), "no write attempted while the link is down")
    }

    @Test
    fun aWriteCutOffByADropIsLoggedAsFailedAtTheDrop() = runTest {
        val rig = BikeRig(this, savedBike = SavedBikeRecord(MOTO_NAME, BIKE_ADDRESS))
        rig.start()
        val t0 = rig.awaitConnected()
        // The first rider frame goes out at t0; drop the link while it waits for its ack.
        rig.advance(rig.bike.ackDelayMs / 2)
        val dropAt = rig.time
        rig.bike.dropLink()
        rig.advance(0)

        val cut = rig.bike.writes.single()
        assertEquals(t0, cut.atMillis)
        assertNull(cut.ackedAtMillis, "the bike never acknowledged it")
        val logged = rig.recording.tx().single()
        assertContentEquals(cut.bytes, logged.bytes)
        assertEquals(CLOCK_BASE + dropAt, logged.atMillis, "logged at the drop, not after the ack delay")
        assertEquals(false, logged.ok)
        // markLost sets the link state before it fails the write, so LinkRunner resumes first and cancels the
        // queue; the serial dispatcher keeps that order on a phone too. Hence "cancelled", not "status -1".
        assertEquals("write failed: ${WriteQueue.CANCELLED}", logged.note)
        rig.advance(Timing.STATUS_PERIOD_MS)
        assertEquals(1, rig.recording.tx().size, "no second outcome for the cut-off frame")
    }

    @Test
    fun reconnectSucceedsWhenTheBikeComesBack() = runTest {
        val rig = connectedRig()
        rig.bike.powerOff()
        rig.advance(10_000)
        rig.bike.powerOn()
        val back = rig.awaitConnected()

        assertTrue(back >= rig.bike.connectRequests.last())
        rig.advance(100)
        val riders = rig.writes('6', from = back)
        assertEquals('R', rig.frames.rider(riders.single().bytes).connection, "reconnect to the saved bike")
        assertEquals(2, rig.bike.notifyEnabledAt.size)
        rig.advance(Timing.CLUSTER_PERIOD_MS)
        assertTrue(rig.manager.state.value.framesReceived >= 1, "frames flow again")
        assertEquals(0, rig.radio.scanner.starts)
    }

    @Test
    fun userDisconnectStopsReconnecting() = runTest {
        val rig = connectedRig()
        rig.bike.dropLink()
        rig.advance(500)
        assertTrue(rig.link is LinkState.Reconnecting)

        rig.manager.disconnect()
        rig.advance(0)
        assertEquals(LinkState.Idle, rig.link)
        val requests = rig.bike.connectRequests.size
        rig.advance(120_000)
        assertEquals(requests, rig.bike.connectRequests.size, "no reconnect after a user disconnect")
        assertEquals(false, rig.manager.serviceWanted.value)
    }

    @Test
    fun userDisconnectWhileConnectedClosesTheLink() = runTest {
        val rig = connectedRig()
        val disconnectAt = rig.time
        rig.manager.disconnect()
        rig.advance(0)
        assertEquals(LinkState.Idle, rig.link)
        assertTrue(rig.radio.links.single().closed)
        rig.advance(60_000)
        assertEquals(emptyList(), rig.txAfter(disconnectAt), "no write attempted after the disconnect")
        assertEquals(1, rig.bike.connectRequests.size)
    }

    @Test
    fun bluetoothOffStopsReconnectingAndBluetoothOnResumes() = runTest {
        val rig = connectedRig()
        rig.bike.dropLink()
        rig.advance(500)
        rig.radio.bluetooth.set(false)
        rig.advance(0)
        assertEquals(LinkState.BluetoothOff, rig.link)
        val requests = rig.bike.connectRequests.size
        rig.advance(120_000)
        assertEquals(requests, rig.bike.connectRequests.size, "no reconnect while Bluetooth is off")
        assertEquals(true, rig.manager.serviceWanted.value, "the address is kept for Bluetooth on")

        rig.radio.bluetooth.set(true)
        rig.advance(0)
        assertEquals(LinkState.Connecting(MOTO_NAME), rig.link, "resumes at once, no back-off")
        val back = rig.awaitConnected()
        assertEquals(requests + 1, rig.bike.connectRequests.size)
        rig.advance(100)
        assertEquals('R', rig.frames.rider(rig.writes('6', from = back).single().bytes).connection)
    }

    @Test
    fun bluetoothOffWhileConnectedClosesTheLink() = runTest {
        val rig = connectedRig()
        rig.radio.bluetooth.set(false)
        rig.advance(0)
        assertEquals(LinkState.BluetoothOff, rig.link)
        assertTrue(rig.radio.links.single().closed)
        assertEquals(false, rig.bike.connected)
    }
}
