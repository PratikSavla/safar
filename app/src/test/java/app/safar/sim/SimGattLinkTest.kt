package app.safar.sim

import app.safar.ble.WriteOutcome
import app.safar.protocol.GattUuids
import app.safar.sim.fakes.RecordingLog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The fake link answers writes the way GattClient does (docs 2.3). */
@OptIn(ExperimentalCoroutinesApi::class)
class SimGattLinkTest {
    private val frame = ByteArray(30) { it.toByte() }

    private suspend fun TestScope.connectedLink(): Pair<FakeBikePeripheral, SimGattLink> {
        val bike = FakeBikePeripheral(MOTO_NAME, BIKE_ADDRESS, backgroundScope) { testScheduler.currentTime }
        val link = SimGattLink(bike, RecordingLog { testScheduler.currentTime })
        assertEquals(true, link.connect(timeoutMs = 20_000))
        return bike to link
    }

    @Test
    fun writeCompletesWithTheBikesAck() = runTest {
        val (bike, link) = connectedLink()
        val start = testScheduler.currentTime
        val outcome = link.write(GattUuids.SERVICE, GattUuids.TX_CHARACTERISTIC, frame)
        assertEquals(WriteOutcome.Ok, outcome)
        assertEquals(start + bike.ackDelayMs, testScheduler.currentTime)
        assertEquals(start + bike.ackDelayMs, bike.writes.single().ackedAtMillis)
    }

    @Test
    fun aBikeSideDropFailsTheWaitingWriteAtOnceWithStatusLocal() = runTest {
        val (bike, link) = connectedLink()
        val write = async { link.write(GattUuids.SERVICE, GattUuids.TX_CHARACTERISTIC, frame) }
        advanceTimeBy(bike.ackDelayMs / 2)
        runCurrent()
        val dropAt = testScheduler.currentTime
        bike.dropLink()
        runCurrent()

        assertEquals(true, write.isCompleted, "no wait for the ack delay")
        assertEquals(WriteOutcome.Failed("status -1"), write.await())
        assertEquals(dropAt, testScheduler.currentTime)
        advanceTimeBy(bike.ackDelayMs)
        runCurrent()
        assertNull(bike.writes.single().ackedAtMillis, "no ack after the drop")
    }
}
