package app.safar.phone

import app.safar.core.NavSnapshot
import app.safar.core.NavStatusCode
import app.safar.core.PhoneSnapshot
import app.safar.protocol.MapsNotification
import app.safar.protocol.NavStatus
import app.safar.protocol.NavUpdate
import app.safar.protocol.PhoneConditions
import app.safar.protocol.splitTitle
import app.safar.protocol.toNavigationData
import app.safar.protocol.updateFromNotification
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

// Google Maps notification -> NavSnapshot (docs 11.3 steps 4-7). Pure Kotlin: no Android imports.

/** Category of the Google Maps navigation notification. */
const val CATEGORY_NAVIGATION = "navigation"

/** Icon sent before any maneuver is known: "continue straight" (docs 4.4). */
const val DEFAULT_ICON_CODE = 8

/** The text fields of a Google Maps navigation notification, as read from `Notification.extras`. */
data class MapsFields(
    val title: String? = null,
    val shortCriticalText: String? = null,
    val subText: String? = null,
    val text: String? = null,
    val progress: Int? = null,
    val progressMax: Int? = null,
)

/** One parsed route state. [iconCode] is always 1..45: the last known maneuver, else [DEFAULT_ICON_CODE]. */
data class MapsTurn(
    val update: NavUpdate,
    val iconCode: Int,
    val iconMatched: Boolean,
    val instruction: String,
    val receivedAtMillis: Long,
)

/**
 * Merge one notification into [previous] (null for a new route). [matchedIcon] is the code from the icon
 * table, null when the large icon had no match; then the instruction text decides. A field that does not
 * parse keeps its previous value (docs 11.7), including the instruction when the title is empty.
 */
fun reduceMaps(
    previous: MapsTurn?,
    fields: MapsFields,
    matchedIcon: Int?,
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): MapsTurn {
    val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
    val notification = MapsNotification(
        title = fields.title.orEmpty(),
        shortCriticalText = fields.shortCriticalText,
        subText = fields.subText.orEmpty(),
        text = fields.text,
        progress = fields.progress,
        progressMax = fields.progressMax,
        iconCode = matchedIcon,
    )
    val update = updateFromNotification(previous?.update, notification, now)
    val instruction = splitTitle(fields.title.orEmpty()).instruction.ifBlank { previous?.instruction.orEmpty() }
    return MapsTurn(
        update = update,
        iconCode = update.iconCode ?: previous?.iconCode ?: DEFAULT_ICON_CODE,
        iconMatched = matchedIcon != null,
        instruction = instruction,
        receivedAtMillis = nowMillis,
    )
}

/**
 * Phone facts for the navigation status byte. Cellular counts as fine while the phone has internet (Wi-Fi
 * hotspot, or signal bars unknown without permission), because Google Maps keeps routing then.
 */
fun phoneConditions(phone: PhoneSnapshot): PhoneConditions =
    PhoneConditions(gpsOn = phone.gpsOn, cellularOk = phone.signalBars > 0 || phone.hasInternet, internetOk = phone.hasInternet)

fun navStatusCode(status: NavStatus): NavStatusCode = NavStatusCode.entries.first { it.code == status.code }

/** What the cluster receives on the next navigation tick, given the phone's current state. */
fun MapsTurn.toSnapshot(phone: PhoneSnapshot, zone: ZoneId = ZoneId.systemDefault()): NavSnapshot {
    val data = toNavigationData(update, iconCode, phoneConditions(phone))
    return NavSnapshot(
        iconCode = data.iconCode,
        iconMatched = iconMatched,
        distanceToTurnM = data.distanceToTurnM,
        etaMillis = update.eta.atZone(zone).toInstant().toEpochMilli(),
        remainingM = data.remainingM,
        status = navStatusCode(data.status),
        instruction = instruction,
        receivedAtMillis = receivedAtMillis,
    )
}

/** Raw fields for the debug log (docs 12.6). Missing fields are left out. */
fun MapsFields.toLogDetail(iconHash: String?, iconCode: Int?): Map<String, String> = buildMap {
    title?.let { put("title", it) }
    shortCriticalText?.let { put("shortCriticalText", it) }
    subText?.let { put("subText", it) }
    text?.let { put("text", it) }
    progress?.let { put("progress", it.toString()) }
    progressMax?.let { put("progressMax", it.toString()) }
    iconHash?.let { put("iconHash", it) }
    put("iconCode", iconCode?.toString() ?: "none")
}
