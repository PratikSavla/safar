package app.safar.protocol

import java.time.LocalTime

/*
 * App -> bike rider frame (type '6') and phone status frame (type '3').
 * Port of docs/helper/src/phone-status.ts.
 */

const val PHONE_STATUS_TYPE = '3'
const val RIDER_TYPE = '6'

/** App v2.15: 2 s timer, 3 writes with 150 ms gaps. App v7.6 timing is in [Timing]. */
const val STATUS_PERIOD_MS = 2000L
const val STATUS_REPEAT = 3
const val STATUS_GAP_MS = 150L

/**
 * Type '6': rider name. Send on the first timer tick after notify is on.
 * [firstConnection] = true when this cluster differs from the last saved cluster ('F'), else 'R'.
 */
fun encodeRider(riderName: String, firstConnection: Boolean, mode: ChecksumMode): ByteArray {
    val body = RIDER_TYPE + nameField(riderName) + "\u0000".repeat(5) + (if (firstConnection) 'F' else 'R')
    return buildFrame(body, mode, (22..26).associateWith { FILLER_BYTE })
}

data class PhoneStatus(
    /** Phone battery, percent 0..100. */
    val batteryPercent: Int,
    val charging: Boolean,
    /** Speed alert limit in km/h. 0 = alert off. Scooter 5..120, Motorcycle 10..299 in the original UI. */
    val speedAlertKmh: Int,
    /** Cellular signal bars 0..3. Use 0 for airplane mode or no SIM. */
    val signalBars: Int,
    /** Phone clock. The frame uses a 12-hour clock with no AM/PM. */
    val time: LocalTime,
    /** true when the rider has cleared all message notifications (byte 14 = 'Y'). */
    val messagesCleared: Boolean,
    /** true when the rider has cleared all missed calls (byte 15 = 'Y'). */
    val missedCallsCleared: Boolean,
)

/** Battery level code: '0' < 25 %, '1' 25-49 %, '2' 50-74 %, '3' >= 75 %. */
fun batteryCode(percent: Int): Char = when {
    percent < 25 -> '0'
    percent < 50 -> '1'
    percent < 75 -> '2'
    else -> '3'
}

/** 12-hour clock "hhmmss" with no AM/PM, as the status frame carries it. */
internal fun statusClock(time: LocalTime): String {
    val h12 = if (time.hour % 12 == 0) 12 else time.hour % 12
    return digits(h12, 2) + digits(time.minute, 2) + digits(time.second, 2)
}

private fun yn(flag: Boolean): Char = if (flag) 'Y' else 'N'

/** Type '3': phone status. */
fun encodePhoneStatus(s: PhoneStatus, mode: ChecksumMode): ByteArray {
    if (s.signalBars !in 0..3) throw FrameException("signalBars must be 0..3, got ${s.signalBars}")
    if (s.speedAlertKmh !in 0..999) throw FrameException("speedAlertKmh must be 0..999, got ${s.speedAlertKmh}")
    val alertOff = s.speedAlertKmh == 0
    val body = PHONE_STATUS_TYPE.toString() +
        batteryCode(s.batteryPercent) +
        yn(s.charging) +
        (if (alertOff) "\u0000\u0000\u0000" else digits(s.speedAlertKmh, 3)) +
        s.signalBars +
        statusClock(s.time) +
        yn(s.messagesCleared) +
        yn(s.missedCallsCleared) +
        "\u0000".repeat(12)
    val overrides = (16..27).associateWith { FILLER_BYTE }.toMutableMap()
    if (alertOff) (4..6).forEach { overrides[it] = FILLER_BYTE }
    return buildFrame(body, mode, overrides)
}
