package app.safar.ui.shell

import app.safar.ui.nav.Route
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NavStackTest {
    private fun routes(s: NavStack) = s.entries.map { it.route }

    @Test
    fun goPushesAndBackPops() {
        val s = NavStack().resetTo(Route.Home).go(Route.Fuel).go(Route.FuelUp())
        assertEquals(listOf(Route.Home, Route.Fuel, Route.FuelUp()), routes(s))
        val b = s.back()
        assertEquals(listOf(Route.Home, Route.Fuel), routes(b))
        assertEquals(NavDirection.BACK, b.direction)
    }

    @Test
    fun goingHomeClearsTheStack() {
        val s = NavStack().resetTo(Route.Home).go(Route.Trips).go(Route.Trip(3)).go(Route.Home)
        assertEquals(listOf(Route.Home), routes(s))
        assertFalse(s.canGoBack)
    }

    @Test
    fun goingToTheTopRouteDoesNothing() {
        val s = NavStack().resetTo(Route.Home).go(Route.Alerts)
        assertSame(s, s.go(Route.Alerts))
    }

    @Test
    fun backAtHomeOrOnboardingDoesNothing() {
        val home = NavStack().resetTo(Route.Home)
        assertSame(home, home.back())
        val ob = NavStack().resetTo(Route.Onboarding)
        assertFalse(ob.canGoBack)
        assertSame(ob, ob.back())
    }

    @Test
    fun backAtAnotherRootLandsOnHome() {
        val pair = NavStack().resetTo(Route.Onboarding).resetTo(Route.Pair)
        assertTrue(pair.canGoBack)
        assertEquals(listOf(Route.Home), routes(pair.back()))
    }

    @Test
    fun entriesKeepDistinctIdsAndRemovedInListsDroppedOnes() {
        val a = NavStack().resetTo(Route.Home).go(Route.Fuel).go(Route.Service)
        assertEquals(listOf(1L, 2L, 3L), a.entries.map { it.id })
        val b = a.back().back()
        assertEquals(listOf(2L, 3L), a.removedIn(b))
        val c = b.go(Route.Fuel)
        assertEquals(4L, c.top?.id)
    }

    @Test
    fun encodeDecodeRebuildsTheStack() {
        val s = NavStack().resetTo(Route.Home).go(Route.Place(sharedText = "Main Rd | NH 48")).go(Route.Trip(7))
        val back = NavStack.decode(s.encode())
        assertEquals(routes(s), routes(back))
        assertEquals(4L, back.nextId)
    }

    @Test
    fun decodeSkipsUnknownEntries() {
        val s = NavStack.decode(listOf("home", "warp-drive", "fuel"))
        assertEquals(listOf(Route.Home, Route.Fuel), routes(s))
        assertTrue(NavStack.decode(null).entries.isEmpty())
        assertNull(NavStack.decode(emptyList()).top)
    }
}
