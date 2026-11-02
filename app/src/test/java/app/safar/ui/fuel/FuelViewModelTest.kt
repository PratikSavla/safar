package app.safar.ui.fuel

import app.safar.core.BikeState
import app.safar.core.LinkState
import app.safar.ui.testReading
import app.safar.ui.testSaved
import app.safar.ui.testing.FakeFuelUps
import app.safar.ui.testing.FakeSettings
import app.safar.ui.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.ZoneOffset
import kotlin.test.assertEquals

/** The fuel page follows fuel readings, not the link's frame counter or MTU. */
@OptIn(ExperimentalCoroutinesApi::class)
class FuelViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val linked = BikeState(link = LinkState.Connected, saved = testSaved, cluster = testReading(at = 1_000, bars = 4), framesReceived = 1)
    private val bike = MutableStateFlow(linked)
    private val vm by lazy { FuelViewModel(FakeFuelUps(), FakeSettings(), bike, now = { 10_000L }, zone = ZoneOffset.UTC) }

    @Test
    fun onlyAFuelReadingReEmits() = runTest {
        val seen = mutableListOf<FuelUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.toList(seen) }
        runCurrent()
        val count = seen.size
        bike.value = linked.copy(framesReceived = 9, mtu = 65)
        runCurrent()
        assertEquals(count, seen.size)
        bike.value = linked.copy(cluster = testReading(at = 6_000, bars = 3))
        runCurrent()
        assertEquals(count + 1, seen.size)
        assertEquals(3, seen.last().reading.bars)
    }
}
