package app.safar.phone

// WhatsApp notification -> alert kind (docs 5.7). Pure Kotlin: the listener copies the fields it needs.

/** WhatsApp and WhatsApp Business. */
val WHATSAPP_PACKAGES: Set<String> = setOf("com.whatsapp", "com.whatsapp.w4b")

/** `Notification.CATEGORY_*` values (literal, so this file needs no Android import). */
const val CATEGORY_CALL = "call"
const val CATEGORY_MISSED_CALL = "missed_call"
const val CATEGORY_MESSAGE = "msg"

/** `Notification.CallStyle` call types (`android.callType`, API 31). */
const val CALL_TYPE_INCOMING = 1
const val CALL_TYPE_ONGOING = 2

/** The parts of a WhatsApp notification the classifier reads. [text] is read for call words only, never kept. */
data class WaNotification(
    val key: String,
    val category: String? = null,
    val title: String? = null,
    val text: String? = null,
    val groupSummary: Boolean = false,
    val ongoing: Boolean = false,
    val chronometer: Boolean = false,
    val fullScreenIntent: Boolean = false,
    /** `android.callType` of a CallStyle notification, or null. */
    val callType: Int? = null,
    /** `android.template` is MessagingStyle. */
    val messagingStyle: Boolean = false,
    /** Number of entries in `android.messages`: the unread messages of this chat. 0 when absent. */
    val messageCount: Int = 0,
)

enum class WaKind { CALL_RINGING, CALL_ONGOING, MISSED_CALL, MESSAGE, IGNORE }

private val INCOMING_CALL = Regex("incoming (voice |video )?call", RegexOption.IGNORE_CASE)
private val ONGOING_CALL = Regex("ongoing (voice |video )?call", RegexOption.IGNORE_CASE)
private val MISSED_CALL = Regex("missed (voice |video )?calls?", RegexOption.IGNORE_CASE)
private val OUTGOING_CALL = Regex("^(calling|ringing)", RegexOption.IGNORE_CASE)
private val LEADING_COUNT = Regex("^\\s*(\\d+)\\s")
private val MESSAGES_SUFFIX = Regex("\\s*\\((\\d+) (new )?messages?\\)\\s*$", RegexOption.IGNORE_CASE)

/**
 * Category first (docs 5.7 note), then the CallStyle type, then the English texts of the original app
 * ("Incoming voice call", "Ongoing voice call", "missed voice call"). Group summaries are ignored: the per-chat
 * notifications carry the counts. Outgoing calls ("Calling…", "Ringing…") send nothing.
 */
fun classifyWhatsApp(n: WaNotification): WaKind {
    if (n.groupSummary) return WaKind.IGNORE
    return when (n.category) {
        CATEGORY_CALL -> classifyCall(n)
        CATEGORY_MISSED_CALL -> WaKind.MISSED_CALL
        CATEGORY_MESSAGE -> if (n.ongoing) WaKind.IGNORE else WaKind.MESSAGE
        else -> classifyByText(n)
    }
}

private fun classifyCall(n: WaNotification): WaKind {
    val text = n.text.orEmpty()
    return when {
        n.callType == CALL_TYPE_INCOMING -> WaKind.CALL_RINGING
        n.callType == CALL_TYPE_ONGOING -> WaKind.CALL_ONGOING
        INCOMING_CALL.containsMatchIn(text) -> WaKind.CALL_RINGING
        ONGOING_CALL.containsMatchIn(text) -> WaKind.CALL_ONGOING
        MISSED_CALL.containsMatchIn(text) -> WaKind.MISSED_CALL
        OUTGOING_CALL.containsMatchIn(text.trim()) -> WaKind.IGNORE
        n.chronometer -> WaKind.CALL_ONGOING
        n.fullScreenIntent -> WaKind.CALL_RINGING
        else -> WaKind.IGNORE
    }
}

private fun classifyByText(n: WaNotification): WaKind {
    val text = n.text.orEmpty()
    return when {
        // A chat whose last message reads "missed call" is still a chat.
        n.messagingStyle && n.title != null && !n.ongoing -> WaKind.MESSAGE
        INCOMING_CALL.containsMatchIn(text) -> WaKind.CALL_RINGING
        ONGOING_CALL.containsMatchIn(text) -> WaKind.CALL_ONGOING
        MISSED_CALL.containsMatchIn(text) -> WaKind.MISSED_CALL
        else -> WaKind.IGNORE
    }
}

/** Unread messages of one chat: the MessagingStyle entries, else "(3 messages)" in the title, else 1. */
fun unreadCount(n: WaNotification): Int {
    if (n.messageCount > 0) return n.messageCount
    val suffix = MESSAGES_SUFFIX.find(n.title.orEmpty())?.groupValues?.get(1)?.toIntOrNull()
    return suffix?.coerceAtLeast(1) ?: 1
}

/** Missed calls in one notification: "2 missed voice calls" -> 2, else 1. */
fun missedCount(n: WaNotification): Int =
    LEADING_COUNT.find(n.text.orEmpty())?.groupValues?.get(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1

/** Chat or caller name from the title, without the "(3 messages)" suffix. Never the message text. */
fun waSenderName(n: WaNotification): String =
    n.title.orEmpty().replace(MESSAGES_SUFFIX, "").trim().ifEmpty { "WhatsApp" }
