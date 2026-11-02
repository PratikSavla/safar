package app.safar.log

import android.util.Log
import app.safar.protocol.CALL_TYPE
import app.safar.protocol.CHECKSUM_INDEX
import app.safar.protocol.ChecksumMode
import app.safar.protocol.FRAME_LENGTH
import app.safar.protocol.LogEntry
import app.safar.protocol.MESSAGE_TYPE
import app.safar.protocol.MISSED_CALL_TYPE
import app.safar.protocol.RIDER_TYPE
import app.safar.protocol.computeChecksum
import app.safar.protocol.fromHex
import app.safar.protocol.messageType
import app.safar.protocol.toHex
import app.safar.protocol.validateFrame
import kotlinx.serialization.json.JsonPrimitive

/**
 * "Hide names" for log exports (docs 12.6, 12.7): name bytes in frames '2', '4', '5', '6' become '*' and
 * the checksum is calculated again; person names, bike names, MAC addresses and serials in event details are
 * masked. Other layers must put such values under one of the keys below so the export can hide them.
 */
object LogPrivacy {
    private const val TAG = "SafarLog"

    /** '*' */
    const val MASK_BYTE = 0x2A
    private const val MASK_CHAR = '*'

    /** Detail keys whose whole value is a person's name (caller, sender, rider). */
    val NAME_KEYS: Set<String> = setOf("name", "caller", "sender", "rider", "contact", "person")

    /** Detail keys holding a cluster BLE name. The model code (first 5 chars) stays, the rest is masked. */
    val BIKE_KEYS: Set<String> = setOf("bike", "bikeName", "device")

    /** Detail keys holding a MAC address. The vendor half stays. */
    val ADDRESS_KEYS: Set<String> = setOf("address", "mac")

    /** Detail keys holding a device serial number. Masked completely. */
    val SERIAL_KEYS: Set<String> = setOf("serial")

    private const val BIKE_KEEP = 5
    private const val MAC_KEEP = 8 // "AA:BB:CC"

    /** Absolute frame positions of the name field per message type (docs 5.4, 5.5, 5.6, 6.2). */
    fun nameRange(type: Char): IntRange? = when (type) {
        CALL_TYPE, RIDER_TYPE -> 2..21
        MISSED_CALL_TYPE -> 4..23
        MESSAGE_TYPE -> 5..24
        else -> null
    }

    /**
     * Copy of [frame] with every name character replaced by '*'. NUL padding stays, so the name length is
     * still visible. The checksum is recalculated with the mode the frame was valid in; a frame that was
     * invalid in both modes keeps its original checksum byte.
     */
    fun maskFrame(frame: ByteArray): ByteArray {
        val out = frame.copyOf()
        val range = nameRange(messageType(frame)) ?: return out
        if (frame.size != FRAME_LENGTH) return out
        val mode = ChecksumMode.entries.firstOrNull { validateFrame(frame, it).ok }
        for (i in range) {
            val b = out[i].toInt() and 0xFF
            if (b != 0x00 && b != 0xFF) out[i] = MASK_BYTE.toByte()
        }
        if (mode != null) out[CHECKSUM_INDEX] = computeChecksum(out, mode).toByte()
        return out
    }

    /** "SBM01A1B2C3D" becomes "SBM01*******". */
    fun maskBikeName(name: String): String =
        if (name.length <= BIKE_KEEP) name else name.take(BIKE_KEEP) + MASK_CHAR.toString().repeat(name.length - BIKE_KEEP)

    fun maskAddress(address: String): String =
        if (address.length <= MAC_KEEP) address else address.take(MAC_KEEP) + address.drop(MAC_KEEP).map {
            if (it == ':') ':' else MASK_CHAR
        }.joinToString("")

    private fun maskAll(text: String): String = text.map { if (it == ' ') ' ' else MASK_CHAR }.joinToString("")

    /** Masked value for one event detail, or the value unchanged when [key] is not sensitive. */
    fun maskDetail(key: String, value: String): String = when (key) {
        in NAME_KEYS, in SERIAL_KEYS -> maskAll(value)
        in BIKE_KEYS -> maskBikeName(value)
        in ADDRESS_KEYS -> maskAddress(value)
        else -> value
    }

    fun maskEntry(entry: LogEntry): LogEntry = when (entry) {
        is LogEntry.Frame -> maskFrameEntry(entry)
        is LogEntry.Event -> entry.copy(detail = entry.detail?.mapValues { (key, value) -> maskPrimitive(key, value) })
    }

    private fun maskFrameEntry(entry: LogEntry.Frame): LogEntry.Frame {
        if (nameRange(entry.type) == null) return entry
        val bytes = try {
            fromHex(entry.hex)
        } catch (e: IllegalArgumentException) {
            // Not valid hex: hide the whole text rather than risk exporting a name.
            Log.w(TAG, "Masking a '${entry.type}' frame with unreadable hex", e)
            return entry.copy(hex = maskAll(entry.hex))
        }
        return entry.copy(hex = toHex(maskFrame(bytes)))
    }

    private fun maskPrimitive(key: String, value: JsonPrimitive): JsonPrimitive {
        if (!value.isString) return value
        val masked = maskDetail(key, value.content)
        return if (masked == value.content) value else JsonPrimitive(masked)
    }
}
