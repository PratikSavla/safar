package app.safar.protocol

/*
 * Turn icon codes for the navigation frame (byte 2). Port of docs/helper/src/maneuvers.ts.
 *
 * The original app gets a maneuver ID from the Mappls navigation SDK and converts it to a cluster
 * icon code (1..45). Names come from the Mappls icon drawables. For another map source, convert its
 * maneuver type to a cluster code with this table.
 */

data class Maneuver(
    /** Cluster icon code that goes into byte 2 of the navigation frame. */
    val code: Int,
    /** Mappls maneuver ID that the original app converts from. */
    val mapplsId: Int,
    val name: String,
)

val MANEUVERS: List<Maneuver> = listOf(
    Maneuver(1, 0, "Turn left"),
    Maneuver(2, 1, "Sharp left"),
    Maneuver(3, 2, "Slight left"),
    Maneuver(4, 3, "Turn right"),
    Maneuver(5, 4, "Sharp right"),
    Maneuver(6, 5, "Slight right"),
    Maneuver(7, 6, "U-turn (left side)"),
    Maneuver(8, 7, "Go straight"),
    Maneuver(9, 8, "Arrive at destination"),
    Maneuver(10, 75, "Keep right (lane)"),
    Maneuver(11, 11, "Take side road left"),
    Maneuver(12, 12, "Take side road right"),
    Maneuver(13, 13, "End of road: turn left"),
    Maneuver(14, 14, "End of road: turn right"),
    Maneuver(15, 53, "Head south-east (start)"),
    Maneuver(16, 54, "Head south (start)"),
    Maneuver(17, 55, "Head south-west (start)"),
    Maneuver(18, 56, "Head west (start)"),
    Maneuver(19, 57, "Head north-west (start)"),
    Maneuver(20, 65, "Roundabout: exit sharp left"),
    Maneuver(21, 66, "Roundabout: exit left"),
    Maneuver(22, 67, "Roundabout: exit slight left"),
    Maneuver(23, 68, "Roundabout: go straight"),
    Maneuver(24, 69, "Roundabout: exit slight right"),
    Maneuver(25, 70, "Roundabout: exit right"),
    Maneuver(26, 71, "Roundabout: exit sharp right"),
    Maneuver(27, 19, "Take slip road left"),
    Maneuver(28, 20, "Take slip road right"),
    Maneuver(29, 17, "Ramp: turn back left"),
    Maneuver(30, 18, "Ramp: turn back right"),
    Maneuver(31, 15, "Fork: keep left"),
    Maneuver(32, 16, "Fork: keep right"),
    Maneuver(33, 21, "Cross the intersection"),
    Maneuver(34, 22, "Bend: sharp left"),
    Maneuver(35, 23, "Bend: slight left"),
    Maneuver(36, 24, "Bend: sharp right"),
    Maneuver(37, 25, "Bend: slight right"),
    Maneuver(38, 73, "Keep left (lane)"),
    Maneuver(39, 41, "U-turn (right side)"),
    Maneuver(40, 50, "Head north (start)"),
    Maneuver(41, 51, "Head north-east (start)"),
    Maneuver(42, 52, "Head east (start)"),
    Maneuver(43, 36, "Take ferry"),
    Maneuver(44, 74, "Keep left (lane, variant)"),
    Maneuver(45, 72, "Roundabout (generic)"),
)

/** Lowest and highest cluster turn icon codes. */
const val MIN_ICON_CODE = 1
const val MAX_ICON_CODE = 45

/**
 * Code 46 (0x2E) is not a maneuver. The original app sends it instead of the turn icon when the
 * navigation status is not normal (for example GPS off). See docs/04-navigation.md.
 */
const val STATUS_ICON_CODE = 46

private val byMappls: Map<Int, Maneuver> = MANEUVERS.associateBy { it.mapplsId }
private val byCode: Map<Int, Maneuver> = MANEUVERS.associateBy { it.code }

/** Returns null for Mappls IDs the app does not map. The original app then keeps the previous icon. */
fun codeFromMapplsId(mapplsId: Int): Int? = byMappls[mapplsId]?.code

fun maneuverByCode(code: Int): Maneuver? = byCode[code]
