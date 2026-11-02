package app.safar.ui.components

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PaperShadowTest {
    private val blur = 72 // 26 dp at 2.75x
    private val corner = 17
    private val reach = blurReach(blur)

    @Test
    fun `a long axis is two corners and a stretched one-pixel middle`() {
        val (source, spans) = shadowSpans(shapeLen = 900, corner = corner, blur = blur, dstStart = -28)
        val side = blur + corner + reach
        assertEquals(2 * (corner + reach) + 1, source)
        assertEquals(listOf(Span(0, side, -28, side), Span(side, 1, -28 + side, 900 + 2 * blur - 2 * side), Span(side + 1, side, -28 + 900 + 2 * blur - side, side)), spans)
        // The three pieces cover the whole shadow, edge to edge, with no gap or overlap.
        assertEquals(900 + 2 * blur, spans.sumOf { it.dstLen })
        assertEquals(source + 2 * blur, spans.sumOf { it.srcLen })
    }

    @Test
    fun `a short axis is drawn from a bitmap of its own length`() {
        val short = 2 * (corner + reach)
        val (source, spans) = shadowSpans(shapeLen = short, corner = corner, blur = blur, dstStart = 5)
        assertEquals(short, source)
        assertEquals(listOf(Span(0, short + 2 * blur, 5, short + 2 * blur)), spans)
    }

    @Test
    fun `the straight middle is past the blur's reach`() {
        // Skia's blur sigma is 0.57735 r + 0.5; three sigma must fall inside the straight part, for any radius.
        for (r in listOf(1, 10, 28, 72, 83)) {
            val sigma = 0.57735f * r + 0.5f
            assertTrue(blurReach(r) >= 3 * sigma, "reach ${blurReach(r)} < 3 sigma ${3 * sigma} for r $r")
        }
    }
}
