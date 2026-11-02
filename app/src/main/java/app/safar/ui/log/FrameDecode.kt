package app.safar.ui.log

import app.safar.protocol.CHECKSUM_INDEX
import app.safar.protocol.CLUSTER_TYPE
import app.safar.protocol.ChecksumMode
import app.safar.protocol.END_BYTE
import app.safar.protocol.FILLER_BYTE
import app.safar.protocol.FRAME_LENGTH
import app.safar.protocol.FrameException
import app.safar.protocol.NavStatus
import app.safar.protocol.START_BYTE
import app.safar.protocol.decodeCluster
import app.safar.protocol.fromHex
import app.safar.protocol.hex
import app.safar.protocol.maneuverByCode
import app.safar.protocol.validateFrame

/** Frame types by their type byte (ux shared/protocol.js TYPE_NAMES). */
val FRAME_TYPE_NAMES: List<Pair<Char, String>> = listOf(
    '7' to "Cluster", '6' to "Rider", '3' to "Status", '1' to "Navigation", '2' to "Call", '4' to "Missed call", '5' to "Message",
)

fun frameTypeName(type: Char?): String = FRAME_TYPE_NAMES.firstOrNull { it.first == type }?.second ?: "Unknown"

/** Highlight of one byte in the hex grid. */
enum class ByteRole { START, TYPE, CHECKSUM, END, UNKNOWN, PAD, DATA }

data class HexByte(val index: Int, val text: String, val role: ByteRole)

/** A frame taken apart: the type, the decoded fields in frame order, and every byte with its role. */
data class DecodedFrame(
    val type: Char?,
    val typeName: String,
    val fields: List<Pair<String, String>>,
    val bytes: List<HexByte>,
)

/** Decodes the logged hex of one frame. Never throws: problems become a "Problem" field. */
fun decodeFrame(hexText: String): DecodedFrame {
    val frame = try {
        fromHex(hexText)
    } catch (e: FrameException) {
        return DecodedFrame(null, "Unreadable", listOf("Problem" to (e.message ?: "bad hex")), emptyList())
    }
    val type = if (frame.size > 1) (frame[1].toInt() and 0xFF).toChar() else null
    val bytes = frame.mapIndexed { i, b -> HexByte(i, hex(b.toInt() and 0xFF).uppercase(), roleOf(i, b.toInt() and 0xFF, type)) }
    if (frame.size != FRAME_LENGTH) {
        return DecodedFrame(type, frameTypeName(type), listOf("Problem" to "${frame.size} bytes, expected $FRAME_LENGTH"), bytes)
    }
    val (mode, check) = checksumOf(frame)
    val fields = try {
        fieldsOf(frame, type, mode ?: ChecksumMode.SUM)
    } catch (e: FrameException) {
        listOf("Problem" to (e.message ?: "cannot decode"))
    }
    return DecodedFrame(type, frameTypeName(type), fields + ("Checksum" to check), bytes)
}

private fun roleOf(i: Int, value: Int, type: Char?): ByteRole = when {
    i == 0 && value == START_BYTE -> ByteRole.START
    i == 1 -> ByteRole.TYPE
    i == CHECKSUM_INDEX -> ByteRole.CHECKSUM
    i == FRAME_LENGTH - 1 && value == END_BYTE -> ByteRole.END
    type == CLUSTER_TYPE && i == 25 -> ByteRole.UNKNOWN
    value == FILLER_BYTE -> ByteRole.PAD
    else -> ByteRole.DATA
}

/** Which checksum mode the frame passes, and a line saying so. */
private fun checksumOf(frame: ByteArray): Pair<ChecksumMode?, String> {
    val sum = validateFrame(frame, ChecksumMode.SUM)
    if (sum.ok) return ChecksumMode.SUM to "0x${hex(frame.u(CHECKSUM_INDEX))} · sum ok"
    if (validateFrame(frame, ChecksumMode.INVERTED).ok) return ChecksumMode.INVERTED to "0x${hex(frame.u(CHECKSUM_INDEX))} · inverted ok"
    return null to (sum.reason ?: "invalid")
}

