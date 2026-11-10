package app.safar.ble

import app.safar.core.BikeModel
import app.safar.core.ClusterReading
import app.safar.core.NavSnapshot
import app.safar.core.NavStatusCode
import app.safar.core.PhoneSnapshot
import app.safar.core.cellularOk
import app.safar.data.Settings
import app.safar.protocol.CLUSTER_TYPE
import app.safar.protocol.ChecksumMode
import app.safar.protocol.FrameException
import app.safar.protocol.MAX_ICON_CODE
import app.safar.protocol.MIN_ICON_CODE
import app.safar.protocol.NavStatus
import app.safar.protocol.NavigationData
import app.safar.protocol.PhoneStatus
import app.safar.protocol.decodeCluster
import app.safar.protocol.messageType
import app.safar.protocol.sanitizeName
import app.safar.protocol.validateFrame
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

/** Rider name on the cluster when Settings has none (docs 6.2). */
const val DEFAULT_RIDER_NAME = "RIDER"

/** App v7.6 sends the rider name in uppercase (docs 6.2). Empty after cleaning: [DEFAULT_RIDER_NAME]. */
fun riderNameFor(settings: Settings): String =
    sanitizeName(settings.riderName.uppercase(Locale.ROOT)).ifEmpty { DEFAULT_RIDER_NAME }

/** Speed alert byte value (docs 6.3): 0 = off, else the setting clamped to the model range (docs 12.4 R10). */
fun speedAlertFor(settings: Settings, model: BikeModel): Int =
    if (!settings.speedAlertOn) 0 else settings.speedAlertKmh.coerceIn(model.alertRange)

fun buildPhoneStatus(
    phone: PhoneSnapshot,
    settings: Settings,
    model: BikeModel,
    time: LocalTime,
    cleared: ClearedFlags,
): PhoneStatus = PhoneStatus(
    batteryPercent = phone.batteryPercent.coerceIn(0, 100),
    charging = phone.charging,
    speedAlertKmh = speedAlertFor(settings, model),
    signalBars = phone.signalBars.coerceIn(0, 3),
    time = time,
    messagesCleared = cleared.messages,
    missedCallsCleared = cleared.missedCalls,
)

data class ClearedFlags(val messages: Boolean, val missedCalls: Boolean)

/**
 * Status bytes 14 and 15 (docs 6.3): 'Y' for [holdTicks] status ticks after the count drops to 0, like the
 * original app. A new message or missed call during the hold ends it at once.
 */
class ClearedFlagTracker(private val holdTicks: Int = HOLD_TICKS) {
    private var lastMessages: Int? = null
    private var lastMissed: Int? = null
    private var messagesLeft = 0
    private var missedLeft = 0

    /** Call once per status tick with the current counts. */
    fun tick(messages: Int, missedCalls: Int): ClearedFlags {
        messagesLeft = next(lastMessages, messages, messagesLeft)
        missedLeft = next(lastMissed, missedCalls, missedLeft)
        lastMessages = messages
        lastMissed = missedCalls
        val flags = ClearedFlags(messagesLeft > 0, missedLeft > 0)
        if (messagesLeft > 0) messagesLeft--
        if (missedLeft > 0) missedLeft--
        return flags
    }

    private fun next(last: Int?, now: Int, left: Int): Int = when {
        now > 0 -> 0
        last != null && last > 0 -> holdTicks
        else -> left
    }

    companion object {
        const val HOLD_TICKS = 4
    }
}

/**
 * Navigation status byte (docs 4.3) with its priority: GPS off, no network, no internet, reroute, arrived, via.
 * No network uses the phone layer's rule ([cellularOk]), so the cluster agrees with what the app shows.
 *
 * When [mapsActive] is true, Google Maps is actively sending turn notifications, which proves that routing
 * is working even without cellular or internet (e.g. offline maps in Airplane mode). In that case, skip
 * the network and internet checks so the cluster keeps showing turn arrows. GPS still gates navigation.
 */
