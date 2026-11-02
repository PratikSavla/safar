package app.safar.phone

import app.safar.core.AlertSource

/**
 * An "ongoing call" notification counts as the answer only this soon after the last ringing update, so an
 * outgoing call later on is not taken for the answer of an old ring.
 */
const val WA_ANSWER_WINDOW_MS = 60_000L

/**
 * Turns WhatsApp notifications into [AlertCenterImpl] events (docs 5.7). WhatsApp re-posts a chat's
 * notification on each new message, so counts are tracked per notification key and only the increase is
 * reported. When the last notification of a kind is removed, the count drops to 0 and the 0-count frame goes
 * out. Pure Kotlin; the listener calls it on its main thread, and the methods are synchronized anyway.
 */
class WhatsAppAlerts(
    private val center: AlertCenterImpl,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** [posted] is false once the ringing notification is gone; an answer may still follow it. */
    private data class Ringing(val key: String, val name: String, val atMillis: Long, val posted: Boolean = true)

    private val callKeys = HashSet<String>()
    private val missedByKey = HashMap<String, Int>()
    private val unreadByKey = HashMap<String, Int>()
    private var ringing: Ringing? = null

    @Synchronized
    fun posted(n: WaNotification) {
        when (classifyWhatsApp(n)) {
            WaKind.CALL_RINGING -> onRinging(n)
            WaKind.CALL_ONGOING -> onOngoing(n)
            WaKind.MISSED_CALL -> onMissed(n)
            WaKind.MESSAGE -> onMessage(n)
            WaKind.IGNORE -> Unit
        }
    }

    @Synchronized
    fun removed(key: String) {
        ringing?.takeIf { it.key == key }?.let { ringing = it.copy(posted = false) }
        if (callKeys.remove(key) && callKeys.isEmpty()) center.callEnded(AlertSource.WHATSAPP)
        if (missedByKey.remove(key) != null && missedByKey.isEmpty()) center.whatsAppMissedCleared()
        if (unreadByKey.remove(key) != null && unreadByKey.isEmpty()) center.whatsAppMessagesCleared()
    }

    private fun onRinging(n: WaNotification) {
        callKeys += n.key
        val name = waSenderName(n)
        val current = ringing
        // WhatsApp updates the ringing notification while it rings: send the ringing frame once per call.
        if (current != null && current.posted && current.key == n.key && current.name == name) {
            ringing = current.copy(atMillis = clock())
            return
        }
        ringing = Ringing(n.key, name, clock())
        center.callRinging(name, AlertSource.WHATSAPP)
    }

    private fun onOngoing(n: WaNotification) {
        callKeys += n.key
        val current = ringing ?: return // outgoing call or already answered
        ringing = null
        if (clock() - current.atMillis <= WA_ANSWER_WINDOW_MS) center.callAnswered(current.name, AlertSource.WHATSAPP)
    }

    private fun onMissed(n: WaNotification) {
        ringing = null
        val count = missedCount(n)
        val previous = missedByKey.put(n.key, count) ?: 0
        if (count > previous) center.callMissed(waSenderName(n), AlertSource.WHATSAPP, count - previous)
    }

    private fun onMessage(n: WaNotification) {
        val count = unreadCount(n)
        val previous = unreadByKey.put(n.key, count) ?: 0
        if (count > previous) center.messageReceived(waSenderName(n), count - previous)
    }
}
