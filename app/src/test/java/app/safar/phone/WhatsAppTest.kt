package app.safar.phone

import app.safar.core.AlertSource
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WhatsAppTest {
    private fun wa(key: String = "k1", title: String? = "Alex", text: String? = null, category: String? = null) =
        WaNotification(key = key, category = category, title = title, text = text)

    @Test
    fun categoryDecidesFirst() {
        assertEquals(WaKind.MISSED_CALL, classifyWhatsApp(wa(category = CATEGORY_MISSED_CALL, text = "anything")))
        assertEquals(WaKind.MESSAGE, classifyWhatsApp(wa(category = CATEGORY_MESSAGE, text = "Incoming voice call")))
        val incoming = wa(category = CATEGORY_CALL).copy(callType = CALL_TYPE_INCOMING)
        assertEquals(WaKind.CALL_RINGING, classifyWhatsApp(incoming))
        assertEquals(WaKind.CALL_ONGOING, classifyWhatsApp(incoming.copy(callType = CALL_TYPE_ONGOING)))
    }

    @Test
    fun callCategoryWithoutTypeUsesTextThenFlags() {
        val call = wa(category = CATEGORY_CALL)
        assertEquals(WaKind.CALL_RINGING, classifyWhatsApp(call.copy(text = "Incoming video call")))
        assertEquals(WaKind.CALL_ONGOING, classifyWhatsApp(call.copy(text = "Ongoing voice call")))
        assertEquals(WaKind.IGNORE, classifyWhatsApp(call.copy(text = "Calling…", ongoing = true)))
        assertEquals(WaKind.CALL_ONGOING, classifyWhatsApp(call.copy(text = "Llamada", chronometer = true)))
        assertEquals(WaKind.CALL_RINGING, classifyWhatsApp(call.copy(text = "Llamada", fullScreenIntent = true)))
        assertEquals(WaKind.IGNORE, classifyWhatsApp(call.copy(text = "Llamada")))
    }

    @Test
    fun englishTextFallbackWithoutCategory() {
        assertEquals(WaKind.CALL_RINGING, classifyWhatsApp(wa(text = "Incoming voice call")))
        assertEquals(WaKind.CALL_ONGOING, classifyWhatsApp(wa(text = "Ongoing voice call")))
        assertEquals(WaKind.MISSED_CALL, classifyWhatsApp(wa(text = "missed voice call")))
        assertEquals(WaKind.MISSED_CALL, classifyWhatsApp(wa(text = "2 missed calls")))
        assertEquals(WaKind.MESSAGE, classifyWhatsApp(wa(text = "hi").copy(messagingStyle = true)))
        assertEquals(WaKind.MESSAGE, classifyWhatsApp(wa(text = "I saw your missed call").copy(messagingStyle = true)))
        assertEquals(WaKind.IGNORE, classifyWhatsApp(wa(text = "Checking for new messages", title = "WhatsApp")))
        assertEquals(WaKind.IGNORE, classifyWhatsApp(wa(text = "Backing up", title = "WhatsApp").copy(ongoing = true)))
    }

    @Test
    fun groupSummaryIsIgnored() {
        val summary = wa(category = CATEGORY_MESSAGE, title = "WhatsApp", text = "5 messages from 2 chats")
        assertEquals(WaKind.IGNORE, classifyWhatsApp(summary.copy(groupSummary = true)))
    }

    @Test
    fun countsAndNames() {
        assertEquals(3, unreadCount(wa().copy(messageCount = 3)))
        assertEquals(4, unreadCount(wa(title = "Alex (4 messages)")))
        assertEquals(1, unreadCount(wa()))
        assertEquals("Alex", waSenderName(wa(title = "Alex (4 messages)")))
        assertEquals("WhatsApp", waSenderName(wa(title = null)))
        assertEquals(2, missedCount(wa(text = "2 missed voice calls")))
        assertEquals(1, missedCount(wa(text = "missed voice call")))
    }

    @Test
    fun repostedChatCountsOnlyNewMessages() {
        val rig = AlertRig()
        val alerts = WhatsAppAlerts(rig.center, rig.clock)
        val chat = wa(key = "chat-alex", category = CATEGORY_MESSAGE, text = "hello")
        alerts.posted(chat.copy(messageCount = 1))
        alerts.posted(chat.copy(messageCount = 1)) // same notification refreshed
        alerts.posted(chat.copy(messageCount = 3))
        alerts.posted(wa(key = "chat-rider", title = "Rider", category = CATEGORY_MESSAGE).copy(messageCount = 1))
        alerts.posted(wa(key = "summary", title = "WhatsApp", category = CATEGORY_MESSAGE).copy(groupSummary = true))
        assertEquals(listOf("5:Alex:1:W", "5:Alex:3:W", "5:Rider:4:W"), rig.outbox.frames)
        assertTrue(rig.center.state.value.feed.none { it.name.contains("hello") }, "never message text")
    }

    @Test
    fun removingAllChatsSendsZero() {
        val rig = AlertRig()
        val alerts = WhatsAppAlerts(rig.center, rig.clock)
        alerts.posted(wa(key = "a", category = CATEGORY_MESSAGE))
        alerts.posted(wa(key = "b", title = "Rider", category = CATEGORY_MESSAGE))
        alerts.removed("a")
        assertEquals(2, rig.outbox.frames.size, "one chat still unread: screen only")
        assertEquals(1, rig.center.state.value.messages.count)
        alerts.removed("b")
        assertEquals("5::0:W", rig.outbox.frames.last())
        alerts.removed("b")
        assertEquals(3, rig.outbox.frames.size)
    }

    @Test
    fun readingOneChatDropsItsMessages() {
        val rig = AlertRig()
        val alerts = WhatsAppAlerts(rig.center, rig.clock)
        alerts.posted(wa(key = "a", category = CATEGORY_MESSAGE).copy(messageCount = 5))
        alerts.posted(wa(key = "b", title = "Rider", category = CATEGORY_MESSAGE).copy(messageCount = 1))
        assertEquals("5:Rider:6:W", rig.outbox.frames.last())
        alerts.removed("a")
        assertEquals(1, rig.center.state.value.messages.count)
        alerts.posted(wa(key = "b", title = "Rider", category = CATEGORY_MESSAGE).copy(messageCount = 2))
        assertEquals(listOf("5:Alex:5:W", "5:Rider:6:W", "5:Rider:2:W"), rig.outbox.frames)
    }

    @Test
    fun removingOneMissedCallNotificationDropsItsCalls() {
        val rig = AlertRig()
        val alerts = WhatsAppAlerts(rig.center, rig.clock)
        alerts.posted(wa(key = "m1", category = CATEGORY_MISSED_CALL, text = "2 missed voice calls"))
        alerts.posted(wa(key = "m2", title = "Rider", category = CATEGORY_MISSED_CALL, text = "missed voice call"))
        alerts.removed("m1")
        assertEquals(1, rig.center.state.value.missed.count)
        alerts.posted(wa(key = "m2", title = "Rider", category = CATEGORY_MISSED_CALL, text = "2 missed voice calls"))
        assertEquals("4:Rider:2:W", rig.outbox.frames.last())
    }

    @Test
    fun seededChatsCountOnlyNewMessages() {
        val rig = AlertRig()
        val alerts = WhatsAppAlerts(rig.center, rig.clock)
        val chat = wa(key = "a", category = CATEGORY_MESSAGE).copy(messageCount = 4)
        val missed = wa(key = "m", title = "Rider", category = CATEGORY_MISSED_CALL, text = "2 missed voice calls")
        alerts.seed(listOf(chat, missed))
        assertTrue(rig.outbox.frames.isEmpty(), "seeding sends nothing")
        alerts.posted(chat.copy(messageCount = 5))
        assertEquals(listOf("5:Alex:1:W"), rig.outbox.frames)
        alerts.removed("a")
        assertEquals("5::0:W", rig.outbox.frames.last())
        alerts.posted(missed.copy(text = "3 missed voice calls"))
        assertEquals("4:Rider:1:W", rig.outbox.frames.last())
    }

    @Test
    fun reseedDropsChatsRemovedWhileDisconnected() {
        val rig = AlertRig()
        val alerts = WhatsAppAlerts(rig.center, rig.clock)
        alerts.posted(wa(key = "a", category = CATEGORY_MESSAGE).copy(messageCount = 2))
        alerts.posted(wa(key = "b", title = "Rider", category = CATEGORY_MESSAGE).copy(messageCount = 1))
        alerts.seed(listOf(wa(key = "b", title = "Rider", category = CATEGORY_MESSAGE).copy(messageCount = 1)))
        assertEquals(1, rig.center.state.value.messages.count)
        alerts.seed(emptyList())
        assertEquals("5::0:W", rig.outbox.frames.last())
    }

    @Test
    fun ringingThenOngoingIsAnswered() {
        val rig = AlertRig()
        val alerts = WhatsAppAlerts(rig.center, rig.clock)
        val ring = wa(key = "call", category = CATEGORY_CALL, text = "Incoming voice call")
        alerts.posted(ring)
        alerts.posted(ring) // update while ringing
        alerts.removed("call")
        rig.clock.now += 2_000
        alerts.posted(wa(key = "call2", category = CATEGORY_CALL, text = "Ongoing voice call"))
        assertEquals(listOf("2:Alex:ringing:W", "2:Alex:answered:W"), rig.outbox.frames)
    }

    @Test
    fun declinedCallIsNotAnsweredByALaterOutgoingCall() {
        val rig = AlertRig()
        val alerts = WhatsAppAlerts(rig.center, rig.clock)
        alerts.posted(wa(key = "call", category = CATEGORY_CALL, text = "Incoming voice call"))
        rig.clock.now += 3_000
        alerts.removed("call") // declined: no missed-call notification follows
        rig.clock.now += WA_HANDOFF_MS + 5_000
        alerts.posted(wa(key = "out", title = "Rider", category = CATEGORY_CALL, text = "Ongoing voice call"))
        assertEquals(listOf("2:Alex:ringing:W"), rig.outbox.frames)
        assertEquals(listOf(AlertSource.WHATSAPP), rig.outbox.stops, "the ringing repeats stop when the call is declined")
    }

    @Test
    fun ringingThenMissedCounts() {
        val rig = AlertRig()
        val alerts = WhatsAppAlerts(rig.center, rig.clock)
        alerts.posted(wa(key = "call", category = CATEGORY_CALL, text = "Incoming voice call"))
        alerts.removed("call")
        alerts.posted(wa(key = "missed", category = CATEGORY_MISSED_CALL, text = "missed voice call"))
        alerts.posted(wa(key = "missed", category = CATEGORY_MISSED_CALL, text = "2 missed voice calls"))
        assertEquals(listOf("2:Alex:ringing:W", "4:Alex:1:W", "4:Alex:2:W"), rig.outbox.frames)
        alerts.posted(wa(key = "call3", category = CATEGORY_CALL, text = "Ongoing voice call"))
        assertEquals(3, rig.outbox.frames.size, "a later call is not the answer of the missed one")
        alerts.removed("missed")
        assertEquals("4::0:W", rig.outbox.frames.last())
    }

    @Test
    fun secondRingFromSameChatAfterRemovalRingsAgain() {
        val rig = AlertRig()
        val alerts = WhatsAppAlerts(rig.center, rig.clock)
        val ring = wa(key = "call", category = CATEGORY_CALL, text = "Incoming voice call")
        alerts.posted(ring)
        alerts.removed("call")
        rig.clock.now += WA_ANSWER_WINDOW_MS * 2
        alerts.posted(ring)
        assertEquals(listOf("2:Alex:ringing:W", "2:Alex:ringing:W"), rig.outbox.frames)
    }
}