fun navStatusFor(
    code: NavStatusCode,
    phone: PhoneSnapshot,
    rerouteAlreadySent: Boolean,
    mapsActive: Boolean = false,
): NavStatus = when {
    !phone.gpsOn || code == NavStatusCode.GPS_OFF -> NavStatus.GPS_OFF
    !mapsActive && (!phone.cellularOk || code == NavStatusCode.NO_NETWORK) -> NavStatus.NO_NETWORK_SIGNAL
    !mapsActive && (!phone.hasInternet || code == NavStatusCode.NO_INTERNET) -> NavStatus.NO_INTERNET
    code == NavStatusCode.RECALCULATING && !rerouteAlreadySent -> NavStatus.REROUTING
    code == NavStatusCode.ARRIVED -> NavStatus.DESTINATION_REACHED
    code == NavStatusCode.VIA_POINT -> NavStatus.VIA_POINT_REACHED
    else -> NavStatus.NORMAL
}

/**
 * [NavSnapshot] to protocol [NavigationData]. Keeps the last valid icon for an out-of-range code (the
 * original app sends the previous icon again) and sends "rerouting" for one frame per snapshot (docs 4.3).
 */
class NavFrameMapper(private val zone: () -> ZoneId) {
    constructor(zone: ZoneId) : this({ zone })

    private var lastIcon = DEFAULT_ICON
    private var rerouteSentFor: Long? = null

    fun map(snapshot: NavSnapshot, phone: PhoneSnapshot, active: Boolean): NavigationData {
        if (snapshot.iconCode in MIN_ICON_CODE..MAX_ICON_CODE) lastIcon = snapshot.iconCode
        val sent = rerouteSentFor == snapshot.receivedAtMillis
        val status = navStatusFor(snapshot.status, phone, sent, mapsActive = active)
        if (status == NavStatus.REROUTING) rerouteSentFor = snapshot.receivedAtMillis
        return NavigationData(
            iconCode = lastIcon,
            distanceToTurnM = snapshot.distanceToTurnM.coerceAtLeast(0),
            // The phone side turns Maps' local ETA into epoch millis with the zone of that moment; read it again here.
            eta = Instant.ofEpochMilli(snapshot.etaMillis).atZone(zone()).toLocalTime(),
            remainingM = snapshot.remainingM.coerceAtLeast(0),
            status = status,
            active = active,
        )
    }

    companion object {
        /** Go straight: shown until the first snapshot with a valid icon. */
        const val DEFAULT_ICON = 8
    }
}

/** Result of one RX notification. */
sealed interface RxResult {
    data class Reading(val reading: ClusterReading) : RxResult
    /** Failed a check of docs 3.2: discard, log, do not show the rider. */
    data class Invalid(val reason: String) : RxResult
    /** A valid frame of another type. */
    data class Other(val type: Char) : RxResult
}

/**
 * Decode an RX frame with the vehicle checksum [mode] (docs 3.2). [firstFrame] = the first cluster frame since
 * the connection, whose fuel value is discarded (docs 3.3). Gear is byte 23 when it is a letter or a digit.
 */
fun decodeRx(frame: ByteArray, mode: ChecksumMode, firstFrame: Boolean, atMillis: Long): RxResult {
    val check = validateFrame(frame, mode)
    if (!check.ok) return RxResult.Invalid(check.reason ?: "invalid frame")
    if (messageType(frame) != CLUSTER_TYPE) return RxResult.Other(messageType(frame))
    val data = try {
        decodeCluster(frame, mode)
    } catch (e: FrameException) {
        return RxResult.Invalid(e.message ?: "cluster fields do not parse")
    }
    val b23 = data.unknown.b23
    val gearChar = b23.toChar()
    val gear = gearChar.takeIf { it in '0'..'9' || it in 'A'..'Z' || it in 'a'..'z' }
    return RxResult.Reading(
        ClusterReading(
            speedKmh = data.speedKmh,
            odometerKm = data.odometerKm,
            tripAKm = data.tripAKm,
            tripBKm = data.tripBKm,
            fuelBars = if (firstFrame) null else data.fuelBars,
            gear = gear,
            raw23 = b23,
            raw25 = data.unknown.b25,
            receivedAtMillis = atMillis,
        ),
    )
}
