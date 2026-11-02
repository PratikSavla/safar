package app.safar.protocol

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalTime

/*
 * App -> bike turn-by-turn navigation frame (type '1'). Port of docs/helper/src/navigation.ts.
 */

const val NAVIGATION_TYPE = '1'

/** App v2.15 period. App v7.6 sends about every 220 ms; use [Timing.NAVIGATION_PERIOD_MS] for new code. */
const val NAVIGATION_PERIOD_MS = 500L

/** Byte 23. [code] is the ASCII digit sent to the cluster. */
enum class NavStatus(val code: Char) {
    /** Airplane mode, no SIM, or very low cellular signal. */
    NO_NETWORK_SIGNAL('0'),
    NORMAL('1'),
    /** Send for one tick when the route is recalculated. */
    REROUTING('2'),
    VIA_POINT_REACHED('3'),
    GPS_OFF('4'),
    DESTINATION_REACHED('5'),
    NO_INTERNET('6'),
    ;

    /** The original app shows the turn icon only for these; for all others it sends [STATUS_ICON_CODE]. */
    val showsIcon: Boolean get() = this == NORMAL || this == VIA_POINT_REACHED || this == DESTINATION_REACHED

    companion object {
        fun fromCode(code: Char): NavStatus? = entries.firstOrNull { it.code == code }
    }
}

enum class DistanceUnit(val code: Char) { METRES('M'), KILOMETRES('K') }

/** [text] is 4 ASCII characters, for example "0250", "01.2", "12.5", "0123". */
data class Distance(val text: String, val unit: DistanceUnit)

/** Format a distance in metres the same way as the original app. */
fun formatDistance(metres: Double): Distance {
    if (!metres.isFinite() || metres < 0) throw FrameException("distance must be >= 0 m, got $metres")
    val rounded = Math.round(metres / 10) * 10
    if (rounded < 1000) return Distance(rounded.toString().padStart(4, '0'), DistanceUnit.METRES)
    val km = rounded / 1000.0
    if (km < 100) {
        val oneDecimal = toFixed1(km)
        // The TypeScript helper returns "100.0" (5 chars) for 99 950..99 994 m and the frame build fails.
        // Fall through to the whole-km format instead, so those distances show "0100".
        if (oneDecimal.length <= 4) return Distance(oneDecimal.padStart(4, '0'), DistanceUnit.KILOMETRES)
    }
    return Distance(Math.round(km).toString().padStart(4, '0').takeLast(4), DistanceUnit.KILOMETRES)
}

fun formatDistance(metres: Int): Distance = formatDistance(metres.toDouble())

/** Same digits as JavaScript `Number.prototype.toFixed(1)`: rounds the exact binary value half up. */
private fun toFixed1(value: Double): String = BigDecimal(value).setScale(1, RoundingMode.HALF_UP).toPlainString()

/** Format an arrival time as "hhmmAM" / "hhmmPM" (12-hour clock, 6 characters). */
fun formatEta(hour: Int, minute: Int): String {
    if (hour !in 0..23 || minute !in 0..59) throw FrameException("ETA must be a time of day, got $hour:$minute")
    val h12 = if (hour % 12 == 0) 12 else hour % 12
    return h12.toString().padStart(2, '0') + minute.toString().padStart(2, '0') + if (hour < 12) "AM" else "PM"
}

fun formatEta(time: LocalTime): String = formatEta(time.hour, time.minute)

data class NavigationData(
    /** Cluster icon code 1..45. Use codeFromInstruction() or an icon match. */
    val iconCode: Int,
    /** Distance to the next maneuver, metres. */
    val distanceToTurnM: Int,
    /** Arrival time at the destination (local clock). */
    val eta: LocalTime,
    /** Remaining distance to the destination, metres. */
    val remainingM: Int,
    val status: NavStatus,
    /** false sends the "navigation ended" flag. Send it once when the rider stops navigation. */
    val active: Boolean,
)

fun encodeNavigation(data: NavigationData, mode: ChecksumMode): ByteArray {
    if (data.iconCode !in MIN_ICON_CODE..MAX_ICON_CODE) {
        throw FrameException("iconCode must be 1..45, got ${data.iconCode}")
    }
    val icon = if (data.status.showsIcon) data.iconCode else STATUS_ICON_CODE
    val turn = formatDistance(data.distanceToTurnM)
    val left = formatDistance(data.remainingM)
    val body = NAVIGATION_TYPE +
        "\u0000" + // byte 2: icon, set below
        "\u0000" + // byte 3: filler
        turn.text + turn.unit.code +
        formatEta(data.eta) +
        "\u0000\u0000\u0000" + // bytes 15..17: filler
        left.text + left.unit.code +
        data.status.code +
        (if (data.active) '1' else '0') +
        "\u0000\u0000\u0000" // bytes 25..27: filler
    val filler = listOf(3, 15, 16, 17, 25, 26, 27).associateWith { FILLER_BYTE }
    return buildFrame(body, mode, filler + (2 to icon))
}
