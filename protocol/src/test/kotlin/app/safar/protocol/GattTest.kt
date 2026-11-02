package app.safar.protocol

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GattTest {
    @Test
    fun `UUID objects match the string constants`() {
        assertEquals(SERVICE_UUID, GattUuids.SERVICE.toString())
        assertEquals(TX_CHARACTERISTIC_UUID, GattUuids.TX_CHARACTERISTIC.toString())
        assertEquals(RX_CHARACTERISTIC_UUID, GattUuids.RX_CHARACTERISTIC.toString())
        assertEquals(CCCD_UUID, GattUuids.CCCD.toString())
        assertEquals(0xFFF0, (GattUuids.SERVICE.mostSignificantBits ushr 32).toInt())
    }

    @Test
    fun `bike MTU fits one frame`() {
        assertEquals(FRAME_LENGTH + 3, MIN_MTU_FOR_FRAME)
        assertEquals(true, BIKE_MTU >= MIN_MTU_FOR_FRAME)
    }

    @Test
    fun `v7_6 timing values`() {
        assertEquals(1000L, Timing.STATUS_PERIOD_MS)
        assertEquals(250L, Timing.NAVIGATION_PERIOD_MS)
        assertEquals(5000L, Timing.CLUSTER_PERIOD_MS)
        assertEquals(2, Timing.MISSED_CALL_REPEAT)
        assertEquals(200L, Timing.MISSED_CALL_GAP_MS)
    }

    @Test
    fun `reconnect back-off is 2 s, 5 s, 10 s, then 30 s`() {
        assertEquals(listOf(2_000L, 5_000L, 10_000L, 30_000L, 30_000L, 30_000L), (1..6).map(Timing::reconnectDelayMs))
        assertFailsWith<IllegalArgumentException> { Timing.reconnectDelayMs(0) }
    }
}
