package app.safar.protocol

/*
 * Frame layer of the Suzuki Ride Connect BLE protocol (port of docs/helper/src/frame.ts).
 *
 * Every message in both directions is one 30-byte frame:
 *   [0]      0xA5 start byte
 *   [1]      message type (one ASCII character)
 *   [2..27]  payload (26 bytes)
 *   [28]     checksum
 *   [29]     0x7F end byte
 */

const val FRAME_LENGTH = 30
const val START_BYTE = 0xA5
const val END_BYTE = 0x7F
const val CHECKSUM_INDEX = 28
const val PAYLOAD_START = 2
const val PAYLOAD_END = 27 // inclusive

/** Byte the app writes into payload positions that carry no data. */
const val FILLER_BYTE = 0xFF

/**
 * Checksum mode depends on the vehicle family.
 * - [SUM]: sum of bytes 1..27, low 8 bits. BLE name char[1] is 'B' (V-Strom SX, Gixxer, ...).
 * - [INVERTED]: 0xFF minus that sum. BLE name char[1] is 'A' (Access 125, early Burgman Street).
 */
enum class ChecksumMode { SUM, INVERTED }

/** Thrown for frames or field values that the protocol cannot encode or decode. */
class FrameException(message: String) : IllegalArgumentException(message)

/** Result of [validateFrame]. [reason] is null when the frame is valid. */
sealed interface ValidationResult {
    val ok: Boolean
    val reason: String?

    data object Valid : ValidationResult {
        override val ok: Boolean get() = true
        override val reason: String? get() = null
    }

    data class Invalid(override val reason: String) : ValidationResult {
        override val ok: Boolean get() = false
    }
}

/** Unsigned value of the byte at [index]. */
internal fun ByteArray.u8(index: Int): Int = this[index].toInt() and 0xFF

fun computeChecksum(frame: ByteArray, mode: ChecksumMode): Int {
    if (frame.size < FRAME_LENGTH) {
        throw FrameException("frame too short: ${frame.size} bytes, need $FRAME_LENGTH")
    }
    var sum = 0
    for (i in 1..PAYLOAD_END) sum = (sum + frame.u8(i)) and 0xFF
    return if (mode == ChecksumMode.INVERTED) 0xFF - sum else sum
}

fun validateFrame(frame: ByteArray, mode: ChecksumMode): ValidationResult {
    if (frame.size != FRAME_LENGTH) {
        return ValidationResult.Invalid("length ${frame.size}, expected $FRAME_LENGTH")
    }
    if (frame.u8(0) != START_BYTE) {
        return ValidationResult.Invalid("start byte 0x${hex(frame.u8(0))}, expected 0xa5")
    }
    if (frame.u8(FRAME_LENGTH - 1) != END_BYTE) {
        return ValidationResult.Invalid("end byte 0x${hex(frame.u8(29))}, expected 0x7f")
    }
    val expected = computeChecksum(frame, mode)
    val actual = frame.u8(CHECKSUM_INDEX)
    if (actual != expected) {
        return ValidationResult.Invalid("checksum 0x${hex(actual)}, expected 0x${hex(expected)}")
    }
    return ValidationResult.Valid
}

/** Message type of a frame (for example '7' for cluster data). NUL for a frame shorter than 2 bytes. */
fun messageType(frame: ByteArray): Char = if (frame.size > 1) frame.u8(1).toChar() else '\u0000'

/**
 * Build a frame from an ASCII payload template.
 * [body] holds bytes 1..27 (type byte + 26 payload bytes) and must be 27 characters.
 * Use [overrides] to put raw (non-ASCII) bytes at absolute frame positions, for example 0xFF filler.
 */
fun buildFrame(body: String, mode: ChecksumMode, overrides: Map<Int, Int> = emptyMap()): ByteArray {
    if (body.length != PAYLOAD_END) {
        throw FrameException(
            "body must be $PAYLOAD_END characters (type + payload), got ${body.length}: \"$body\"",
        )
    }
    val frame = ByteArray(FRAME_LENGTH)
    frame[0] = START_BYTE.toByte()
    body.forEachIndexed { i, ch ->
        if (ch.code > 0x7F) throw FrameException("non-ASCII character at body index $i: \"$ch\"")
        frame[i + 1] = ch.code.toByte()
    }
    for ((pos, value) in overrides) {
        if (pos < 1 || pos > PAYLOAD_END) {
            throw FrameException("override index $pos is outside 1..$PAYLOAD_END")
        }
        frame[pos] = (value and 0xFF).toByte()
    }
    frame[CHECKSUM_INDEX] = computeChecksum(frame, mode).toByte()
    frame[FRAME_LENGTH - 1] = END_BYTE.toByte()
    return frame
}

/** Lower-case hex of [value], at least 2 digits. */
fun hex(value: Int): String = value.toString(16).padStart(2, '0')

/** Space-separated lower-case hex, for example "a5 37 ... 7f". */
fun toHex(frame: ByteArray): String = frame.joinToString(" ") { hex(it.toInt() and 0xFF) }

/** Parse hex text. Every non-hex character (spaces, colons, ...) is ignored. */
fun fromHex(text: String): ByteArray {
    val clean = text.filter { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    if (clean.length % 2 != 0) throw FrameException("hex string has an odd number of digits")
    return ByteArray(clean.length / 2) { i -> clean.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
}

/** Right-align a number as zero-padded ASCII digits, keeping the last [width] digits. */
fun digits(value: Long, width: Int): String {
    if (value < 0) throw FrameException("value must be a non-negative number, got $value")
    return value.toString().padStart(width, '0').takeLast(width)
}

/** Right-align a number as zero-padded ASCII digits, keeping the last [width] digits. */
fun digits(value: Int, width: Int): String = digits(value.toLong(), width)

/** Read text from absolute frame positions [start, end). Each byte becomes one char (Latin-1). */
fun ascii(frame: ByteArray, start: Int, end: Int): String =
    buildString(end - start) { for (i in start until end) append(frame.u8(i).toChar()) }
