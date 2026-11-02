package app.safar.protocol

import java.time.LocalDateTime

/*
 * Text parsers for the Google Maps navigation notification (part of docs/helper/src/google-maps.ts).
 * The notification layout is not a public Google API. Keep all parsing in this package.
 */

/** Title split into the distance prefix (if any) and the instruction. */
data class TitleParts(val distance: String?, val instruction: String)

/** Remaining distance and arrival time found in the sub-text. Either can be missing. */
data class SubTextParts(val remainingM: Int?, val eta: LocalDateTime?)

private val TITLE = Regex(
    "^$WS*([0-9.,]+$WS*(?:km|mi|ft|m))$WS*[·•]$WS*(.*)$",
    RegexOption.IGNORE_CASE,
)
private val DISTANCE = Regex("($DIGIT+(?:[.,]$DIGIT+)?)$WS*(km|mi|ft|m)$WORD_END", RegexOption.IGNORE_CASE)
private val CLOCK = Regex("($DIGIT{1,2}):($DIGIT{2})$WS*([AaPp][Mm])?")
private val HAS_CLOCK = Regex("$DIGIT:$DIGIT{2}")
private val SUB_TEXT_SEPARATOR = Regex("[·•|]")
private const val ETA_GRACE_SECONDS = 60L

private val UNIT_TO_METRES: Map<String, Double> = mapOf("m" to 1.0, "km" to 1000.0, "ft" to 0.3048, "mi" to 1609.344)

/** Split the title "200 m · Turn right toward NH 48" into distance text and instruction. */
fun splitTitle(title: String): TitleParts {
    val match = TITLE.find(title) ?: return TitleParts(null, jsTrim(title))
    return TitleParts(match.groupValues[1], jsTrim(match.groupValues[2]))
}

/** Parse "300 m", "1.2 km", "1,2 km", "500 ft", "0.3 mi" to whole metres. Null if no distance is found. */
fun parseDistance(text: String): Int? {
    val match = DISTANCE.find(text) ?: return null
    val value = match.groupValues[1].replace(',', '.').toDouble()
    val factor = UNIT_TO_METRES.getValue(match.groupValues[2].lowercase())
    return Math.round(value * factor).toInt()
}

/**
 * Parse a clock time such as "11:42 AM", "11:42 pm" or "23:42" to the next such time after [now].
 * A time more than 60 s before [now] is taken as tomorrow (ETA after midnight).
 */
fun parseClock(text: String, now: LocalDateTime): LocalDateTime? {
    val match = CLOCK.find(text) ?: return null
    var hours = match.groupValues[1].toInt()
    val minutes = match.groupValues[2].toInt()
    val meridiem = match.groupValues[3].uppercase()
    if (meridiem == "PM" && hours < 12) hours += 12
    if (meridiem == "AM" && hours == 12) hours = 0
    // Same overflow rules as JavaScript Date.setHours(hours, minutes, 0, 0).
    val eta = now.toLocalDate().atStartOfDay().plusHours(hours.toLong()).plusMinutes(minutes.toLong())
    return if (eta.isBefore(now.minusSeconds(ETA_GRACE_SECONDS))) eta.plusDays(1) else eta
}

/** Parse the sub-text "12 min · 4.3 km · 11:42 AM". The parts can come in a different order. */
fun parseSubText(subText: String, now: LocalDateTime): SubTextParts {
    var remainingM: Int? = null
    var eta: LocalDateTime? = null
    for (part in subText.split(SUB_TEXT_SEPARATOR).map(::jsTrim)) {
        if (eta == null && HAS_CLOCK.containsMatchIn(part)) {
            eta = parseClock(part, now)
        } else if (remainingM == null) {
            remainingM = parseDistance(part)
        }
    }
    return SubTextParts(remainingM, eta)
}
