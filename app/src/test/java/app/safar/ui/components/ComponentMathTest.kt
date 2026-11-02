package app.safar.ui.components

import org.junit.Test
import kotlin.test.assertEquals

class ComponentMathTest {
    @Test
    fun `slider maps touch position to whole values within range`() {
        assertEquals(10, sliderValueAt(-20f, 300f, 10..299))
        assertEquals(10, sliderValueAt(0f, 300f, 10..299))
        assertEquals(155, sliderValueAt(150f, 300f, 10..300))
        assertEquals(299, sliderValueAt(300f, 300f, 10..299))
        assertEquals(299, sliderValueAt(999f, 300f, 10..299))
        assertEquals(5, sliderValueAt(10f, 0f, 5..120))
    }

    @Test
    fun `fuel bars rise like a ruler and the last bar is full height`() {
        assertEquals(0.45f + 0.55f / 6, fuelBarFraction(0, 6), 1e-6f)
        assertEquals(1f, fuelBarFraction(5, 6), 1e-6f)
        assertEquals(1f, fuelBarFraction(4, 5), 1e-6f)
    }
}
