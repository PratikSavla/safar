package app.safar.ui.art

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TurnCodesTest {
    @Test
    fun `every cluster code 1 to 46 has a name and a drawing family`() {
        val all = TurnCodes.all
        assertEquals(46, all.size)
        assertEquals((1..46).toList(), all.map { it.code })
        assertTrue(all.none { it.family == TurnFamily.UNKNOWN })
        assertEquals(46, all.map { it.name }.toSet().size, "names are unique")
    }

    @Test
    fun `table rows match docs 4_4`() {
        assertEquals(TurnSpec(1, "Turn left", TurnFamily.TURN, TurnSide.LEFT), TurnCodes.spec(1))
        assertEquals(TurnSpec(4, "Turn right", TurnFamily.TURN, TurnSide.RIGHT), TurnCodes.spec(4))
        assertEquals("Arrive at destination", TurnCodes.spec(9).name)
        assertEquals("Fork: keep right", TurnCodes.spec(32).name)
        assertEquals("Take ferry", TurnCodes.spec(43).name)
        assertEquals("Status", TurnCodes.spec(46).name)
    }

    @Test
    fun `start headings run clockwise from north`() {
        val headings = mapOf(40 to 0f, 41 to 45f, 42 to 90f, 15 to 135f, 16 to 180f, 17 to 225f, 18 to 270f, 19 to 315f)
        headings.forEach { (code, angle) ->
            assertEquals(TurnFamily.START, TurnCodes.spec(code).family)
            assertEquals(angle, TurnCodes.spec(code).angle, "code $code")
        }
        assertEquals("Start: head SW", TurnCodes.spec(17).name)
    }

    @Test
    fun `roundabout exits go from sharp left to sharp right`() {
        val exits = (20..26).map { TurnCodes.spec(it) }
        assertEquals(listOf(-135f, -90f, -45f, 0f, 45f, 90f, 135f), exits.map { it.angle })
        assertEquals("Roundabout exit: straight", exits[3].name)
        assertEquals(TurnSide.LEFT, exits[0].side)
        assertEquals(TurnSide.RIGHT, exits[6].side)
    }

    @Test
    fun `left and right pairs share a family and mirror each other`() {
        listOf(1 to 4, 2 to 5, 3 to 6, 7 to 39, 11 to 12, 13 to 14, 27 to 28, 29 to 30, 31 to 32, 34 to 36, 35 to 37, 38 to 10)
            .forEach { (l, r) ->
                assertEquals(TurnCodes.spec(l).family, TurnCodes.spec(r).family, "$l/$r")
                assertEquals(TurnSide.LEFT, TurnCodes.spec(l).side)
                assertEquals(TurnSide.RIGHT, TurnCodes.spec(r).side)
            }
    }

    @Test
    fun `unknown codes fall back without crashing`() {
        assertEquals(TurnFamily.UNKNOWN, TurnCodes.spec(0).family)
        assertEquals(TurnFamily.UNKNOWN, TurnCodes.spec(47).family)
        assertNotEquals(TurnCodes.spec(0), TurnCodes.spec(47))
    }

    @Test
    fun `arrowhead arms sit 12 units behind the tip`() {
        assertEquals("M18.49 13.51L10.00 22.00L18.49 30.49", TurnArrows.head(10f, 22f, -90f))
    }
}
