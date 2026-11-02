package app.safar.e2e

import app.safar.ble.BikeSession
import app.safar.core.DeviceInfo
import app.safar.data.SavedBikeRecord
import app.safar.data.Settings
import app.safar.protocol.BIKE_MTU
import app.safar.protocol.ChecksumMode
import app.safar.protocol.MIN_MTU_FOR_FRAME
import app.safar.protocol.Timing
import app.safar.protocol.encodeRider
import app.safar.sim.BIKE_ADDRESS
import app.safar.sim.BikeRig
import app.safar.sim.MOTO_NAME
import app.safar.sim.TxFrames
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Connect, notify, rider frame and the first status frames, against the simulated cluster (docs 2.2, 6.2, 6.3). */
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectSequenceTest {
    @Test
    fun firstPairingSetsUpTheLinkAndSendsRiderF() = runTest {
        val rig = BikeRig(this, initialSettings = Settings(riderName = "  alex rider! "))
        val t0 = rig.pairFromScan()

        assertEquals(1, rig.radio.scanner.starts)
        assertEquals(listOf(BIKE_ADDRESS), rig.radio.opened)
        val state = rig.manager.state.value
        assertEquals(BIKE_MTU, state.mtu)
        assertTrue((state.mtu ?: 0) >= MIN_MTU_FOR_FRAME)
        assertEquals(
            DeviceInfo("Suzuki", "BM01", "A1B2C3D", "1.0.4", null, "2.1", "01 0d 00 00 01 01 00"),
            state.deviceInfo,
        )
        assertEquals(1, rig.bike.notifyEnabledAt.size)
        assertTrue(rig.bike.notifyEnabledAt.single() < t0, "notify is on before the first frame")

        rig.advance(2_500)
        val riders = rig.writes('6')
        assertEquals(List(BikeSession.RIDER_REPEAT) { t0 + it * BikeSession.RIDER_GAP_MS }, riders.map { it.atMillis })
        val expected = encodeRider("ALEX RIDER", firstConnection = true, mode = ChecksumMode.SUM)
        riders.forEach { assertContentEquals(expected, it.bytes) }
        assertEquals(TxFrames.Rider("ALEX RIDER", 'F'), rig.frames.rider(riders[0].bytes))
        assertEquals('6', rig.bike.writes.first().type, "rider frame is the first write")
        assertEquals(listOf(SavedBikeRecord(MOTO_NAME, BIKE_ADDRESS)), rig.savedStore.saves)
    }

    @Test
    fun reconnectToSavedBikeSendsRiderRWithoutScan() = runTest {
        val rig = BikeRig(this, savedBike = SavedBikeRecord(MOTO_NAME, BIKE_ADDRESS), initialSettings = Settings())
        rig.start()
        val t0 = rig.awaitConnected()

        assertEquals(0, rig.radio.scanner.starts)
        rig.advance((BikeSession.RIDER_REPEAT - 1) * BikeSession.RIDER_GAP_MS + rig.bike.ackDelayMs)
        val riders = rig.writes('6')
        assertEquals(List(BikeSession.RIDER_REPEAT) { t0 + it * BikeSession.RIDER_GAP_MS }, riders.map { it.atMillis })
        val expected = encodeRider("RIDER", firstConnection = false, mode = ChecksumMode.SUM)
        riders.forEach { assertContentEquals(expected, it.bytes) }
        assertEquals(TxFrames.Rider("RIDER", 'R'), rig.frames.rider(riders[0].bytes))
        rig.advance(BikeSession.RIDER_GAP_MS * 5)
        assertEquals(BikeSession.RIDER_REPEAT, rig.writes('6').size, "no rider frame after the repeats")
        assertEquals(emptyList(), rig.savedStore.saves, "the saved bike is not saved again")
    }

    @Test
    fun statusFramesFollowTheTimingPeriodWithTheClock() = runTest {
        val rig = BikeRig(this)
        val t0 = rig.pairFromScan()
        rig.advance(5_100)

        val status = rig.writes('3')
        val start = t0 + BikeSession.STATUS_START_DELAY_MS
        // At start the third rider frame is queued at the same moment and goes first (events before status).
        val ack = rig.bike.ackDelayMs
        val expectedTimes = listOf(start + ack) + (1..3).map { start + it * Timing.STATUS_PERIOD_MS }
        assertEquals(expectedTimes, status.map { it.atMillis })
        // Each frame is built when it is queued; 14:05:00 plus the virtual time, as hhmmss on a 12-hour clock.
        val queuedAt = (0..3).map { start + it * Timing.STATUS_PERIOD_MS }
        val expectedClocks = queuedAt.map { "0205%02d".format(java.util.Locale.ROOT, it / 1000) }
        assertEquals(expectedClocks, status.map { rig.frames.status(it.bytes).clock })
    }

    @Test
    fun statusFrameCarriesBatteryChargeSignalAndSpeedAlert() = runTest {
        val rig = BikeRig(this)
        val t0 = rig.pairFromScan()
        rig.advance(2_100)
        val first = rig.frames.status(rig.writes('3').last().bytes)
        assertEquals(TxFrames.Status('3', 'N', null, '3', first.clock, 'N', 'N'), first, "80 %, alert off: FF FF FF")

        rig.phone.set { it.copy(batteryPercent = 30, charging = true, signalBars = 1) }
        rig.settings.set { it.copy(speedAlertOn = true, speedAlertKmh = 100) }
        rig.advance(Timing.STATUS_PERIOD_MS)
        val second = rig.frames.status(rig.writes('3').last().bytes)
        assertEquals(Triple('1', 'Y', '1'), Triple(second.battery, second.charging, second.bars))
        assertEquals("100", second.speedAlert)

        rig.settings.set { it.copy(speedAlertKmh = 400) }
        rig.advance(Timing.STATUS_PERIOD_MS)
        assertEquals("299", rig.frames.status(rig.writes('3').last().bytes).speedAlert, "clamped to 10..299")

        rig.settings.set { it.copy(speedAlertKmh = 3) }
        rig.phone.set { it.copy(batteryPercent = 10, signalBars = 0) }
        rig.advance(Timing.STATUS_PERIOD_MS)
        val last = rig.frames.status(rig.writes('3').last().bytes)
        assertEquals(Triple('0', "010", '0'), Triple(last.battery, last.speedAlert, last.bars))
        assertTrue(rig.writes('3').all { it.atMillis >= t0 + BikeSession.STATUS_START_DELAY_MS })
    }

    @Test
    fun scooterSpeedAlertIsClampedToItsRange() = runTest {
        val rig = BikeRig(this, bikeName = "SBS21A1B2C3D", initialSettings = Settings(speedAlertOn = true, speedAlertKmh = 150))
        rig.pairFromScan()
        rig.advance(2_100)
        assertEquals("120", rig.frames.status(rig.writes('3').last().bytes).speedAlert, "Avenis range 5..120")
    }

    @Test
    fun onlyOneWriteIsInFlightAtATime() = runTest {
        val rig = BikeRig(this)
        rig.bike.ackDelayMs = 60
        rig.pairFromScan()
        rig.alerts.callRinging("Alex", app.safar.core.AlertSource.PHONE)
        rig.alerts.messageReceived("Rider")
        rig.advance(8_000)

        assertEquals(1, rig.bike.maxWritesInFlight)
        rig.bike.writes.zipWithNext().forEach { (a, b) ->
            val ack = a.ackedAtMillis ?: Long.MAX_VALUE
            assertTrue(b.atMillis >= ack, "write at ${b.atMillis} before the ack at ${a.ackedAtMillis}")
        }
        assertTrue(rig.bike.writes.size >= 20, "enough traffic to contend: ${rig.bike.writes.size} writes")
    }
}
