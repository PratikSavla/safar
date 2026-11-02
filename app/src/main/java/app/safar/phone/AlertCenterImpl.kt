package app.safar.phone

import app.safar.core.ActiveCall
import app.safar.core.AlertCenter
import app.safar.core.AlertCounter
import app.safar.core.AlertSource
import app.safar.core.AlertsState
import app.safar.core.ClusterOutbox
import app.safar.core.FeedItem
import app.safar.core.FeedKind
import app.safar.data.Settings
import app.safar.protocol.MAX_COUNT
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Most recent feed lines kept for the alerts screen. */
const val FEED_LIMIT = 50

/**
 * Call and WhatsApp alert state, and the frames that go with it (docs 5). Callers are [CallMonitor] (phone
 * calls) and [WhatsAppAlerts]; the rider clears counters from the alerts screen. Only names and counts are
 * kept: never message text. Each alert kind respects its switch in [Settings].
 *
 * Missed calls keep one counter on the cluster (name and source of the latest), but the phone and WhatsApp
 * parts are counted apart so removing WhatsApp's missed-call notifications only drops the WhatsApp part.
 * When WhatsApp notifications are removed, the WhatsApp counts drop to what the remaining notifications still
 * hold. A count frame is sent when the count goes up or drops to 0; a partial drop updates the screen only, so
 * the cluster does not pop a new alert for a smaller number.
 *
 * Pure Kotlin and thread-safe: [outbox] and [settings] are read lazily on each event.
 */
