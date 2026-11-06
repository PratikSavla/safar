package app.safar.ui.art

import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VectorBuildTest {
    private fun paths(node: VectorNode): List<VectorPath> = when (node) {
        is VectorPath -> listOf(node)
        is VectorGroup -> node.flatMap { paths(it) }
    }

    private fun pathCount(node: VectorNode): Int = paths(node).size

    @Test
    fun `every turn code builds a 64 grid vector with drawn paths`() {
        (TurnCodes.MIN..TurnCodes.MAX).forEach { code ->
            val v = TurnArrows.vector(code)
            assertEquals(64f, v.viewportWidth)
            assertTrue(pathCount(v.root) > 0, "code $code has no paths")
            assertTrue(paths(v.root).all { it.pathData.isNotEmpty() }, "code $code has an empty path")
        }
    }

    @Test
    fun `right turns are mirrored groups of the left shapes`() {
        val right = TurnArrows.vector(4).root.single() as VectorGroup
        assertEquals(-1f, right.scaleX)
        val left = TurnArrows.vector(1).root.single() as VectorGroup
        assertEquals(1f, left.scaleX)
    }

    @Test
    fun `all prototype icons build with the same names`() {
        val names = AtlasIcons.all.keys
        assertEquals(53, names.size)
        assertTrue(listOf("today", "route", "journal", "alerts", "settings", "wa", "bt", "export", "battery", "coolant", "spark", "light").all { it in names })
        AtlasIcons.all.forEach { (name, v) -> assertTrue(pathCount(v.root) > 0, name) }
        assertEquals(AtlasIcons.info, AtlasIcons.byName("no-such-icon"))
    }

    @Test
    fun `silhouettes use the 124 by 66 art box`() {
        assertEquals(124f, BikeSilhouettes.scooter.viewportWidth)
        assertEquals(66f, BikeSilhouettes.motorcycle.viewportHeight)
    }
}
