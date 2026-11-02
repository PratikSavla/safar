package app.safar.bikesim.sim

import app.safar.protocol.CallStatus
import app.safar.protocol.FILLER_BYTE
import app.safar.protocol.NavStatus
import app.safar.protocol.STATUS_ICON_CODE
import app.safar.protocol.maneuverByCode

/** Field text of the app -> bike frames, by the byte layout in docs 4-6 and the :protocol encoders. */
internal object FrameFields {
    /** '6': name in bytes 2..21, 'F' or 'R' in byte 27. */
    fun rider(f: ByteArray): String {
        val connection = when (f.c(27)) {
            'F' -> "F first connection"
            'R' -> "R reconnect"
            else -> "'${f.c(27)}' unknown"
        }
        return "name \"${f.text(2, 22)}\" · $connection"
    }

    /** '3': battery, charging, speed alert, signal, 12-hour clock, cleared flags. */
    fun status(f: ByteArray): String {
        val battery = when (f.c(2)) {
            '0' -> "under 25 %"
            '1' -> "25-49 %"
            '2' -> "50-74 %"
            '3' -> "75 % or more"
            else -> "'${f.c(2)}' unknown"
        }
        val alert = if (f.u(4) == FILLER_BYTE) "off" else "${f.text(4, 7).trimStart('0').ifEmpty { "0" }} km/h"
        val clock = f.text(8, 14)
        val clockText = if (clock.length == 6) "${clock.take(2)}:${clock.substring(2, 4)}:${clock.substring(4)}" else clock
        return "battery $battery · charging ${yesNo(f.c(3))} · alert $alert · signal ${f.c(7)}/3 · " +
            "clock $clockText · messages cleared ${yesNo(f.c(14))} · missed calls cleared ${yesNo(f.c(15))}"
    }

    /** '1': icon, distance to the turn, ETA, remaining distance, status, active flag. */
    fun navigation(f: ByteArray): String {
        val icon = f.u(2)
        val maneuver = maneuverByCode(icon)?.name ?: if (icon == STATUS_ICON_CODE) "status icon" else "unknown"
        val status = f.c(23)
        val statusName = NavStatus.fromCode(status)?.name?.lowercase()?.replace('_', ' ') ?: "unknown"
        val active = when (f.c(24)) {
            '1' -> "active"
            '0' -> "ended"
            else -> "'${f.c(24)}' unknown"
        }
        return "icon $icon $maneuver · ${distance(f.text(4, 8), f.c(8))} to turn · ETA ${eta(f.text(9, 15))} · " +
            "${distance(f.text(18, 22), f.c(22))} left · status $status $statusName · $active"
    }

    /** '2': name in bytes 2..21, source in byte 22, ringing or answered in byte 23. */
    fun call(f: ByteArray): String {
        val state = when (f.c(23)) {
            CallStatus.RINGING.code -> "ringing"
            CallStatus.ANSWERED.code -> "answered"
            else -> "'${f.c(23)}' unknown"
        }
        return "name \"${f.text(2, 22)}\" · source ${source(f.c(22))} · $state"
    }

    /** "0250" + 'M' -> "250 m", "01.2" + 'K' -> "1.2 km". */
    fun distance(text: String, unit: Char): String {
        val trimmed = text.trimStart('0').let { if (it.isEmpty() || it.startsWith(".")) "0$it" else it }
        return when (unit) {
            'M' -> "$trimmed m"
            'K' -> "$trimmed km"
            else -> "$text '$unit'"
        }
    }

    /** "1055AM" -> "10:55 AM". Other text is returned as it is. */
    fun eta(text: String): String =
        if (text.length == 6) "${text.take(2)}:${text.substring(2, 4)} ${text.substring(4)}" else text
}
