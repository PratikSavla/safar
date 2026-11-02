package app.safar.ui.navigate

import app.safar.core.BikeState
import app.safar.core.LinkState
import app.safar.core.NavSnapshot
import app.safar.core.NavState
import app.safar.core.NavStatusCode
import app.safar.data.Place
import app.safar.data.PlaceKind
import app.safar.data.PlaceSource
import app.safar.ui.testing.FakeBike
import app.safar.ui.testing.FakeNavigation
import app.safar.ui.testing.FakeParked
import app.safar.ui.testing.FakePlaces
import app.safar.ui.testing.MainDispatcherRule
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavigateViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val home = Place(1, "Home", "Main Rd", "Main Rd, Sample Town", PlaceKind.HOME, PlaceSource.MAPS, 0)
    private val work = Place(2, "Work", "NH 48", "NH 48", PlaceKind.WORK, PlaceSource.TYPED, 1)
    private val nav = FakeNavigation()
    private val places = FakePlaces(listOf(home, work))
    private val bike = FakeBike(BikeState(link = LinkState.Connected))

    private fun vm() = NavigateViewModel(nav, places, FakeParked(), bike)

    @Test
    fun startOpensMapsWithTheSelectedPlace() = runTest {
        val vm = vm()
        backgroundScope.launch(main.dispatcher) { vm.state.collect {} }
        vm.pick(placeKey(2))
        vm.start()
        assertEquals(listOf("Work" to "NH 48"), nav.opened)
        assertEquals(RoutePhase.WAITING_FIRST_UPDATE, vm.state.value.phase)
    }

    @Test
    fun activeNavigationBuildsTheMirrorAndEnds() = runTest {
        val vm = vm()
        backgroundScope.launch(main.dispatcher) { vm.state.collect {} }
        val snap = NavSnapshot(8, true, 300, 0L, 2_000, NavStatusCode.RECALCULATING, "Go straight", 1L)
        nav.state.value = NavState(active = true, destinationLabel = "Home", snapshot = snap)
        val s = vm.state.value
        assertEquals(RoutePhase.ACTIVE, s.phase)
        assertEquals(46, s.mirror?.clusterIcon)
        assertTrue(s.bikeConnected)
        vm.end()
        assertEquals(1, nav.ended)
        assertEquals(RoutePhase.PICK, vm.state.value.phase)
    }

    @Test
    fun searchHandOffEndsWhenMapsStartsNavigating() = runTest {
        val vm = vm()
        backgroundScope.launch(main.dispatcher) { vm.state.collect {} }
        vm.searchOpened(true)
        assertEquals(RoutePhase.AWAITING_MAPS, vm.state.value.phase)
        nav.state.value = NavState(active = true)
        nav.state.value = NavState()
        assertEquals(RoutePhase.PICK, vm.state.value.phase)
    }

    @Test
    fun editingMovesPlaces() = runTest {
        val vm = vm()
        backgroundScope.launch(main.dispatcher) { vm.state.collect {} }
        vm.toggleEditing()
        assertTrue(vm.state.value.editing)
        vm.move(1, 1)
        assertEquals(listOf(1L to 1), places.moves)
        vm.stopEditing()
        assertFalse(vm.state.value.editing)
    }
}
