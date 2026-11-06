package app.safar.e2e

import app.safar.core.ClusterReading
import app.safar.core.LogKind
import app.safar.protocol.ChecksumMode
import app.safar.protocol.Timing
import app.safar.protocol.decodeCluster
import app.safar.protocol.toHex
import app.safar.sim.BadFrames
import app.safar.sim.BikeRig
import app.safar.sim.CLOCK_BASE
import app.safar.sim.ClusterSpec
import app.safar.sim.SCOOTER_NAME
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Cluster frames '7' from the simulated bike into BikeState (docs 3.1-3.3). */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardTest {
    private val riding = ClusterSpec(
        speedKmh = 62,
        odometerKm = 12_280,
        tripAKm = 103.4,
        tripBKm = 481.1,
        fuelBars = 3,
        byte23 = '4'.code,
        byte25 = 0x1E,
    )

    @Test
    fun firstFrameIgnoresFuelAndLaterFramesDecodeEveryField() = runTest {
        val rig = BikeRig(this)
        rig.pairFromScan()
        val firstAt = rig.bike.notifyEnabledAt.single() + rig.bike.firstClusterDelayMs
        rig.advance(firstAt - rig.time)

        val first = rig.manager.state.value
        assertEquals(1, first.framesReceived)
        assertEquals(
            ClusterReading(0, 12_261, 84.6, 462.3, null, 'N', 'N'.code, 0x1D, CLOCK_BASE + firstAt),
            first.cluster,
            "first frame after connect: fuel ignored",
        )
        assertEquals("first frame: fuel ignored", rig.recording.rx().single().note)

        rig.bike.cluster = riding
        rig.advance(Timing.CLUSTER_PERIOD_MS)
        val second = rig.manager.state.value
        assertEquals(2, second.framesReceived)
        val expected = ClusterReading(62, 12_280, 103.4, 481.1, 3, '4', '4'.code, 0x1E, CLOCK_BASE + rig.time)
        assertEquals(expected, second.cluster)
        assertEquals(expected, second.lastCluster)
        val decoded = decodeCluster(rig.bike.notified.last(), ChecksumMode.SUM)
        assertEquals(Triple(62, 12_280, 3), Triple(decoded.speedKmh, decoded.odometerKm, decoded.fuelBars))

        rig.advance(3 * Timing.CLUSTER_PERIOD_MS)
        assertEquals(5, rig.manager.state.value.framesReceived)
        assertEquals(rig.bike.notified.map(::toHex), rig.recording.rx().map { toHex(it.bytes) }, "every RX logged")
        assertTrue(rig.recording.rx().all { it.ok })
    }

    @Test
    fun firstReadingOfANewBikeSeedsServiceReminders() = runTest {
        val rig = BikeRig(this)
        rig.pairFromScan()
        rig.advance(1_000)
        val items = rig.service.items.value
        assertTrue(items.isNotEmpty(), "service items seeded")
        assertTrue(items.all { it.lastDoneOdoKm == 12_261 }, "counted from the first odometer reading")
    }

    @Test
    fun corruptFramesAreDroppedAndLoggedWithoutChangingState() = runTest {
        val rig = BikeRig(this)
        rig.pairFromScan()
        rig.advance(1_000)
        val before = rig.manager.state.value
        val valid = riding.encode(ChecksumMode.SUM)
        val bad = listOf(
            BadFrames.badChecksum(valid),
            BadFrames.truncated(valid),
            BadFrames.tooLong(valid),
            BadFrames.badStart(valid),
            BadFrames.nonDigitSpeed(valid, ChecksumMode.SUM),
            riding.encode(ChecksumMode.INVERTED),
        )
        bad.forEach(rig.bike::inject)
        rig.advance(100)

        val after = rig.manager.state.value
        assertSame(before.cluster, after.cluster, "state unchanged")
        assertEquals(before.framesReceived, after.framesReceived)
        val logged = rig.recording.rx().drop(1)
        assertEquals(bad.map(::toHex), logged.map { toHex(it.bytes) })
        assertTrue(logged.none { it.ok }, "every corrupt frame is logged as invalid")
        assertTrue(logged[0].note.orEmpty().startsWith("checksum"), "reason: ${logged[0].note}")
        assertEquals("length 20, expected 30", logged[1].note)
        assertEquals("length 31, expected 30", logged[2].note)
        assertTrue(logged[3].note.orEmpty().startsWith("start byte"), "reason: ${logged[3].note}")
        assertTrue(logged[4].note.orEmpty().contains("speed"), "reason: ${logged[4].note}")
        assertTrue(logged[5].note.orEmpty().startsWith("checksum"), "inverted checksum on a sum bike")

        rig.bike.cluster = riding
        rig.advance(Timing.CLUSTER_PERIOD_MS)
        assertEquals(62, rig.manager.state.value.cluster?.speedKmh, "the next valid frame still decodes")
    }

    @Test
    fun scooterNameUsesTheInvertedChecksum() = runTest {
        val rig = BikeRig(this, bikeName = SCOOTER_NAME)
        assertEquals(ChecksumMode.INVERTED, rig.bike.mode)
        rig.pairFromScan()
        rig.bike.cluster = riding.copy(fuelBars = 5, byte23 = 'D'.code)
        rig.advance(Timing.CLUSTER_PERIOD_MS + 1_000)

        val reading = rig.manager.state.value.cluster
        assertEquals(Triple(62, 5, 'D'), Triple(reading?.speedKmh, reading?.fuelBars, reading?.gear))
        assertEquals(30, reading?.engineTempC, "byte 25 is the engine temperature in °C")
        rig.bike.inject(riding.encode(ChecksumMode.SUM))
        rig.advance(10)
        assertEquals(LogKind.RX, rig.recording.frames.last().kind)
        assertEquals(false, rig.recording.frames.last().ok, "a sum checksum is wrong for 'SA' names")

        val rider = rig.writes('6').first().bytes
        assertEquals("ALEX", rig.frames.rider(rider).name, "TX frames use the inverted checksum too")
        rig.frames.status(rig.writes('3').first().bytes)
    }

    @Test
    fun nonLetterByte23GivesNoGear() = runTest {
        val rig = BikeRig(this)
        rig.bike.cluster = riding.copy(byte23 = 0x00)
        rig.pairFromScan()
        rig.advance(1_000)
        val reading = rig.manager.state.value.cluster
        assertNull(reading?.gear)
        assertEquals(0, reading?.raw23)
    }
}
