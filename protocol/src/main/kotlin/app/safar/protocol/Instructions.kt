package app.safar.protocol

/*
 * English instruction text -> cluster icon code (part of docs/helper/src/google-maps.ts).
 * Use it only when the icon bitmap has no match. The order of the rules is important: the most
 * specific phrase comes first.
 */

private fun rule(pattern: String, code: Int): Pair<Regex, Int> = Regex(pattern, RegexOption.IGNORE_CASE) to code

private val INSTRUCTION_RULES: List<Pair<Regex, Int>> = listOf(
    rule("destination|arrive", 9),
    rule("ferry", 43),
    rule("u-?turn", 7),
    rule("roundabout.*(1st|first) exit", 21),
    rule("roundabout.*(2nd|second) exit", 23),
    rule("roundabout.*(3rd|third) exit", 25),
    rule("roundabout|rotary", 45),
    rule("sharp left", 2),
    rule("sharp right", 5),
    rule("slight left", 3),
    rule("slight right", 6),
    rule("fork.*left|left.*fork", 31),
    rule("fork.*right|right.*fork", 32),
    rule("exit.*left|ramp.*left", 27),
    rule("exit.*right|ramp.*right", 28),
    rule("keep left", 38),
    rule("keep right", 10),
    rule("turn left|left onto|left at", 1),
    rule("turn right|right onto|right at", 4),
    rule("head north-?east", 41),
    rule("head north-?west", 19),
    rule("head south-?east", 15),
    rule("head south-?west", 17),
    rule("head north", 40),
    rule("head south", 16),
    rule("head east", 42),
    rule("head west", 18),
    rule("continue|straight|head", 8),
)

/** Cluster icon code for an English instruction, or null when no rule matches. */
fun codeFromInstruction(text: String): Int? = INSTRUCTION_RULES.firstOrNull { it.first.containsMatchIn(text) }?.second
