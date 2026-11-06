package app.safar.e2e

import app.safar.core.LinkState
import app.safar.data.SavedBikeRecord
import app.safar.protocol.ConnectTiming
import app.safar.sim.BIKE_ADDRESS
import app.safar.sim.BikeRig
import app.safar.sim.CLOCK_BASE
import app.safar.sim.MOTO_NAME
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The saved bike comes back on a new address (a rotating private address, as Android advertisers use): two
 * direct connects to the saved address time out, a search by name finds it, Safar reconnects and saves it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddressChangeTest {
    private val newAddress = "5A:1B:2C:3D:4E:6F"

    private fun TestScope.connectedRig(): BikeRig {
        val rig = BikeRig(this, savedBike = SavedBikeRecord(MOTO_NAME, BIKE_ADDRESS))
        rig.start()
        rig.awaitConnected()
        rig.advance(3_000)
        return rig
    }

    /** Drops the link and moves the bike to [newAddress]; returns the virtual time of the drop. */
    private fun BikeRig.dropAndMove(): Long {
        val at = time
        bike.dropLink()
        bike.rotateAddress(newAddress)
        advance(0)
        return at
    }

    @Test
    fun reconnectsThroughASearchByNameAndSavesTheNewAddress() = runTest {
        val rig = connectedRig()
        val dropAt = rig.dropAndMove()
        val back = rig.awaitConnected()

        // 2 s back-off, 20 s timeout, 5 s back-off, 20 s timeout; then the search finds the bike at once.
        val timeout = ConnectTiming.CONNECT_TIMEOUT_MS
        assertEquals(dropAt + 2_000 + timeout + 5_000 + timeout, rig.findAt())
        assertEquals(listOf(BIKE_ADDRESS, BIKE_ADDRESS, BIKE_ADDRESS, newAddress), rig.radio.opened)
        assertEquals(1, rig.radio.findScanner.starts)
        assertEquals(0, rig.radio.scanner.starts, "the rider's scan is not touched")
        assertTrue(back - dropAt < 60_000, "back within a minute, took ${back - dropAt} ms")

        rig.advance(100)
        assertEquals(SavedBikeRecord(MOTO_NAME, newAddress), rig.savedStore.saved.value)
        assertEquals(listOf(SavedBikeRecord(MOTO_NAME, newAddress)), rig.savedStore.saves)
        val changed = rig.recording.named("Saved bike address changed").single()
        assertEquals(mapOf("bike" to MOTO_NAME, "oldAddress" to BIKE_ADDRESS, "newAddress" to newAddress), changed.detail)
        assertEquals(emptyList(), rig.recording.named("Bike saved"), "the same bike, not a new pairing")
        val rider = rig.writes('6', from = back).single()
        assertEquals('R', rig.frames.rider(rider.bytes).connection, "a reconnection, not a first pairing")
        assertEquals(LinkState.Connected, rig.link)
    }

    @Test
    fun theNextDropReconnectsDirectlyToTheNewAddress() = runTest {
        val rig = connectedRig()
        rig.dropAndMove()
        rig.awaitConnected()
        rig.advance(100)
        val opened = rig.radio.opened.size

        rig.bike.dropLink()
        rig.advance(0)
        val back = rig.awaitConnected()
        assertEquals(listOf(newAddress), rig.radio.opened.drop(opened), "direct, no search")
        assertEquals(1, rig.radio.findScanner.starts)
        rig.advance(100)
        assertEquals('R', rig.frames.rider(rig.writes('6', from = back).single().bytes).connection)
    }

    @Test
    fun withoutScanPermissionTheSearchIsSkippedAndLoggedOnce() = runTest {
        val rig = connectedRig()
        rig.radio.deniedScanPermission = "BLUETOOTH_SCAN"
        rig.dropAndMove()
        rig.advance(300_000)

        assertEquals(0, rig.radio.findScanner.starts)
        val logged = rig.recording.named("Search by name needs permission").single()
        assertEquals(mapOf("permission" to "BLUETOOTH_SCAN"), logged.detail)
        assertTrue(logged.error)
        assertTrue(rig.radio.opened.all { it == BIKE_ADDRESS }, "only direct connects")
        // First connect, then attempts at 2, 27, 57 and 107 s after the drop. Attempt 4 fails at 127 s and
        // attempt 5 would start at 157 s, past the 2 minute window: Safar gives up.
        assertEquals(5, rig.radio.opened.size, "${rig.radio.opened}")
        assertEquals(LinkState.Idle, rig.link)
    }

    @Test
    fun aSearchLeavesTheRidersRunningScanAlone() = runTest {
        val rig = connectedRig()
        rig.manager.startScan()
        rig.advance(0)
        rig.dropAndMove()
        rig.awaitConnected()

        assertEquals(1, rig.radio.scanner.starts)
        assertEquals(true, rig.radio.scanner.scanning.value, "the rider's scan still runs")
        assertEquals(true, rig.manager.state.value.scanning)
        assertEquals(1, rig.radio.findScanner.starts)
        assertEquals(false, rig.radio.findScanner.scanning.value, "the search stopped its own scan")
    }

    /** Virtual time of the search, from its log event. */
    private fun BikeRig.findAt(): Long = recording.named("Searching for the bike by name").single().atMillis - CLOCK_BASE
}
