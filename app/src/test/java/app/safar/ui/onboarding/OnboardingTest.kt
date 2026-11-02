package app.safar.ui.onboarding

import android.Manifest
import app.safar.core.RideState
import app.safar.data.Settings
import app.safar.ui.FakeRide
import app.safar.ui.FakeSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun clusterNameIsCapsAsciiAndAtMost19() {
        assertEquals("ALEX", clusterName("  alex "))
        assertEquals("RIDER ONE", clusterName("Rider · one"))
        assertEquals(19, clusterName("Alexander the Rider of NH 48").length)
        assertEquals("", clusterName("🏍️"))
    }

    @Test
    fun permissionsPerSdk() {
        assertEquals(listOf(Manifest.permission.ACCESS_FINE_LOCATION), SafarPermissions.runtime(PermId.NEARBY, 30))
        assertEquals(listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT), SafarPermissions.runtime(PermId.NEARBY, 31))
        assertTrue(SafarPermissions.runtime(PermId.NOTIFICATIONS, 32).isEmpty())
        assertEquals(listOf(Manifest.permission.POST_NOTIFICATIONS), SafarPermissions.runtime(PermId.NOTIFICATIONS, 33))
        assertEquals(3, SafarPermissions.runtime(PermId.PHONE, 37).size)
    }

    @Test
    fun statusAndButtonPerItem() {
        assertEquals(PermStatus.ALLOWED, permStatus(granted = true, asked = false, canAskAgain = false))
        assertEquals(PermStatus.NOT_ASKED, permStatus(false, asked = false, canAskAgain = false))
        assertEquals(PermStatus.DENIED, permStatus(false, asked = true, canAskAgain = true))
        assertEquals(PermStatus.BLOCKED, permStatus(false, asked = true, canAskAgain = false))
        assertNull(permAction(PermId.LOCATION, PermStatus.ALLOWED))
        assertEquals("Allow", permAction(PermId.LOCATION, PermStatus.NOT_ASKED))
        assertEquals("Retry", permAction(PermId.PHONE, PermStatus.DENIED))
        assertEquals("Open", permAction(PermId.NOTIFICATION_ACCESS, PermStatus.DENIED))
        assertEquals("Settings", permAction(PermId.NEARBY, PermStatus.BLOCKED))
        assertEquals("denied", permLabel(PermStatus.DENIED))
    }

    @Test
    fun finishingSavesNameAndOnboarded() {
        val settings = FakeSettings(Settings(riderName = "Rider"))
        val vm = OnboardingViewModel(settings, FakeRide(), TestScope(dispatcher))
        assertEquals("Rider", vm.ui.value.name)
        var done = 0
        vm.next { done++ }
        vm.next { done++ }
        assertEquals(2, vm.ui.value.step)
        assertEquals("Find my bike", vm.ui.value.nextLabel)
        vm.setName("Alex")
        assertEquals("ALEX", vm.ui.value.cleaned)
        vm.next { done++ }
        assertEquals(1, done)
        assertEquals(Settings(riderName = "Alex", onboarded = true), settings.settings.value)
    }

    @Test
    fun anEmptyNameKeepsTheOldOne() {
        val settings = FakeSettings(Settings(riderName = "Rider"))
        val vm = OnboardingViewModel(settings, FakeRide(), TestScope(dispatcher))
        repeat(2) { vm.next {} }
        vm.setName("!!")
        vm.next {}
        assertEquals("Rider", settings.settings.value.riderName)
        assertTrue(settings.settings.value.onboarded)
    }

    @Test
    fun typingIsLockedWhileMoving() {
        val ride = FakeRide(RideState(speedKmh = 30f, moving = true))
        val vm = OnboardingViewModel(FakeSettings(), ride, TestScope(dispatcher))
        assertTrue(vm.ui.value.locked)
        vm.setName("Alex")
        assertEquals("", vm.ui.value.name)
        ride.state.value = RideState()
        assertFalse(vm.ui.value.locked)
        assertFalse(vm.prev())
    }
}
