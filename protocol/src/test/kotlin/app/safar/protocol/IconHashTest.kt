package app.safar.protocol

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class IconHashTest {
    /** Each row falls from left to right, so every pixel is brighter than its right neighbour. */
    private val falling = IntArray(72) { i -> 255 - (i % 9) * 10 }
    private val rising = IntArray(72) { i -> (i % 9) * 10 }

    @Test
    fun `dHash sets a bit where the left pixel is brighter`() {
        assertEquals(-1L, dHash(falling))
        assertEquals(0L, dHash(rising))
        val firstOnly = IntArray(72).also { it[0] = 1 }
        assertEquals(Long.MIN_VALUE, dHash(firstOnly)) // bit 63 = row 0, column 0
        val lastOnly = IntArray(72).also { it[7 * 9 + 7] = 1 }
        assertEquals(1L, dHash(lastOnly)) // bit 0 = row 7, column 7
    }

    @Test
    fun `equal neighbours give 0 and the image size is checked`() {
        assertEquals(0L, dHash(IntArray(72) { 128 }))
        assertFailsWith<IllegalArgumentException> { dHash(IntArray(64)) }
    }

    @Test
    fun `hamming distance counts differing bits`() {
        assertEquals(0, hammingDistance(0x1234L, 0x1234L))
        assertEquals(64, hammingDistance(0L, -1L))
        assertEquals(3, hammingDistance(0b1011L, 0b0000L))
    }

    @Test
    fun `hash hex round-trips including the sign bit`() {
        assertEquals("ffffffffffffffff", hashToHex(-1L))
        assertEquals("0000000000000abc", hashToHex(0xABCL))
        assertEquals(Long.MIN_VALUE, hashFromHex(hashToHex(Long.MIN_VALUE)))
        assertFailsWith<IllegalArgumentException> { hashFromHex("") }
    }

    @Test
    fun `matcher returns the closest code within distance 6`() {
        val turnRight = 0x0F0F_0F0F_0F0F_0F0FL
        val turnLeft = 0x7070_7070_7070_7070L
        val matcher = IconMatcher(mapOf(turnRight to 4, turnLeft to 1))
        assertEquals(IconMatch(4, turnRight, 0), matcher.match(turnRight))
        val sixOff = turnRight xor 0b111111L
        assertEquals(4, matcher.codeFor(sixOff))
        assertEquals(6, matcher.match(sixOff)?.distance)
        assertNull(matcher.codeFor(turnRight xor 0b1111111L)) // 7 bits off
        assertEquals(1, matcher.codeFor(turnLeft xor 1L))
    }

    @Test
    fun `ties go to the lower hash and the image overload hashes first`() {
        val matcher = IconMatcher(mapOf(0b10L to 8, 0b01L to 9))
        assertEquals(IconMatch(9, 0b01L, 1), matcher.match(0L))
        val images = IconMatcher(mapOf(dHash(falling) to 17))
        assertEquals(17, images.codeFor(falling))
        assertNull(images.codeFor(rising))
        assertNull(IconMatcher(emptyMap()).codeFor(0L))
    }
}
