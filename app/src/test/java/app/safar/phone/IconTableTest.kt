package app.safar.phone

import app.safar.protocol.IconMatcher
import app.safar.protocol.dHash
import app.safar.protocol.hashToHex
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IconTableTest {
    private val white = 0xFFFFFFFF.toInt()
    private val clear = 0x00000000

    /** [w] x [h] image, opaque white where [lit] is true, transparent elsewhere. */
    private fun image(w: Int, h: Int, lit: (x: Int, y: Int) -> Boolean) =
        IntArray(w * h) { i -> if (lit(i % w, i / w)) white else clear }

    @Test
    fun luminanceWeighsAlpha() {
        assertEquals(255, luminance(white))
        assertEquals(0, luminance(0x00FFFFFF))
        assertEquals(127, luminance(0x7FFFFFFF))
        assertEquals(76, luminance(0xFFFF0000.toInt()))
    }

    @Test
    fun reducesExactMultipleByBoxMean() {
        // 18 x 16: each output pixel is a 2 x 2 box. Left half lit.
        val gray = grayscale9x8(image(18, 16) { x, _ -> x < 8 }, 18, 16)
        assertEquals(72, gray.size)
        assertContentEquals(intArrayOf(255, 255, 255, 255, 0, 0, 0, 0, 0), gray.copyOfRange(0, 9))
        // Hash: per row only column 3 is brighter than its right neighbour -> 0b00010000 per row.
        assertEquals("1010101010101010", hashToHex(dHash(gray)))
    }

    @Test
    fun reducesOddSizeWithPartialBoxes() {
        // 113 x 113 like the Maps large icon; a vertical bar on the left third.
        val gray = grayscale9x8(image(113, 113) { x, _ -> x < 38 }, 113, 113)
        assertEquals(255, gray[0])
        assertEquals(0, gray[8])
        val boxAt3 = gray[3] // box x 37..49: one lit column of 13
        assertEquals(255 / 13, boxAt3)
    }

    @Test
    fun sameShapeAtDifferentSizesMatches() {
        val arrow = { w: Int, h: Int -> image(w, h) { x, y -> x < y * w / h } } // lit below the diagonal
        val small = iconHash(arrow(72, 64), 72, 64)
        val large = iconHash(arrow(113, 113), 113, 113)
        assertTrue(small != 0L)
        val matcher = IconMatcher(mapOf(small to 4))
        assertEquals(4, matcher.codeFor(large))
    }

    @Test
    fun parsesTableAndSkipsBadEntries() {
        val text = """{ "00000000000000ff": 4, "zz": 1, "0000000000000001": 46, "0000000000000002": "3" }"""
        val parsed = parseIconTable(text)
        assertEquals(mapOf(0xffL to 4), parsed.table)
        assertEquals(3, parsed.errors.size)
    }

    @Test
    fun emptyAndBrokenFiles() {
        assertTrue(parseIconTable("{}").table.isEmpty())
        assertTrue(parseIconTable("").errors.isEmpty())
        assertEquals(1, parseIconTable("[1, 2]").errors.size)
        assertEquals(1, parseIconTable("{ nope").errors.size)
    }

    @Test
    fun tableRoundTrips() {
        val table = mapOf(-1L to 45, 0x1234L to 7)
        val text = iconTableToJson(table)
        assertTrue(text.indexOf("0000000000001234") < text.indexOf("ffffffffffffffff"), "sorted keys")
        assertEquals(table, parseIconTable(text).table)
    }
}
