package app.safar.core

/** Navigation status byte 23 (docs 4.3). [code] is the ASCII digit sent to the cluster. */
enum class NavStatusCode(val code: Char) {
    NO_NETWORK('0'), NORMAL('1'), RECALCULATING('2'), VIA_POINT('3'),
    GPS_OFF('4'), ARRIVED('5'), NO_INTERNET('6'),
}

/**
 * Exactly what the cluster receives on the next navigation tick, plus the raw text it came from.
 * [iconCode] is 1..45 (docs 4.4). [iconMatched] is false when the large-icon hash had no match and the
 * code came from the instruction text.
 */
data class NavSnapshot(
    val iconCode: Int,
    val iconMatched: Boolean,
    val distanceToTurnM: Int,
    val etaMillis: Long,
    val remainingM: Int,
    val status: NavStatusCode,
    val instruction: String,
    val receivedAtMillis: Long,
)

/**
 * Google Maps bridge state. [waiting] = rider opened Maps from Safar but no notification yet.
 * [snapshot] is null until the first parsed update (docs 4.5 step 4, 11.7).
 */
data class NavState(
    val active: Boolean = false,
    val waiting: Boolean = false,
    val destinationLabel: String? = null,
    val snapshot: NavSnapshot? = null,
)

/** 'N' = phone call / SMS, 'W' = WhatsApp (docs 5.1). */
enum class AlertSource(val code: Char) { PHONE('N'), WHATSAPP('W') }

data class ActiveCall(
    val name: String,
    val source: AlertSource,
    val answered: Boolean,
    val sinceMillis: Long,
)

/** Missed calls or unread messages: what the cluster shows (name of the latest + count). */
data class AlertCounter(
    val count: Int = 0,
    val lastName: String? = null,
    val source: AlertSource = AlertSource.PHONE,
)

enum class FeedKind { CALL_RINGING, CALL_ANSWERED, MISSED_CALL, MESSAGE, CLEARED }

/** One line of the "sent to cluster" feed on the alerts screen. Never holds message text. */
data class FeedItem(
    val atMillis: Long,
    val kind: FeedKind,
    val name: String,
    val source: AlertSource,
    val count: Int? = null,
)

data class AlertsState(
    val call: ActiveCall? = null,
    val missed: AlertCounter = AlertCounter(),
    val messages: AlertCounter = AlertCounter(source = AlertSource.WHATSAPP),
    val feed: List<FeedItem> = emptyList(),
)

/** Phone facts that go into the status frame '3' (docs 6.3). */
data class PhoneSnapshot(
    val batteryPercent: Int = 100,
    val charging: Boolean = false,
    /** 0..3 bars; 0 for airplane mode or no SIM. */
    val signalBars: Int = 0,
    val hasInternet: Boolean = true,
    val gpsOn: Boolean = true,
)

/**
 * Cellular counts as fine while the phone has internet (Wi-Fi hotspot, poor or unknown bars), because Google Maps
 * keeps routing then. The one rule for both the phone's navigation status and the cluster's status byte.
 */
val PhoneSnapshot.cellularOk: Boolean get() = signalBars > 0 || hasInternet

/** Phone GPS. [speedKmh] is null without a recent fix. [moving] = above 10 km/h (UX rule 3 locks input). */
data class RideState(
    val speedKmh: Float? = null,
    val topSpeedKmh: Float = 0f,
    val moving: Boolean = false,
    val recordingTripId: Long? = null,
    val lastFixAtMillis: Long? = null,
)
