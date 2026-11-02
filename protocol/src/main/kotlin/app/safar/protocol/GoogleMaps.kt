package app.safar.protocol

import java.time.LocalDateTime

/*
 * Google Maps app -> cluster navigation bridge (port of docs/helper/src/google-maps.ts).
 *
 * The rider navigates in the Google Maps app, which shows an ongoing navigation notification. The phone
 * app reads it with NotificationListenerService and passes the text fields here (docs 11).
 * Format confirmed with Google Maps 26.44.03 on Android 17 (adb shell dumpsys notification --noredact):
 *   android.title              "200 m · Turn right toward NH 48"   (no distance prefix just after start)
 *   android.shortCriticalText  "200 m"
 *   android.subText            "Arrive 11:56 am"
 *   android.text               null
 *   android.progress / progressMax   0 / 23708   (ProgressStyle, metres)
 *   android.largeIcon          113x113 turn arrow bitmap
 *   category=navigation, flags ONGOING_EVENT
 */

const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"

/** Fields of the Google Maps navigation notification (Notification.extras). */
data class MapsNotification(
    /** android.title: "200 m · Turn right toward NH 48", or only the instruction. */
    val title: String,
    /** android.shortCriticalText: "200 m". Empty or missing on some updates. */
    val shortCriticalText: String? = null,
    /** android.subText: "Arrive 11:56 am". */
    val subText: String = "",
    /** android.text. Null in Google Maps 26.44. Kept for older versions. */
    val text: String? = null,
    /** android.progress and android.progressMax (ProgressStyle), metres. Remaining = max - progress. */
    val progress: Int? = null,
    val progressMax: Int? = null,
    /** Cluster icon code from matching android.largeIcon, if the app has a match. */
    val iconCode: Int? = null,
)

/** Provider-neutral state that the phone app keeps between notifications. */
data class NavUpdate(
    /** Cluster icon code 1..45, or null when the maneuver is unknown. */
    val iconCode: Int?,
    val distanceToTurnM: Int,
    val remainingM: Int,
    val eta: LocalDateTime,
    val rerouting: Boolean,
    val arrived: Boolean,
    val active: Boolean,
)

/** Phone facts that decide the navigation status byte. */
data class PhoneConditions(val gpsOn: Boolean, val cellularOk: Boolean, val internetOk: Boolean)

private val REROUTING = Regex("rerouting|recalculating", RegexOption.IGNORE_CASE)
private val ARRIVED = Regex("arrived|you have arrived|destination is on", RegexOption.IGNORE_CASE)

/**
 * Merge one Google Maps notification into the previous state. [now] is the phone's local time.
 * Fields that do not parse keep their previous value, so one odd notification does not blank the cluster.
 */
fun updateFromNotification(previous: NavUpdate?, n: MapsNotification, now: LocalDateTime): NavUpdate {
    val title = splitTitle(n.title)
    val text = jsTrim(title.instruction + " " + (n.text ?: ""))
    val sub = parseSubText(n.subText, now)
    val distanceToTurnM = parseDistance(n.shortCriticalText ?: "") ?: parseDistance(title.distance ?: "")
    val max = n.progressMax
    val progress = n.progress
    val fromProgress = if (max != null && progress != null && max > 0) maxOf(0, max - progress) else null
    return NavUpdate(
        iconCode = n.iconCode ?: codeFromInstruction(text) ?: previous?.iconCode,
        distanceToTurnM = distanceToTurnM ?: previous?.distanceToTurnM ?: 0,
        remainingM = sub.remainingM ?: fromProgress ?: previous?.remainingM ?: 0,
        eta = sub.eta ?: previous?.eta ?: now,
        rerouting = REROUTING.containsMatchIn(text),
        arrived = ARRIVED.containsMatchIn(text),
        active = true,
    )
}

/** State to send once when Google Maps removes the navigation notification. */
fun endedUpdate(previous: NavUpdate): NavUpdate = previous.copy(active = false)

/**
 * Convert the state to cluster navigation data.
 * [previousIconCode] is used when the maneuver is unknown (same as the original app).
 */
fun toNavigationData(update: NavUpdate, previousIconCode: Int, phone: PhoneConditions): NavigationData {
    val status = when {
        !phone.gpsOn -> NavStatus.GPS_OFF
        !phone.cellularOk -> NavStatus.NO_NETWORK_SIGNAL
        !phone.internetOk -> NavStatus.NO_INTERNET
        update.rerouting -> NavStatus.REROUTING
        update.arrived -> NavStatus.DESTINATION_REACHED
        else -> NavStatus.NORMAL
    }
    return NavigationData(
        iconCode = update.iconCode ?: previousIconCode,
        distanceToTurnM = update.distanceToTurnM,
        eta = update.eta.toLocalTime(),
        remainingM = update.remainingM,
        status = status,
        active = update.active,
    )
}
