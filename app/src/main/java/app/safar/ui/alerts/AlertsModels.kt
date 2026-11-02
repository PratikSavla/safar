package app.safar.ui.alerts

import app.safar.core.ActiveCall
import app.safar.core.AlertSource
import app.safar.core.FeedItem
import app.safar.core.FeedKind
import app.safar.data.Settings
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Glyph of a feed line. */
enum class FeedIcon { CALL, WHATSAPP, MISSED, MESSAGE, CLEARED }

/**
 * One line of the "sent to your bike" feed, ready to draw. [time] is the clock ("14:05"); [exactTime] adds the
 * seconds for Developer mode. [alarm] draws the mark in rust.
 */
data class FeedRow(
    val icon: FeedIcon,
    val name: String,
    val what: String,
    val time: String,
    val exactTime: String,
    val atMillis: Long,
    val alarm: Boolean,
)

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.UK)
private val CLOCK_SECONDS = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.UK)

/** Feed newest first, with what showed on the bike. Never message text: [FeedItem] has none. */
fun feedRows(feed: List<FeedItem>, zone: ZoneId = ZoneId.systemDefault()): List<FeedRow> =
    feed.sortedByDescending { it.atMillis }.map { item ->
        val wa = item.source == AlertSource.WHATSAPP
        val waiting = item.count?.let { " · $it waiting" } ?: ""
        val (icon, what) = when (item.kind) {
            FeedKind.CALL_RINGING -> (if (wa) FeedIcon.WHATSAPP else FeedIcon.CALL) to "${callWord(wa)} · ringing"
            FeedKind.CALL_ANSWERED -> (if (wa) FeedIcon.WHATSAPP else FeedIcon.CALL) to "${callWord(wa)} · answered"
            FeedKind.MISSED_CALL -> FeedIcon.MISSED to "Missed call$waiting"
            FeedKind.MESSAGE -> (if (wa) FeedIcon.WHATSAPP else FeedIcon.MESSAGE) to "${if (wa) "WhatsApp message" else "Message"}$waiting"
            FeedKind.CLEARED -> FeedIcon.CLEARED to "Cleared on your bike too"
        }
        val at = Instant.ofEpochMilli(item.atMillis).atZone(zone)
        FeedRow(icon, item.name, what, CLOCK.format(at), CLOCK_SECONDS.format(at), item.atMillis, item.kind == FeedKind.MISSED_CALL)
    }

private fun callWord(wa: Boolean) = if (wa) "WhatsApp call" else "Phone call"

/** Is this source switched on for the bike? */
fun callForwarded(call: ActiveCall, settings: Settings): Boolean =
    if (call.source == AlertSource.WHATSAPP) settings.alertWhatsAppCalls else settings.alertCalls

/** Kicker and explanation for the incoming call card. */
fun callCardText(call: ActiveCall, settings: Settings): Pair<String, String> {
    val kind = callWord(call.source == AlertSource.WHATSAPP)
    val status = if (call.answered) "answered" else "ringing"
    val line = when {
        call.answered -> "Answered · your bike shows the call"
        callForwarded(call, settings) -> "Ringing · name on your bike"
        call.source == AlertSource.WHATSAPP -> "Ringing · WhatsApp call alerts are off"
        else -> "Ringing · phone call alerts are off"
    }
    return "$kind · $status" to line
}

/** Small line under a counter: who was last, or that nothing waits. */
fun tallyLine(count: Int, lastName: String?): String = if (count > 0 && lastName != null) "last: $lastName" else if (count > 0) "waiting" else "none waiting"
