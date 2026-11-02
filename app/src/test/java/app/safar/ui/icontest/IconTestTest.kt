package app.safar.ui.icontest

import app.safar.core.BikeState
import app.safar.core.LinkState
import app.safar.ui.testing.FakeBike
import app.safar.ui.testing.MainDispatcherRule
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IconTestTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun gridHas45NamedCodes() {
        assertEquals(45, ICON_CELLS.size)
        assertEquals(IconCell(1, "Turn left"), ICON_CELLS.first())
        assertEquals("Roundabout (generic)", ICON_CELLS.last().name)
    }

    @Test
    fun stepWraps() {
        assertEquals(2, stepCode(1, 1))
        assertEquals(1, stepCode(45, 1))
        assertEquals(45, stepCode(1, -1))
    }

    @Test
    fun sendNextAndPreviousReachTheBike() = runTest {
        val bike = FakeBike(BikeState(link = LinkState.Connected))
        val vm = IconTestViewModel(bike) { 5_000L }
        backgroundScope.launch(main.dispatcher) { vm.state.collect {} }
        assertNull(vm.state.value.lastSentCode)
        vm.send(17)
        vm.next()
        vm.previous()
        vm.previous()
        vm.send(99)
        assertEquals(listOf(17, 18, 17, 16, 45), bike.iconTests)
        assertEquals(45, vm.state.value.current)
        assertEquals(5_000L, vm.state.value.lastSentAtMillis)
        assertTrue(vm.state.value.connected)
    }
}
