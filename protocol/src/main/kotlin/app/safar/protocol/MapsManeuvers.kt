package app.safar.protocol

/*
 * Google Maps turn icon -> cluster icon code (docs 11.4). Deterministic: no hashing, no text.
 *
 * Google Maps draws the navigation notification's large icon (a black arrow on a transparent background)
 * from its own `maneuver_*` drawables. The phone app renders those drawables at the live icon's size and
 * [bestManeuver] picks the reference whose alpha mask is closest to the live bitmap. The drawable name gives
 * the cluster code through [MANEUVER_CODES]. Measured on Maps 26.44 and 26.47: a live icon is within 0.29
 * (mean |alpha diff|, 0..255) of its drawable; drawables that map to different codes are at least 12.7 apart.
 * A 64-bit dHash was tried first and dropped: mirrored left/right arrows differ in only a few bits.
 */

/** Prefix of the Google Maps maneuver drawables. */
const val MANEUVER_DRAWABLE_PREFIX = "maneuver_"

/** A drawable that every Maps version with this icon set has; used to find the drawable resource type. */
const val MANEUVER_ANCHOR_DRAWABLE = "maneuver_straight"

/** Accept a reference whose mean |alpha diff| (0..255) is at most this. */
const val MANEUVER_MATCH_MAX_DIFF = 1.5

/**
 * Maps drawable name -> cluster icon code (docs 4.4). Pixel-identical twins (for example `keep_left`,
 * `fork_left`, `on_ramp_keep_left`) share a code. cw = clockwise roundabout (left-hand traffic, India).
 */
val MANEUVER_CODES: Map<String, Int> = buildMap {
    put("maneuver_depart", 8) // Maps shows a straight arrow; the compass "head" codes would disagree
    put("maneuver_straight", 8)
    put("maneuver_name_change", 8)
    for (m in listOf("maneuver_merge", "maneuver_merge_left", "maneuver_merge_right")) put(m, 8)
    for (m in listOf("", "_left", "_right", "_straight")) put("maneuver_destination$m", 9)
    for (side in listOf("left", "right")) {
        val left = side == "left"
        put("maneuver_turn_normal_$side", if (left) 1 else 4)
        put("maneuver_turn_sharp_$side", if (left) 2 else 5)
        put("maneuver_turn_slight_$side", if (left) 3 else 6)
        put("maneuver_u_turn_$side", if (left) 7 else 39)
        put("maneuver_fork_$side", if (left) 31 else 32)
        put("maneuver_keep_$side", if (left) 31 else 32)
        for (ramp in listOf("on_ramp", "off_ramp")) {
            put("maneuver_${ramp}_keep_$side", if (left) 31 else 32)
            put("maneuver_${ramp}_sharp_$side", if (left) 2 else 5)
            put("maneuver_${ramp}_slight_$side", if (left) 3 else 6)
            put("maneuver_${ramp}_u_turn_$side", if (left) 7 else 39)
        }
        put("maneuver_on_ramp_normal_$side", if (left) 1 else 4)
        put("maneuver_off_ramp_normal_$side", if (left) 27 else 28) // slip road; its own picture
    }
    val exits = mapOf(
        "sharp_left" to 20, "normal_left" to 21, "slight_left" to 22, "straight" to 23,
        "slight_right" to 24, "normal_right" to 25, "sharp_right" to 26, "u_turn" to 45,
    )
    for (dir in listOf("cw", "ccw")) {
        for ((exit, code) in exits) put("maneuver_roundabout_enter_and_exit_${dir}_$exit", code)
        put("maneuver_roundabout_enter_and_exit_$dir", 45)
        put("maneuver_roundabout_enter_$dir", 45)
        put("maneuver_roundabout_exit_$dir", 45)
    }
}

/** One Maps drawable rendered at the live icon's size: its name and alpha channel, row-major. */
class ManeuverRef(val name: String, val alpha: IntArray)

/** Closest reference and its mean |alpha diff|. [code] is null for a drawable not in [MANEUVER_CODES]. */
data class ManeuverMatch(val name: String, val diff: Double) {
    val code: Int? get() = MANEUVER_CODES[name]
    val accepted: Boolean get() = diff <= MANEUVER_MATCH_MAX_DIFF && code != null
}

/** Alpha channel (0..255) of ARGB pixels. */
fun alphaOf(argb: IntArray): IntArray = IntArray(argb.size) { argb[it] ushr 24 and 0xFF }

/** Mean |a - b| over two equally sized alpha arrays. */
fun alphaDistance(a: IntArray, b: IntArray): Double {
    require(a.size == b.size && a.isNotEmpty()) { "alpha sizes differ: ${a.size} vs ${b.size}" }
    var sum = 0L
    for (i in a.indices) sum += kotlin.math.abs(a[i] - b[i])
    return sum.toDouble() / a.size
}

/** Reference closest to [liveAlpha], or null when there are no references of the same size. */
fun bestManeuver(liveAlpha: IntArray, refs: List<ManeuverRef>): ManeuverMatch? = refs
    .filter { it.alpha.size == liveAlpha.size }
    .map { ManeuverMatch(it.name, alphaDistance(liveAlpha, it.alpha)) }
    .minWithOrNull(compareBy<ManeuverMatch> { it.diff }.thenBy { it.name })
