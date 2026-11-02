package app.safar.phone

import app.safar.core.AlertSource
import app.safar.core.FeedKind
import app.safar.data.Settings
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AlertCountersTest {
    @Test
    fun clearMissedCallsSendsZeroFrame() {
        val rig = AlertRig()
        rig.center.callMissed("Alex", AlertSource.PHONE)
        rig.center.callMissed("Rider", AlertSource.WHATSAPP)
        assertEquals(2, rig.center.state.value.missed.count)
        rig.center.clearMissedCalls()
        assertEquals("4::0:W", rig.outbox.frames.last(), "0-count frame with the latest source")
        assertEquals(0, rig.center.state.value.missed.count)
        rig.center.callMissed("Alex", AlertSource.PHONE)
        assertEquals("4:Alex:1:N", rig.outbox.frames.last(), "counts restart after a clear")
    }

    @Test
    fun clearMessagesSendsZeroFrame() {
        val rig = AlertRig()
        rig.center.messageReceived("Alex", 2)
        rig.center.messageReceived("Rider")
        assertEquals("5:Rider:3:W", rig.outbox.frames.last())
        rig.center.clearMessages()
        assertEquals("5::0:W", rig.outbox.frames.last())
        assertEquals(0, rig.center.state.value.messages.count)
        assertEquals(FeedKind.CLEARED, rig.center.state.value.feed.first().kind)
    }

    @Test
    fun whatsAppMissedClearKeepsPhonePart() {
        val rig = AlertRig()
        rig.center.callMissed("Alex", AlertSource.PHONE)
        rig.center.callMissed("Rider", AlertSource.WHATSAPP, 2)
        assertEquals("4:Rider:3:W", rig.outbox.frames.last())
        val sent = rig.outbox.frames.size
        rig.center.whatsAppMissedCleared()
        assertEquals(1, rig.center.state.value.missed.count)
        assertEquals(sent, rig.outbox.frames.size, "a partial drop updates the screen only")
    }

    @Test
    fun whatsAppMissedClearToZeroSendsFrame() {
        val rig = AlertRig()
        rig.center.callMissed("Rider", AlertSource.WHATSAPP)
        rig.center.whatsAppMissedCleared()
        assertEquals("4::0:W", rig.outbox.frames.last())
        rig.center.whatsAppMissedCleared()
        assertEquals(2, rig.outbox.frames.size, "nothing left to clear")
    }

    @Test
    fun countsCapAtProtocolMaximum() {
        val rig = AlertRig()
        rig.center.messageReceived("Alex", 300)
        assertEquals(254, rig.center.state.value.messages.count)
        assertEquals("5:Alex:254:W", rig.outbox.frames.last())
    }

    @Test
    fun feedKeepsFiftyNewest() {
        val rig = AlertRig()
        repeat(60) { i ->
            rig.clock.now += 1000
            rig.center.messageReceived("Rider $i")
        }
        val feed = rig.center.state.value.feed
        assertEquals(FEED_LIMIT, feed.size)
        assertEquals("Rider 59", feed.first().name)
        assertEquals("Rider 10", feed.last().name)
    }

    @Test
    fun switchesGateEachKind() {
        val rig = AlertRig(Settings(alertWhatsAppMessages = false, alertWhatsAppCalls = false))
        rig.center.messageReceived("Alex")
        rig.center.callRinging("Alex", AlertSource.WHATSAPP)
        rig.center.callMissed("Alex", AlertSource.WHATSAPP)
        assertTrue(rig.outbox.frames.isEmpty())
        rig.center.callRinging("Rider", AlertSource.PHONE)
        assertEquals(listOf("2:Rider:ringing:N"), rig.outbox.frames)
    }

    @Test
    fun missingOutboxIsLoggedNotThrown() {
        val log = RecordingLog()
        val center = AlertCenterImpl({ null }, { Settings() }, log)
        center.messageReceived("Alex")
        assertEquals(1, center.state.value.messages.count)
        assertTrue("alert.no_outbox" in log.events)
    }
}
