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

/** One line of the "sent to the cluster" feed, ready to draw. [alarm] draws the mark in rust. */
data class FeedRow(
    val icon: FeedIcon,
    val name: String,
    val what: String,
    val time: String,
    val atMillis: Long,
    val alarm: Boolean,
)

private val CLOCK_SECONDS = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.UK)

/** Feed newest first, with the source and what went to the bike. Never message text: [FeedItem] has none. */
fun feedRows(feed: List<FeedItem>, zone: ZoneId = ZoneId.systemDefault()): List<FeedRow> =
    feed.sortedByDescending { it.atMillis }.map { item ->
        val wa = item.source == AlertSource.WHATSAPP
        val count = item.count?.let { " · ×$it" } ?: ""
        val (icon, what) = when (item.kind) {
            FeedKind.CALL_RINGING -> (if (wa) FeedIcon.WHATSAPP else FeedIcon.CALL) to "${callWord(wa)} · ringing → cluster"
            FeedKind.CALL_ANSWERED -> (if (wa) FeedIcon.WHATSAPP else FeedIcon.CALL) to "${callWord(wa)} · answered → cluster"
            FeedKind.MISSED_CALL -> FeedIcon.MISSED to "Missed call · count + name → cluster$count"
            FeedKind.MESSAGE -> (if (wa) FeedIcon.WHATSAPP else FeedIcon.MESSAGE) to "${if (wa) "WhatsApp message" else "Message"} · count + name → cluster$count"
            FeedKind.CLEARED -> FeedIcon.CLEARED to "Cleared · counter reset on the cluster"
        }
        FeedRow(icon, item.name, what, CLOCK_SECONDS.format(Instant.ofEpochMilli(item.atMillis).atZone(zone)), item.atMillis, item.kind == FeedKind.MISSED_CALL)
    }

private fun callWord(wa: Boolean) = if (wa) "WhatsApp call" else "Phone call"

/** Is this source switched on for the cluster? */
fun callForwarded(call: ActiveCall, settings: Settings): Boolean =
    if (call.source == AlertSource.WHATSAPP) settings.alertWhatsAppCalls else settings.alertCalls

/** Kicker and explanation for the incoming call card. */
fun callCardText(call: ActiveCall, settings: Settings): Pair<String, String> {
    val kind = callWord(call.source == AlertSource.WHATSAPP)
    val status = if (call.answered) "answered" else "ringing"
    val line = when {
        call.answered -> "Answered · the cluster shows the call as live"
        callForwarded(call, settings) -> "Ringing · name sent to the cluster"
        else -> "Ringing · cluster alert is off for this source"
    }
    return "$kind · $status" to line
}

/** Small line under a counter: who was last, or that nothing waits. */
fun tallyLine(count: Int, lastName: String?): String = if (count > 0 && lastName != null) "last: $lastName" else if (count > 0) "waiting" else "none waiting"
