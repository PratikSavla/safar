package app.safar.ui.art

/** Which way a manoeuvre goes; RIGHT shapes are mirrored LEFT shapes. */
enum class TurnSide { LEFT, RIGHT, NONE }

/** Drawing families for cluster turn icons (docs 4.4). */
enum class TurnFamily {
    TURN, SHARP, SLIGHT, UTURN, STRAIGHT, CROSS, ARRIVE, KEEP_LANE, KEEP_LANE_ALT, SIDE_ROAD, END_OF_ROAD,
    SLIP_ROAD, RAMP_BACK, FORK, BEND_SHARP, BEND_SLIGHT, ROUNDABOUT_EXIT, ROUNDABOUT, START, FERRY, STATUS, UNKNOWN,
}

/**
 * One cluster icon code. [angle] is degrees clockwise from straight ahead (north for START):
 * the exit direction of a roundabout or the heading of a start icon.
 */
data class TurnSpec(val code: Int, val name: String, val family: TurnFamily, val side: TurnSide = TurnSide.NONE, val angle: Float = 0f)

/** Cluster turn icon codes 1..46 with their names and drawing family (docs/04-navigation.md 4.4). */
object TurnCodes {
    const val MIN = 1
    const val MAX = 46

    private val L = TurnSide.LEFT
    private val R = TurnSide.RIGHT

    private val specs: Map<Int, TurnSpec> = buildList {
        add(TurnSpec(1, "Turn left", TurnFamily.TURN, L))
        add(TurnSpec(2, "Sharp left", TurnFamily.SHARP, L))
        add(TurnSpec(3, "Slight left", TurnFamily.SLIGHT, L))
        add(TurnSpec(4, "Turn right", TurnFamily.TURN, R))
        add(TurnSpec(5, "Sharp right", TurnFamily.SHARP, R))
        add(TurnSpec(6, "Slight right", TurnFamily.SLIGHT, R))
        add(TurnSpec(7, "U-turn (left side)", TurnFamily.UTURN, L))
        add(TurnSpec(8, "Go straight", TurnFamily.STRAIGHT))
        add(TurnSpec(9, "Arrive at destination", TurnFamily.ARRIVE))
        add(TurnSpec(10, "Keep right (lane)", TurnFamily.KEEP_LANE, R))
        add(TurnSpec(11, "Take side road left", TurnFamily.SIDE_ROAD, L))
        add(TurnSpec(12, "Take side road right", TurnFamily.SIDE_ROAD, R))
        add(TurnSpec(13, "End of road: turn left", TurnFamily.END_OF_ROAD, L))
        add(TurnSpec(14, "End of road: turn right", TurnFamily.END_OF_ROAD, R))
        listOf("SE" to 135f, "S" to 180f, "SW" to 225f, "W" to 270f, "NW" to 315f).forEachIndexed { i, (d, a) ->
            add(TurnSpec(15 + i, "Start: head $d", TurnFamily.START, angle = a))
        }
        roundaboutExits().forEachIndexed { i, (n, a) ->
            val side = if (a < 0) L else if (a > 0) R else TurnSide.NONE
            add(TurnSpec(20 + i, "Roundabout exit: $n", TurnFamily.ROUNDABOUT_EXIT, side, a))
        }
        add(TurnSpec(27, "Take slip road left", TurnFamily.SLIP_ROAD, L))
        add(TurnSpec(28, "Take slip road right", TurnFamily.SLIP_ROAD, R))
        add(TurnSpec(29, "Ramp: turn back left", TurnFamily.RAMP_BACK, L))
        add(TurnSpec(30, "Ramp: turn back right", TurnFamily.RAMP_BACK, R))
        add(TurnSpec(31, "Fork: keep left", TurnFamily.FORK, L))
        add(TurnSpec(32, "Fork: keep right", TurnFamily.FORK, R))
        add(TurnSpec(33, "Cross the intersection", TurnFamily.CROSS))
        add(TurnSpec(34, "Bend: sharp left", TurnFamily.BEND_SHARP, L))
        add(TurnSpec(35, "Bend: slight left", TurnFamily.BEND_SLIGHT, L))
        add(TurnSpec(36, "Bend: sharp right", TurnFamily.BEND_SHARP, R))
        add(TurnSpec(37, "Bend: slight right", TurnFamily.BEND_SLIGHT, R))
        add(TurnSpec(38, "Keep left (lane)", TurnFamily.KEEP_LANE, L))
        add(TurnSpec(39, "U-turn (right side)", TurnFamily.UTURN, R))
        listOf("N" to 0f, "NE" to 45f, "E" to 90f).forEachIndexed { i, (d, a) ->
            add(TurnSpec(40 + i, "Start: head $d", TurnFamily.START, angle = a))
        }
        add(TurnSpec(43, "Take ferry", TurnFamily.FERRY))
        add(TurnSpec(44, "Keep left (lane, variant)", TurnFamily.KEEP_LANE_ALT, L))
        add(TurnSpec(45, "Roundabout", TurnFamily.ROUNDABOUT))
        add(TurnSpec(46, "Status", TurnFamily.STATUS))
    }.associateBy { it.code }

    private fun roundaboutExits() = listOf(
        "sharp left" to -135f, "left" to -90f, "slight left" to -45f, "straight" to 0f,
        "slight right" to 45f, "right" to 90f, "sharp right" to 135f,
    )

    /** Spec for [code]; codes outside 1..46 are UNKNOWN (drawn as straight ahead, faint). */
    fun spec(code: Int): TurnSpec = specs[code] ?: TurnSpec(code, "Unknown icon", TurnFamily.UNKNOWN)

    val all: List<TurnSpec> get() = (MIN..MAX).map(::spec)
}
