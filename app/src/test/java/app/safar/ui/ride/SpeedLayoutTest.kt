package app.safar.ui.ride

import androidx.compose.ui.unit.dp
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpeedLayoutTest {
    @Test
    fun `portrait keeps the unit under a full-size number`() {
        val l = speedLayout(360.dp, 420.dp, chars = 2, unitWidth = 62.dp, unitHeight = 28.dp)
        assertFalse(l.unitBeside)
        assertEquals(208.dp, l.numeral)
    }

    @Test
    fun `a short landscape band puts the unit beside the number so both fit`() {
        // Font scale 1.3: the unit is about 36 dp tall; the band left for the speed is 110 dp.
        val l = speedLayout(560.dp, 110.dp, chars = 2, unitWidth = 80.dp, unitHeight = 36.dp)
        assertTrue(l.unitBeside)
        assertEquals(93.5f, l.numeral.value, 0.01f)
        assertTrue(l.numeral <= 110.dp)
    }

    @Test
    fun `stacked, the number leaves room for the unit and the gap`() {
        val l = speedLayout(150.dp, 100.dp, chars = 3, unitWidth = 140.dp, unitHeight = 40.dp)
        assertFalse(l.unitBeside)
        assertTrue(l.numeral + 40.dp + 6.dp <= 100.dp)
    }
}