private fun fieldsOf(f: ByteArray, type: Char?, mode: ChecksumMode): List<Pair<String, String>> = when (type) {
    '7' -> clusterFields(f, mode)
    '1' -> navigationFields(f)
    '2' -> listOf("Name" to f.text(2, 22), "Source" to source(f.c(22)), "Call" to if (f.c(23) == '2') "answered" else "ringing")
    '3' -> statusFields(f)
    '4' -> listOf("Count" to f.u(3).toString(), "Name" to f.text(4, 24), "Source" to source(f.c(24)))
    '5' -> listOf("Count" to f.u(4).toString(), "Name" to f.text(5, 25), "Source" to source(f.c(25)))
    '6' -> listOf("Rider name" to f.text(2, 22), "Connection" to if (f.c(27) == 'F') "F · first with this cluster" else "R · reconnect")
    else -> listOf("Type" to "0x${hex(f.u(1))} · not in the guide")
}

private fun clusterFields(f: ByteArray, mode: ChecksumMode): List<Pair<String, String>> {
    val d = decodeCluster(f, mode)
    val gear = f.u(23)
    return listOf(
        "Speed" to "${d.speedKmh} km/h",
        "Odometer" to "${d.odometerKm} km",
        "Trip A" to "${d.tripAKm} km",
        "Trip B" to "${d.tripBKm} km",
        "Fuel bars" to d.fuelBars.toString(),
        "Byte 23 · gear?" to "0x${hex(gear)} '${printable(gear)}'",
        "Byte 25 · unknown" to "0x${hex(f.u(25))}",
    )
}

private fun navigationFields(f: ByteArray): List<Pair<String, String>> {
    val icon = f.u(2)
    val status = f.c(23)
    return listOf(
        "Icon" to "$icon · ${maneuverByCode(icon)?.name ?: if (icon == 46) "status" else "unknown"}",
        "To next turn" to "${f.text(4, 8)} ${f.c(8)}",
        "ETA" to f.text(9, 15),
        "Remaining" to "${f.text(18, 22)} ${f.c(22)}",
        "Status" to "$status · ${NavStatus.fromCode(status)?.name?.lowercase()?.replace('_', ' ') ?: "unknown"}",
        "Navigation" to if (f.c(24) == '0') "ended" else "active",
    )
}

private fun statusFields(f: ByteArray): List<Pair<String, String>> {
    val battery = when (f.c(2)) {
        '0' -> "under 25 %"
        '1' -> "25–49 %"
        '2' -> "50–74 %"
        '3' -> "75 % or more"
        else -> "?"
    }
    val alert = if (f.u(4) == FILLER_BYTE) "off" else "${f.text(4, 7).trimStart('0').ifEmpty { "0" }} km/h"
    val clock = f.text(8, 14)
    return listOf(
        "Battery" to battery,
        "Charging" to yesNo(f.c(3)),
        "Speed alert" to alert,
        "Signal" to "${f.c(7)} of 3 bars",
        "Clock" to if (clock.length == 6) "${clock.substring(0, 2)}:${clock.substring(2, 4)}:${clock.substring(4)}" else clock,
        "Messages cleared" to yesNo(f.c(14)),
        "Missed calls cleared" to yesNo(f.c(15)),
    )
}

private fun ByteArray.u(i: Int): Int = this[i].toInt() and 0xFF
private fun ByteArray.c(i: Int): Char = u(i).toChar()

/** ASCII text between [start] and [end], NUL and filler bytes dropped. */
private fun ByteArray.text(start: Int, end: Int): String =
    (start until end).map { u(it) }.filter { it in 0x20..0x7E }.joinToString("") { it.toChar().toString() }.trim()

private fun printable(v: Int): String = if (v in 0x20..0x7E) v.toChar().toString() else "·"
private fun source(c: Char): String = when (c) {
    'N' -> "N · phone"
    'W' -> "W · WhatsApp"
    else -> "$c"
}
private fun yesNo(c: Char): String = when (c) {
    'Y' -> "yes"
    'N' -> "no"
    else -> "?"
}
