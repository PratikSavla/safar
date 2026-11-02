package app.safar.ui.alerts

import app.safar.core.ActiveCall
import app.safar.core.AlertCounter
import app.safar.core.AlertSource
import app.safar.core.AlertsState
import app.safar.core.BikeState
import app.safar.core.FeedItem
import app.safar.core.FeedKind
import app.safar.core.LinkState
import app.safar.data.Settings
import app.safar.ui.testing.FakeAlerts
import app.safar.ui.testing.FakeBike
import app.safar.ui.testing.FakeSettings
import app.safar.ui.testing.MainDispatcherRule
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AlertsTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun feedIsNewestFirstWithWhatWentToTheBike() {
        val rows = feedRows(
            listOf(
                FeedItem(1_000, FeedKind.MISSED_CALL, "Rider", AlertSource.PHONE, 3),
                FeedItem(61_000, FeedKind.MESSAGE, "Alex", AlertSource.WHATSAPP, 2),
                FeedItem(30_000, FeedKind.CALL_RINGING, "Alex", AlertSource.PHONE),
            ),
            ZoneOffset.UTC,
        )
        assertEquals(listOf(61_000L, 30_000L, 1_000L), rows.map { it.atMillis })
        assertEquals("WhatsApp message · 2 waiting", rows[0].what)
        assertEquals(FeedIcon.WHATSAPP, rows[0].icon)
        assertEquals("Phone call · ringing", rows[1].what)
        assertEquals("Missed call · 3 waiting", rows[2].what)
        assertEquals("00:00", rows[2].time)
        assertEquals("00:00:01", rows[2].exactTime)
        assertTrue(rows[2].alarm)
        assertFalse(rows[0].alarm)
    }

    @Test
    fun callCardSaysWhetherTheNameWasSent() {
        val wa = ActiveCall("Alex", AlertSource.WHATSAPP, answered = false, sinceMillis = 0)
        assertEquals("WhatsApp call · ringing" to "Ringing · name on your bike", callCardText(wa, Settings()))
        assertEquals("Ringing · WhatsApp call alerts are off", callCardText(wa, Settings(alertWhatsAppCalls = false)).second)
        val phone = wa.copy(source = AlertSource.PHONE)
        assertEquals("Ringing · phone call alerts are off", callCardText(phone, Settings(alertCalls = false)).second)
        val answered = phone.copy(answered = true)
        assertEquals("Phone call · answered" to "Answered · your bike shows the call", callCardText(answered, Settings()))
    }

    @Test
    fun tallyLineNamesTheLastSender() {
        assertEquals("last: Alex", tallyLine(2, "Alex"))
        assertEquals("none waiting", tallyLine(0, null))
    }

    @Test
    fun viewModelClearsCountersAndSavesSwitches() = runTest {
        val alerts = FakeAlerts(AlertsState(missed = AlertCounter(2, "Rider")))
        val settings = FakeSettings()
        val vm = AlertsViewModel(alerts, settings, FakeBike(BikeState(link = LinkState.Connected)))
        backgroundScope.launch(main.dispatcher) { vm.state.collect {} }
        assertTrue(vm.state.value.bikeConnected)
        assertEquals(2, vm.state.value.alerts.missed.count)
        vm.clearMissed()
        vm.clearMessages()
        assertEquals(1, alerts.missedCleared)
        assertEquals(1, alerts.messagesCleared)
        vm.setWhatsAppMessages(false)
        vm.setCalls(false)
        assertFalse(vm.state.value.settings.alertWhatsAppMessages)
        assertFalse(settings.settings.value.alertCalls)
        assertTrue(settings.settings.value.alertWhatsAppCalls)
    }
}
