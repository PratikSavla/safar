package app.safar.ui.shell

import app.safar.core.ActiveCall
import app.safar.core.AlertCounter
import app.safar.core.AlertSource
import app.safar.core.AlertsState
import app.safar.core.LinkState
import app.safar.core.NavState
import app.safar.data.Settings
import app.safar.ui.nav.Route
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShellRulesTest {
    @Test
    fun detailPagesMarkTheirSectionTab() {
        assertEquals("route", tabOf(Route.Place(sharedText = "Main Rd")))
        assertEquals("journal", tabOf(Route.Trip(1)))
        assertEquals("today", tabOf(Route.Home))
        assertNull(tabOf(Route.Fuel))
        assertEquals(Route.Settings, TAB_ROUTES["settings"])
    }

    @Test
    fun onlyTheFiveSectionsShowTheStubs() {
        assertTrue(showsTabs(Route.Alerts))
        assertFalse(showsTabs(Route.Trip(1)))
        assertFalse(showsTabs(Route.Ride))
        assertFalse(showsTabs(null))
    }

    @Test
    fun autoRideEntersWhenMovingStartsOutsideBlockedPages() {
        assertEquals(AutoRideAction.ENTER, autoRideAction(true, false, true, Route.Home, false))
        assertEquals(AutoRideAction.NONE, autoRideAction(true, false, false, Route.Home, false))
        assertEquals(AutoRideAction.NONE, autoRideAction(true, false, true, Route.Pair, false))
        assertEquals(AutoRideAction.NONE, autoRideAction(true, false, true, Route.Onboarding, false))
        assertEquals(AutoRideAction.NONE, autoRideAction(true, true, true, Route.Home, false))
    }

    @Test
    fun autoRideLeavesOnlyWhatItOpened() {
        assertEquals(AutoRideAction.LEAVE, autoRideAction(false, true, true, Route.Ride, true))
        assertEquals(AutoRideAction.NONE, autoRideAction(false, true, true, Route.Ride, false))
        assertEquals(AutoRideAction.NONE, autoRideAction(false, true, true, Route.Home, true))
    }

    private val call = ActiveCall("Alex", AlertSource.PHONE, answered = false, sinceMillis = 0)

    @Test
    fun telegramHidesOnRideOnboardingAndAlerts() {
        val alerts = AlertsState(call = call)
        listOf(Route.Ride, Route.Onboarding, Route.Alerts).forEach {
            assertNull(shellChrome(it, LinkState.Connected, alerts, NavState(), Settings()).call, "route $it")
        }
        assertNotNull(shellChrome(Route.Home, LinkState.Connected, alerts, NavState(), Settings()).call)
    }

    @Test
    fun chromeCountsAlertsAndKeepsScreenOnOnlyInRideMode() {
        val alerts = AlertsState(missed = AlertCounter(2, "Rider"), messages = AlertCounter(3, "Alex", AlertSource.WHATSAPP))
        val on = shellChrome(Route.Ride, LinkState.Connected, alerts, NavState(active = true), Settings(keepScreenOn = true))
        assertEquals(5, on.alertBadge)
        assertTrue(on.navigating)
        assertTrue(on.keepScreenOn)
        // Only while Ride mode is open, with or without the bike, and never with the switch off.
        assertTrue(shellChrome(Route.Ride, LinkState.Idle, alerts, NavState(), Settings(keepScreenOn = true)).keepScreenOn)
        assertFalse(shellChrome(Route.Home, LinkState.Connected, alerts, NavState(), Settings(keepScreenOn = true)).keepScreenOn)
        assertFalse(shellChrome(Route.Ride, LinkState.Connected, alerts, NavState(), Settings(keepScreenOn = false)).keepScreenOn)
    }

    @Test
    fun callBannerSaysWhetherTheClusterShowsIt() {
        assertEquals("Ringing · shown on the cluster", callBanner(call, LinkState.Connected, Settings()).status)
        assertEquals("Ringing · cluster alert off", callBanner(call, LinkState.Connected, Settings(alertCalls = false)).status)
        assertEquals("Ringing · cluster alert off", callBanner(call, LinkState.Idle, Settings()).status)
        val wa = call.copy(source = AlertSource.WHATSAPP)
        assertEquals("Ringing · cluster alert off", callBanner(wa, LinkState.Connected, Settings(alertWhatsAppCalls = false)).status)
        val answered = callBanner(call.copy(answered = true), LinkState.Connected, Settings())
        assertEquals("On call", answered.status)
        assertFalse(answered.ringing)
    }
}