class AlertCenterImpl(
    private val outbox: () -> ClusterOutbox?,
    private val settings: () -> Settings,
    private val log: PhoneEventLog,
    private val clock: () -> Long = System::currentTimeMillis,
) : AlertCenter {
    private val lock = Any()
    private val mutable = MutableStateFlow(AlertsState())
    override val state: StateFlow<AlertsState> = mutable.asStateFlow()

    private var phoneMissed = 0
    private var whatsAppMissed = 0

    fun callRinging(name: String, source: AlertSource) {
        if (!enabled(source, isMessage = false, what = "call")) return
        synchronized(lock) {
            mutable.value = mutable.value.copy(call = ActiveCall(name, source, answered = false, sinceMillis = clock()))
            feed(FeedKind.CALL_RINGING, name, source)
        }
        send("call") { it.sendCall(name, source, answered = false) }
    }

    /** The call was answered. [name] is used when the ringing notification is already gone (WhatsApp). */
    fun callAnswered(name: String, source: AlertSource) {
        if (!enabled(source, isMessage = false, what = "answered")) return
        val callName = synchronized(lock) {
            val current = mutable.value.call
            if (current != null && current.source == source && current.answered) return
            val callName = current?.takeIf { it.source == source }?.name ?: name
            val since = current?.takeIf { it.source == source }?.sinceMillis ?: clock()
            mutable.value = mutable.value.copy(call = ActiveCall(callName, source, answered = true, sinceMillis = since))
            feed(FeedKind.CALL_ANSWERED, callName, source)
            callName
        }
        send("answered") { it.sendCall(callName, source, answered = true) }
    }

    /**
     * The call of [source] ended. No frame: the original app sends no "call end" (docs 5.4); but a ringing frame
     * still repeating stops, so the cluster does not show a call that is gone.
     */
    fun callEnded(source: AlertSource) {
        synchronized(lock) {
            if (mutable.value.call?.source == source) mutable.value = mutable.value.copy(call = null)
        }
        send("call ended") { it.stopRinging(source) }
    }

    /** [added] new missed calls from [name]. */
    fun callMissed(name: String, source: AlertSource, added: Int = 1) {
        if (added <= 0) return
        if (!enabled(source, isMessage = false, what = "missed")) {
            callEnded(source)
            return
        }
        val total = synchronized(lock) {
            if (source == AlertSource.WHATSAPP) whatsAppMissed += added else phoneMissed += added
            val total = (phoneMissed + whatsAppMissed).coerceAtMost(MAX_COUNT)
            val call = mutable.value.call?.takeIf { it.source != source }
            mutable.value = mutable.value.copy(call = call, missed = AlertCounter(total, name, source))
            feed(FeedKind.MISSED_CALL, name, source, total)
            total
        }
        send("missed") { it.sendMissedCalls(name, total, source) }
    }

    /** WhatsApp's missed-call notifications that are still posted hold [left] missed calls. Never raises. */
    fun whatsAppMissedLeft(left: Int) {
        val zero = synchronized(lock) {
            val next = left.coerceIn(0, whatsAppMissed)
            if (next == whatsAppMissed) return
            whatsAppMissed = next
            val total = (phoneMissed + whatsAppMissed).coerceAtMost(MAX_COUNT)
            mutable.value = mutable.value.copy(missed = mutable.value.missed.copy(count = total))
            if (total == 0) feed(FeedKind.CLEARED, "", AlertSource.WHATSAPP, 0)
            total == 0
        }
        if (zero) send("missed cleared") { it.sendMissedCalls("", 0, AlertSource.WHATSAPP) }
    }

    /** [added] new WhatsApp messages; [sender] is the chat title, never the text. */
    fun messageReceived(sender: String, added: Int = 1) {
        if (added <= 0 || !enabled(AlertSource.WHATSAPP, isMessage = true, what = "message")) return
        val total = synchronized(lock) {
            val total = (mutable.value.messages.count + added).coerceAtMost(MAX_COUNT)
            mutable.value = mutable.value.copy(messages = AlertCounter(total, sender, AlertSource.WHATSAPP))
            feed(FeedKind.MESSAGE, sender, AlertSource.WHATSAPP, total)
            total
        }
        send("message") { it.sendMessages(sender, total, AlertSource.WHATSAPP) }
    }

    /**
     * WhatsApp's chat notifications that are still posted hold [left] unread messages (a chat was read or
     * dismissed). Never raises the count: after the rider cleared it, older chats do not come back.
     */
    fun whatsAppMessagesLeft(left: Int) {
        val zero = synchronized(lock) {
            val current = mutable.value.messages
            val next = left.coerceIn(0, current.count)
            if (next == current.count) return
            if (next > 0) mutable.value = mutable.value.copy(messages = current.copy(count = next))
            next == 0
        }
        if (zero) clearMessages()
    }

    override fun clearMissedCalls() {
        val source = synchronized(lock) {
            phoneMissed = 0
            whatsAppMissed = 0
            val source = mutable.value.missed.source
            mutable.value = mutable.value.copy(missed = AlertCounter(source = source))
            feed(FeedKind.CLEARED, "", source, 0)
            source
        }
        send("missed cleared") { it.sendMissedCalls("", 0, source) }
    }

    override fun clearMessages() {
        synchronized(lock) {
            mutable.value = mutable.value.copy(messages = AlertCounter(source = AlertSource.WHATSAPP))
            feed(FeedKind.CLEARED, "", AlertSource.WHATSAPP, 0)
        }
        send("messages cleared") { it.sendMessages("", 0, AlertSource.WHATSAPP) }
    }

    private fun enabled(source: AlertSource, isMessage: Boolean, what: String): Boolean {
        val s = settings()
        val on = when {
            isMessage -> s.alertWhatsAppMessages
            source == AlertSource.WHATSAPP -> s.alertWhatsAppCalls
            else -> s.alertCalls
        }
        if (!on) log.info("alert.off", mapOf("kind" to what, "source" to source.code.toString()))
        return on
    }

    /** Caller holds [lock]. */
    private fun feed(kind: FeedKind, name: String, source: AlertSource, count: Int? = null) {
        val item = FeedItem(clock(), kind, name, source, count)
        mutable.value = mutable.value.copy(feed = (listOf(item) + mutable.value.feed).take(FEED_LIMIT))
    }

    private fun send(what: String, block: (ClusterOutbox) -> Unit) {
        val target = outbox()
        if (target == null) {
            log.error("alert.no_outbox", mapOf("kind" to what))
            return
        }
        block(target)
    }
}
