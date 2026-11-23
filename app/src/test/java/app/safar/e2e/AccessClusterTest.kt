package app.safar.e2e

import app.safar.core.LinkState
import app.safar.data.SavedBikeRecord
import app.safar.protocol.GattProperty
import app.safar.protocol.uuid16
import app.safar.sim.BIKE_ADDRESS
import app.safar.sim.BikeRig
import app.safar.sim.SCOOTER_NAME
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Clusters that differ from the V-Strom SX, connected the way the original app (v2.15) does (docs 2.6): the TX
 * write type from its properties, write errors that keep the link, the frame channel by position, and a
 * Device Information service that does not answer. The scooter is an Access 125 name (inverted checksum).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccessClusterTest {
    private fun TestScope.scooter(): BikeRig = BikeRig(this, bikeName = SCOOTER_NAME, savedBike = SavedBikeRecord(SCOOTER_NAME, BIKE_ADDRESS))

    @Test
    fun writeWithoutResponseTxGetsEveryFrameAndKeepsTheLinkForTenMinutes() = runTest {
        val rig = scooter()
        rig.bike.txProperties = GattProperty.WRITE_NO_RESPONSE
        rig.start()
        val t0 = rig.awaitConnected()
        rig.advance(600_000)

        assertEquals(LinkState.Connected, rig.link)
        assertEquals(1, rig.bike.connectRequests.size, "never reconnected")
        val channel = rig.recording.named("Frame channel").single().detail
        assertEquals("without response", channel["write"])
        assertEquals("fff1 write-no-response", channel["tx"])
        assertTrue(rig.recording.tx().all { it.ok }, "every write done")
        assertTrue(rig.writes('3', from = t0).size >= 590, "a status frame every second: ${rig.writes('3').size}")
        assertTrue(rig.manager.state.value.framesReceived >= 110, "cluster frames every 5 s")
        assertEquals(emptyList(), rig.recording.named("Write queue stalled"))
    }

    @Test
    fun writesTheBikeRejectsAreLoggedButNeverDropTheLink() = runTest {
        val rig = scooter()
        rig.bike.rejectsWrites = true
        rig.start()
        rig.awaitConnected()
        rig.advance(120_000)

        assertEquals(LinkState.Connected, rig.link, "the original app only logs write errors")
        assertEquals(1, rig.bike.connectRequests.size)
        assertEquals(emptyList(), rig.recording.named("Write queue stalled"))
        val rejects = rig.recording.named("Bike rejects writes").single()
        assertEquals(mapOf("status" to "3", "inRow" to "3"), rejects.detail)
        assertTrue(rig.recording.tx().all { it.note == "write rejected: GATT status 3" })
        assertTrue(rig.manager.state.value.framesReceived >= 20, "cluster frames still arrive")
    }

    @Test
    fun aClusterWithOtherUuidsIsFoundByTheOriginalAppsPositions() = runTest {
        val rig = scooter()
        rig.bike.clusterService = uuid16(0xFFE0)
        rig.bike.txUuid = uuid16(0xFFE1)
        rig.bike.rxUuid = uuid16(0xFFE2)
        rig.bike.txProperties = GattProperty.WRITE or GattProperty.WRITE_NO_RESPONSE
        rig.start()
        rig.awaitConnected()
        rig.advance(10_000)

        val channel = rig.recording.named("Frame channel").single().detail
        assertEquals(mapOf("lookup" to "position", "service" to "ffe0", "tx" to "ffe1 write-no-response write",
            "rx" to "ffe2 notify", "write" to "without response"), channel)
        val layout = rig.recording.named("GATT layout").single().detail["services"].orEmpty()
        assertTrue(layout.endsWith("ffe0 [ffe1 write-no-response write, ffe2 notify]"), layout)
        assertTrue(rig.writes('6').isNotEmpty() && rig.writes('3').size >= 7, "frames go to FFE1")
        assertTrue(rig.manager.state.value.framesReceived >= 2, "cluster frames come from FFE2")
    }

    @Test
    fun aFailedDeviceInformationReadStopsAtOnceAndIsNotTriedAgain() = runTest {
        val rig = scooter()
        rig.bike.deviceInfoFails = true
        rig.start()
        val t0 = rig.awaitConnected()
        rig.advance(5_000)

        assertEquals(1, rig.bike.deviceInfoReads.size, "no read after the first failure")
        assertTrue(rig.bike.deviceInfoReads.single() > t0, "read after the session started")
        assertEquals(LinkState.Connected, rig.link)
        assertEquals(1, rig.recording.named("Device information read failed").size)

        rig.bike.dropLink()
        rig.awaitConnected()
        rig.advance(5_000)
        assertEquals(1, rig.bike.deviceInfoReads.size, "skipped on the reconnect")
        assertEquals(1, rig.recording.named("Device information skipped").size)
        assertTrue(rig.manager.state.value.framesReceived >= 1)
    }
}
