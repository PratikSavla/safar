package app.safar.ui.navigate

import app.safar.core.RideState
import app.safar.data.Place
import app.safar.data.PlaceKind
import app.safar.data.PlaceSource
import app.safar.ui.testing.FakePlaces
import app.safar.ui.testing.FakeRide
import app.safar.ui.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaceViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val ride = FakeRide()
    private val existing = Place(7, "Work", "NH 48", "NH 48", PlaceKind.WORK, PlaceSource.TYPED, 3)

    @Test
    fun sharedTextBecomesAMapsDraftAndSaves() = runTest {
        val places = FakePlaces()
        val vm = PlaceViewModel(null, "Lake View Point\nMain Rd, Sample Town\nhttps://maps.app.goo.gl/AbC", places, ride)
        backgroundScope.launch(main.dispatcher) { vm.state.collect {} }
        val draft = vm.state.value.draft
        assertEquals(PlaceSource.MAPS, draft?.source)
        assertEquals("Lake View Point, Main Rd, Sample Town", draft?.query)
        vm.save()
        assertIs<PlaceEvent.Done>(vm.messages.first())
        assertEquals("Lake View Point", places.places.value.single().label)
    }

    @Test
    fun typedDraftNeedsAnAddressAndAName() = runTest {
        val places = FakePlaces()
        val vm = PlaceViewModel(null, null, places, ride)
        backgroundScope.launch(main.dispatcher) { vm.state.collect {} }
        vm.chooseTyped()
        vm.save()
        assertEquals("Add an address first", vm.messages.first().message)
        vm.setQuery("Main Rd, Sample Town")
        vm.save()
        assertEquals("Give it a name", vm.messages.first().message)
        vm.setLabel("Home")
        vm.setKind(PlaceKind.HOME)
        vm.save()
        assertIs<PlaceEvent.Done>(vm.messages.first())
        val saved = places.places.value.single()
        assertEquals("Main Rd, Sample Town", saved.detail)
        assertEquals(PlaceKind.HOME, saved.kind)
    }

    @Test
    fun movingLocksTyping() = runTest {
        val vm = PlaceViewModel(null, null, FakePlaces(), ride)
        backgroundScope.launch(main.dispatcher) { vm.state.collect {} }
        ride.state.value = RideState(speedKmh = 42.4f, moving = true)
        assertEquals("Locked while moving (42 km/h). Stop to type.", vm.state.value.lockReason)
        vm.chooseTyped()
        assertNull(vm.state.value.draft)
        vm.useLocation(12.5, 77.25, 8.4f)
        vm.setLabel("Home")
        assertEquals("", vm.state.value.draft?.label)
        assertEquals("Your location · within 8 m", vm.state.value.draft?.detail)
    }

    @Test
    fun editLoadsUpdatesAndDeletesAfterConfirm() = runTest {
        val places = FakePlaces(listOf(existing))
        val vm = PlaceViewModel(7, null, places, ride)
        backgroundScope.launch(main.dispatcher) { vm.state.collect {} }
        assertTrue(vm.state.value.editing)
        assertEquals("Work", vm.state.value.draft?.label)
        vm.setLabel("Office")
        vm.save()
        vm.messages.first()
        assertEquals("Office", places.places.value.single().label)
        assertEquals(3, places.places.value.single().sortOrder)
        vm.delete()
        assertTrue(vm.state.value.confirmDelete)
        assertEquals(1, places.places.value.size)
        vm.delete()
        assertEquals("Place deleted", vm.messages.first().message)
        assertTrue(places.places.value.isEmpty())
    }

    @Test
    fun missingPlaceClosesThePage() = runTest {
        val vm = PlaceViewModel(99, null, FakePlaces(), ride)
        assertIs<PlaceEvent.Done>(vm.messages.first())
    }

    @Test
    fun validationAndStampsArePure() {
        assertNull(validateDraft(typedDraft().copy(query = "NH 48", label = "Fuel")))
        assertEquals("Typed address" to SourceTone.INK, PlaceSource.TYPED.stamp())
        assertNull(draftFromShare("   \n  "))
        assertEquals("12.500000,77.250000", draftFromLocation(12.5, 77.25, 5f).query)
    }
}
