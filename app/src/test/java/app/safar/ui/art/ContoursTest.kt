package app.safar.ui.art

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContoursTest {
    @Test
    fun `lehmer matches the prototype rng sequence`() {
        // art.js: rng(3 * 977 + 13) -> s = 2944 * 16807 % 2147483647 = 49479808
        val r = Contours.lehmer(3 * 977L + 13)
        assertEquals(49_479_808.0 / 2_147_483_647, r(), 1e-12)
        assertEquals((49_479_808L * 16807 % 2_147_483_647).toDouble() / 2_147_483_647, r(), 1e-12)
    }

    @Test
    fun `zero seed falls back to 7 like the prototype`() {
        assertEquals(7.0 * 16807 / 2_147_483_647, Contours.lehmer(0)(), 1e-12)
    }

    @Test
    fun `two summits with 15 and 11 rings, every fifth an index contour`() {
        val rings = Contours.rings()
        assertEquals(26, rings.size)
        assertEquals(5, rings.count { it.index })
        assertTrue(rings.all { it.points.size == 36 })
    }

    @Test
    fun `rings grow outwards from the summit`() {
        val rings = Contours.rings().take(15)
        val cx = 540f * 0.72f
        val reach = rings.map { ring -> ring.points.maxOf { kotlin.math.abs(it.x - cx) } }
        assertTrue(reach.zipWithNext().all { (a, b) -> b > a })
    }

    @Test
    fun `same seed gives the same drawing`() {
        assertEquals(Contours.rings(seed = 5), Contours.rings(seed = 5))
    }
}
