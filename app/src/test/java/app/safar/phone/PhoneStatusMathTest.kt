package app.safar.phone

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhoneStatusMathTest {
    @Test
    fun signalLevelToBars() {
        assertEquals(listOf(0, 0, 1, 2, 3), (0..4).map { signalBars(it, airplane = false, simReady = true) })
        assertEquals(0, signalBars(4, airplane = true, simReady = true))
        assertEquals(0, signalBars(4, airplane = false, simReady = false))
        assertEquals(0, signalBars(null, airplane = false, simReady = true))
        assertEquals(3, signalBars(9, airplane = false, simReady = true))
    }

    @Test
    fun batteryPercentFromLevelAndScale() {
        assertEquals(68, batteryPercent(68, 100))
        assertEquals(50, batteryPercent(128, 256))
        assertNull(batteryPercent(-1, 100))
        assertNull(batteryPercent(50, 0))
    }

    @Test
    fun chargingFromStatusOrPlug() {
        assertTrue(isCharging(status = 2, plugged = 0))
        assertTrue(isCharging(status = 5, plugged = 0))
        assertTrue(isCharging(status = 3, plugged = 1))
        assertFalse(isCharging(status = 3, plugged = 0))
    }
}
