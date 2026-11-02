package app.safar.sim

import app.safar.protocol.CALL_TYPE
import app.safar.protocol.ChecksumMode
import app.safar.protocol.FILLER_BYTE
import app.safar.protocol.FRAME_LENGTH
import app.safar.protocol.MESSAGE_TYPE
import app.safar.protocol.MISSED_CALL_TYPE
import app.safar.protocol.NAVIGATION_TYPE
import app.safar.protocol.PHONE_STATUS_TYPE
import app.safar.protocol.RIDER_TYPE
import app.safar.protocol.ascii
import app.safar.protocol.messageType
import app.safar.protocol.validateFrame
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Field readers for the frames the app writes (docs 4.2, 5.4-5.6, 6.2, 6.3). Each one first checks the frame
 * with the protocol's [validateFrame] in [mode] and its type byte.
 */
class TxFrames(private val mode: ChecksumMode) {
    data class Rider(val name: String, val connection: Char)

    data class Status(
        val battery: Char,
        val charging: Char,
        /** null when bytes 4..6 are FF FF FF (alert off). */
        val speedAlert: String?,
        val bars: Char,
        val clock: String,
        val messagesCleared: Char,
        val missedCleared: Char,
    )

    data class Nav(
        val icon: Int,
        val turn: String,
        val left: String,
        val eta: String,
        val status: Char,
        val active: Char,
    )

    data class Call(val name: String, val source: Char, val status: Char)
    data class Count(val name: String, val count: Int, val source: Char)

    fun rider(f: ByteArray) = checked(f, RIDER_TYPE).let { Rider(text(it, 2, 22), it.c(27)) }

    fun status(f: ByteArray) = checked(f, PHONE_STATUS_TYPE).let {
        val alertOff = (4..6).all { i -> it.u(i) == FILLER_BYTE }
        Status(it.c(2), it.c(3), if (alertOff) null else ascii(it, 4, 7), it.c(7), ascii(it, 8, 14), it.c(14), it.c(15))
    }

    fun nav(f: ByteArray) = checked(f, NAVIGATION_TYPE).let {
        Nav(it.u(2), ascii(it, 4, 9), ascii(it, 18, 23), ascii(it, 9, 15), it.c(23), it.c(24))
    }

    fun call(f: ByteArray) = checked(f, CALL_TYPE).let { Call(text(it, 2, 22), it.c(22), it.c(23)) }

    fun missed(f: ByteArray) = checked(f, MISSED_CALL_TYPE).let { Count(text(it, 4, 24), it.u(3), it.c(24)) }

    fun message(f: ByteArray) = checked(f, MESSAGE_TYPE).let {
        assertEquals('Y', it.c(3), "message frame byte 3")
        Count(text(it, 5, 25), it.u(4), it.c(25))
    }

    private fun checked(f: ByteArray, type: Char): ByteArray {
        assertEquals(FRAME_LENGTH, f.size, "frame length")
        val check = validateFrame(f, mode)
        assertTrue(check.ok, "frame '${messageType(f)}' invalid in $mode: ${check.reason}")
        assertEquals(type, messageType(f), "message type")
        return f
    }

    private fun text(f: ByteArray, start: Int, end: Int): String = ascii(f, start, end).trimEnd('\u0000')

    private fun ByteArray.u(i: Int): Int = this[i].toInt() and 0xFF
    private fun ByteArray.c(i: Int): Char = u(i).toChar()
}
