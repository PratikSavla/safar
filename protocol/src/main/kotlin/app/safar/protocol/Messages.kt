package app.safar.protocol

/*
 * App -> bike call and message frames (types '2', '4', '5'). Port of docs/helper/src/messages.ts.
 * The bike shows only a name and a count. It never gets message text.
 */

const val CALL_TYPE = '2'
const val MISSED_CALL_TYPE = '4'
const val MESSAGE_TYPE = '5'

const val NAME_FIELD_LENGTH = 20
const val MAX_NAME_LENGTH = 19

/** Highest count the missed-call and message frames carry (0xFF is the filler byte). */
const val MAX_COUNT = 0xFE

/** Send each frame this many times, with [SEND_GAP_MS] between writes (app v2.15, docs 5.2). */
const val SEND_REPEAT = 3
const val SEND_GAP_MS = 200L

/** Alert source byte: 'N' = phone call or SMS, 'W' = WhatsApp. */
enum class MessageSource(val code: Char) { PHONE('N'), WHATSAPP('W') }

/** Byte 23 of the call frame. */
enum class CallStatus(val code: Char) { RINGING('1'), ANSWERED('2') }

private const val SPECIAL_CHARS = "!#&$%'(;)<=>?@`{|}~.^,"
private val PHONE_NUMBER = Regex("^\\+?[0-9$JS_SPACE-]+$")
private val PHONE_SEPARATORS = Regex("[+$JS_SPACE-]")
private val SPACE_RUN = Regex(" {2,}")

/**
 * Make a name safe for the cluster: printable ASCII only, no special characters, max 19 characters.
 * Each Unicode code point outside 0x20..0x7E (an emoji, an accented letter) becomes one space.
 */
fun sanitizeName(raw: String): String {
    var name = jsTrim(raw)
    if (PHONE_NUMBER.matches(name)) name = name.replace(PHONE_SEPARATORS, "")
    name = buildString {
        name.codePoints().forEach { cp ->
            val printable = cp in 0x20..0x7E && cp.toChar() !in SPECIAL_CHARS
            append(if (printable) cp.toChar() else ' ')
        }
    }
    name = name.replace(SPACE_RUN, " ").trim()
    if (name.length >= NAME_FIELD_LENGTH) name = name.take(MAX_NAME_LENGTH)
    return name
}

/** Sanitised name, NUL-padded to exactly [NAME_FIELD_LENGTH] characters. */
internal fun nameField(name: String): String = sanitizeName(name).padEnd(NAME_FIELD_LENGTH, '\u0000')

private fun checkCount(count: Int): Int {
    if (count !in 0..MAX_COUNT) throw FrameException("count must be an integer 0..254, got $count")
    return count
}

/** Type '2': incoming call ringing or answered. */
fun encodeCall(name: String, status: CallStatus, source: MessageSource, mode: ChecksumMode): ByteArray {
    val body = CALL_TYPE + nameField(name) + source.code + status.code + "\u0000".repeat(4)
    return buildFrame(body, mode, (24..27).associateWith { FILLER_BYTE })
}

/** Type '4': missed call. Send count 0 when the rider clears the missed calls. */
fun encodeMissedCall(name: String, count: Int, source: MessageSource, mode: ChecksumMode): ByteArray {
    val body = MISSED_CALL_TYPE + "\u0000\u0000" + nameField(name) + source.code + "\u0000".repeat(3)
    val overrides = mapOf(2 to FILLER_BYTE, 3 to checkCount(count)) + (25..27).associateWith { FILLER_BYTE }
    return buildFrame(body, mode, overrides)
}

/** Type '5': SMS or WhatsApp message received. Send count 0 when the rider clears the messages. */
fun encodeMessage(sender: String, count: Int, source: MessageSource, mode: ChecksumMode): ByteArray {
    val body = MESSAGE_TYPE + "\u0000" + "Y" + "\u0000" + nameField(sender) + source.code + "\u0000\u0000"
    val overrides = mapOf(2 to FILLER_BYTE, 4 to checkCount(count), 26 to FILLER_BYTE, 27 to FILLER_BYTE)
    return buildFrame(body, mode, overrides)
}
