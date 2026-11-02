package app.safar.bikesim.sim

import app.safar.protocol.CALL_TYPE
import app.safar.protocol.CLUSTER_TYPE
import app.safar.protocol.ChecksumMode
import app.safar.protocol.FRAME_LENGTH
import app.safar.protocol.MESSAGE_TYPE
import app.safar.protocol.MISSED_CALL_TYPE
import app.safar.protocol.NAVIGATION_TYPE
import app.safar.protocol.PHONE_STATUS_TYPE
import app.safar.protocol.RIDER_TYPE
import app.safar.protocol.ValidationResult
import app.safar.protocol.hex
import app.safar.protocol.messageType
import app.safar.protocol.toHex
import app.safar.protocol.validateFrame

/** One received frame in readable form. [checksumOk] is the result for the simulator's checksum mode. */
data class FrameDescription(
    val type: Char?,
    val typeName: String,
    val text: String,
    val checksumOk: Boolean,
    val checksumText: String,
    val hex: String,
)

/** Turns frames that Safar writes to FFF1 into one line of text per frame. Never throws. */
object FrameDescriber {
    private val TYPE_NAMES = mapOf(
        RIDER_TYPE to "Rider",
        PHONE_STATUS_TYPE to "Status",
        NAVIGATION_TYPE to "Navigation",
        CALL_TYPE to "Call",
        MISSED_CALL_TYPE to "Missed call",
        MESSAGE_TYPE to "Message",
        CLUSTER_TYPE to "Cluster",
    )

    fun typeName(type: Char?): String = TYPE_NAMES[type] ?: "Unknown"

    fun describe(frame: ByteArray, mode: ChecksumMode): FrameDescription {
        val type = if (frame.size > 1) messageType(frame) else null
        val hexText = toHex(frame)
        if (frame.size != FRAME_LENGTH) {
            val text = "${frame.size} bytes, expected $FRAME_LENGTH"
            return FrameDescription(type, typeName(type), text, false, "not checked", hexText)
        }
        val (ok, checksumText) = checksum(frame, mode)
        return FrameDescription(type, typeName(type), fields(frame, type), ok, checksumText, hexText)
    }

    private fun checksum(frame: ByteArray, mode: ChecksumMode): Pair<Boolean, String> {
        val result = validateFrame(frame, mode)
        val label = if (mode == ChecksumMode.INVERTED) "inverted" else "sum"
        if (result is ValidationResult.Valid) return true to "0x${hex(frame.u(28))} $label ok"
        val other = if (mode == ChecksumMode.INVERTED) ChecksumMode.SUM else ChecksumMode.INVERTED
        val hint = if (validateFrame(frame, other).ok) " (passes the other checksum mode)" else ""
        return false to "${result.reason}$hint"
    }

    private fun fields(f: ByteArray, type: Char?): String = when (type) {
        RIDER_TYPE -> FrameFields.rider(f)
        PHONE_STATUS_TYPE -> FrameFields.status(f)
        NAVIGATION_TYPE -> FrameFields.navigation(f)
        CALL_TYPE -> FrameFields.call(f)
        MISSED_CALL_TYPE -> "count ${f.u(3)} · name \"${f.text(4, 24)}\" · source ${source(f.c(24))}"
        MESSAGE_TYPE -> "count ${f.u(4)} · sender \"${f.text(5, 25)}\" · source ${source(f.c(25))}"
        CLUSTER_TYPE -> "speed ${f.text(2, 5)} · odometer ${f.text(5, 11)} · fuel ${f.c(24)}"
        else -> "type 0x${hex(f.u(1))} is not in the protocol guide"
    }
}

internal fun ByteArray.u(i: Int): Int = this[i].toInt() and 0xFF

internal fun ByteArray.c(i: Int): Char = u(i).toChar()

/** Printable ASCII between [start] and [end]; NUL and 0xFF filler are dropped. */
internal fun ByteArray.text(start: Int, end: Int): String =
    (start until end).map { u(it) }.filter { it in 0x20..0x7E }.joinToString("") { it.toChar().toString() }.trim()

internal fun source(c: Char): String = when (c) {
    'N' -> "N phone"
    'W' -> "W WhatsApp"
    else -> "'$c' unknown"
}

internal fun yesNo(c: Char): String = when (c) {
    'Y' -> "yes"
    'N' -> "no"
    else -> "'$c'"
}
