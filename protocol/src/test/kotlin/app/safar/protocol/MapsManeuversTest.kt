package app.safar.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SIDE = 32

/** The 64 maneuver_* drawables in Google Maps 26.44.05 and 26.47.00 (identical sets). */
private val MAPS_DRAWABLES = """
    depart destination destination_left destination_right destination_straight fork_left fork_right keep_left
    keep_right merge merge_left merge_right name_change off_ramp_keep_left off_ramp_keep_right off_ramp_normal_left
    off_ramp_normal_right off_ramp_sharp_left off_ramp_sharp_right off_ramp_slight_left off_ramp_slight_right
    off_ramp_u_turn_left off_ramp_u_turn_right on_ramp_keep_left on_ramp_keep_right on_ramp_normal_left
    on_ramp_normal_right on_ramp_sharp_left on_ramp_sharp_right on_ramp_slight_left on_ramp_slight_right
    on_ramp_u_turn_left on_ramp_u_turn_right roundabout_enter_and_exit_ccw roundabout_enter_and_exit_ccw_normal_left
    roundabout_enter_and_exit_ccw_normal_right roundabout_enter_and_exit_ccw_sharp_left
    roundabout_enter_and_exit_ccw_sharp_right roundabout_enter_and_exit_ccw_slight_left
    roundabout_enter_and_exit_ccw_slight_right roundabout_enter_and_exit_ccw_straight
    roundabout_enter_and_exit_ccw_u_turn roundabout_enter_and_exit_cw roundabout_enter_and_exit_cw_normal_left
    roundabout_enter_and_exit_cw_normal_right roundabout_enter_and_exit_cw_sharp_left
    roundabout_enter_and_exit_cw_sharp_right roundabout_enter_and_exit_cw_slight_left
    roundabout_enter_and_exit_cw_slight_right roundabout_enter_and_exit_cw_straight roundabout_enter_and_exit_cw_u_turn
    roundabout_enter_ccw roundabout_enter_cw roundabout_exit_ccw roundabout_exit_cw straight turn_normal_left
    turn_normal_right turn_sharp_left turn_sharp_right turn_slight_left turn_slight_right u_turn_left u_turn_right
""".trim().split(Regex("\\s+")).map { MANEUVER_DRAWABLE_PREFIX + it }

/** Pixel-identical drawable groups measured on Maps 26.44.05; each group must send one cluster code. */
private val TWINS = listOf(
    listOf("destination", "destination_straight"),
    listOf("fork_left", "keep_left", "off_ramp_keep_left", "on_ramp_keep_left"),
    listOf("fork_right", "keep_right", "off_ramp_keep_right", "on_ramp_keep_right"),
    listOf("merge", "merge_left", "merge_right"),
    listOf("name_change", "straight"),
    listOf("turn_sharp_left", "off_ramp_sharp_left", "on_ramp_sharp_left"),
    listOf("turn_sharp_right", "off_ramp_sharp_right", "on_ramp_sharp_right"),
    listOf("turn_slight_left", "off_ramp_slight_left", "on_ramp_slight_left"),
    listOf("turn_slight_right", "off_ramp_slight_right", "on_ramp_slight_right"),
    listOf("u_turn_left", "off_ramp_u_turn_left", "on_ramp_u_turn_left"),
    listOf("u_turn_right", "off_ramp_u_turn_right", "on_ramp_u_turn_right"),
    listOf("turn_normal_left", "on_ramp_normal_left"),
    listOf("turn_normal_right", "on_ramp_normal_right"),
    listOf("roundabout_enter_and_exit_ccw", "roundabout_enter_ccw"),
    listOf("roundabout_enter_and_exit_cw", "roundabout_enter_cw"),
).map { group -> group.map { MANEUVER_DRAWABLE_PREFIX + it } }

class MapsManeuversTest {
    /** Alpha mask: 255 where [lit], 0 elsewhere. */
    private fun mask(lit: (x: Int, y: Int) -> Boolean) = IntArray(SIDE * SIDE) { i -> if (lit(i % SIDE, i / SIDE)) 255 else 0 }

    private val leftTurn = mask { x, y -> (x in 14..17 && y >= 10) || (y in 8..11 && x <= 17) }
    private val rightTurn = mask { x, y -> (x in 14..17 && y >= 10) || (y in 8..11 && x >= 14) }
    private val refs = listOf(ManeuverRef("maneuver_turn_normal_left", leftTurn), ManeuverRef("maneuver_turn_normal_right", rightTurn))

    @Test
    fun `every Maps drawable has a cluster code`() {
        assertEquals(64, MAPS_DRAWABLES.size)
        for (name in MAPS_DRAWABLES) {
            val code = MANEUVER_CODES[name]
            assertTrue(code != null && code in MIN_ICON_CODE..MAX_ICON_CODE, "$name -> $code")
        }
        assertEquals(MAPS_DRAWABLES.toSet(), MANEUVER_CODES.keys)
    }

    @Test
    fun `pixel-identical drawables share one code`() {
        for (group in TWINS) assertEquals(1, group.map { MANEUVER_CODES[it] }.toSet().size, "group $group")
    }

    @Test
    fun `drawables map to the cluster icon that looks the same`() {
        assertEquals(1, MANEUVER_CODES["maneuver_turn_normal_left"])
        assertEquals(6, MANEUVER_CODES["maneuver_turn_slight_right"])
        assertEquals(8, MANEUVER_CODES["maneuver_depart"])
        assertEquals(32, MANEUVER_CODES["maneuver_keep_right"])
        assertEquals(25, MANEUVER_CODES["maneuver_roundabout_enter_and_exit_cw_normal_right"])
        assertEquals(28, MANEUVER_CODES["maneuver_off_ramp_normal_right"])
        assertEquals(39, MANEUVER_CODES["maneuver_u_turn_right"])
    }

    @Test
    fun `matcher tells mirrored turns apart`() {
        val left = assertNotNull(bestManeuver(leftTurn, refs))
        val right = assertNotNull(bestManeuver(rightTurn, refs))

        assertEquals(1, left.code)
        assertEquals(4, right.code)
        assertTrue(left.accepted && right.accepted)
        assertEquals(0.0, left.diff)
        assertTrue(alphaDistance(leftTurn, rightTurn) > 10 * MANEUVER_MATCH_MAX_DIFF)
    }

    @Test
    fun `a shape far from every reference is not accepted`() {
        val square = mask { x, y -> x in 4..27 && y in 4..27 }

        val match = assertNotNull(bestManeuver(square, refs))

        assertFalse(match.accepted)
    }

    @Test
    fun `unknown drawable names are never accepted`() {
        val match = assertNotNull(bestManeuver(leftTurn, listOf(ManeuverRef("maneuver_new_thing", leftTurn))))

        assertNull(match.code)
        assertFalse(match.accepted)
    }

    @Test
    fun `references of another size are ignored`() {
        assertNull(bestManeuver(IntArray(4), refs))
    }

    @Test
    fun `alpha helpers`() {
        assertEquals(listOf(0xFF, 0x80, 0), alphaOf(intArrayOf(0xFF000000.toInt(), 0x80FFFFFF.toInt(), 0x00FFFFFF)).toList())
        assertEquals(2.0, alphaDistance(intArrayOf(0, 4), intArrayOf(0, 0)))
        assertFailsWith<IllegalArgumentException> { alphaDistance(intArrayOf(1), intArrayOf(1, 2)) }
    }
}
