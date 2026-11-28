package app.safar.protocol

/*
 * English instruction text -> cluster icon code (part of docs/helper/src/google-maps.ts).
 * Fallback only: the Google Maps turn icon decides first (MapsManeuvers.kt). The order of the rules is
 * important: the most specific phrase comes first. Phrasings below were seen in Google Maps 26.44 notifications.
 */

private fun rule(pattern: String, code: Int): Pair<Regex, Int> = Regex(pattern, RegexOption.IGNORE_CASE) to code

private val INSTRUCTION_RULES: List<Pair<Regex, Int>> = listOf(
    rule("destination|arrive", 9),
    rule("ferry", 43),
    rule("u-?turn", 7),
    // "At Rider Junction, take the 3rd exit": Maps names the roundabout instead of saying "roundabout".
    rule("(1st|first) exit", 21),
    rule("(2nd|second) exit", 23),
    rule("(3rd|third) exit", 25),
    rule("[0-9]+th exit|roundabout|rotary", 45),
    rule("sharp(ly)? left", 2),
    rule("sharp(ly)? right", 5),
    rule("slight(ly)? left", 3),
    rule("slight(ly)? right", 6),
    rule("fork.*left|left.*fork", 31),
    rule("fork.*right|right.*fork", 32),
    rule("exit.*left|ramp.*left", 27),
    rule("exit.*right|ramp.*right", 28),
    rule("keep left", 38),
    rule("keep right", 10),
    rule("turn left|left onto|left at", 1),
    rule("turn right|right onto|right at", 4),
    // "Head southwest on Main Rd": Maps shows a straight arrow, so never the compass start codes (15-19, 40-42).
    rule("continue|straight|head", 8),
)

/** Cluster icon code for an English instruction, or null when no rule matches. */
fun codeFromInstruction(text: String): Int? = INSTRUCTION_RULES.firstOrNull { it.first.containsMatchIn(text) }?.second
