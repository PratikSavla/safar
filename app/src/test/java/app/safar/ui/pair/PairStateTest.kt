package app.safar.ui.pair

import app.safar.core.BikeCatalog
import app.safar.core.BikeState
import app.safar.core.LinkState
import app.safar.core.ScannedBike
import app.safar.ui.FakeBike
import app.safar.ui.FakeSettings
import app.safar.ui.TEST_ADDRESS
import app.safar.ui.TEST_BIKE
import app.safar.ui.components.rssiBars
import app.safar.ui.testSaved
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PairStateTest {
    @After
    fun tearDown() = Dispatchers.resetMain()

    private val found = ScannedBike(TEST_BIKE, TEST_ADDRESS, -62, BikeCatalog.forBleName(TEST_BIKE))

    @Test
    fun accessProblemsComeFirst() {
        assertEquals(PairPhase.NEEDS_PERMISSION, pairPhase(BtAccess.NEEDS_PERMISSION, LinkState.Connected, false, 1, null, null))
        assertEquals(PairPhase.BLUETOOTH_OFF, pairPhase(BtAccess.BLUETOOTH_OFF, LinkState.Idle, false, 0, null, null))
        assertEquals(PairPhase.BLUETOOTH_OFF, pairPhase(BtAccess.READY, LinkState.BluetoothOff, false, 0, null, null))
        assertEquals(PairPhase.UNSUPPORTED, pairPhase(BtAccess.UNSUPPORTED, LinkState.Idle, false, 0, null, null))
    }

    @Test
    fun scanConnectSuccess() {
        assertEquals(PairPhase.LISTENING, pairPhase(BtAccess.READY, LinkState.Searching, true, 0, null, null))
        assertEquals(PairPhase.RESULTS, pairPhase(BtAccess.READY, LinkState.Searching, true, 2, null, null))
        assertEquals(PairPhase.NOTHING, pairPhase(BtAccess.READY, LinkState.Idle, false, 0, null, null))
        assertEquals(PairPhase.CONNECTING, pairPhase(BtAccess.READY, LinkState.Connecting(TEST_BIKE), false, 1, TEST_ADDRESS, null))
        assertEquals(PairPhase.SUCCESS, pairPhase(BtAccess.READY, LinkState.Connected, false, 1, TEST_ADDRESS, TEST_ADDRESS))
        assertEquals(PairPhase.ALREADY_LINKED, pairPhase(BtAccess.READY, LinkState.Connected, false, 0, null, null))
    }

    @Test
    fun pairUiSortsBySignalAndFlagsAFailedPick() {
        val weak = found.copy(address = "00:11:22:33:44:66", rssi = -90)
        val ui = pairUi(PairInputs(BikeState(scan = listOf(weak, found)), "alex ", TEST_ADDRESS, attempted = true), BtAccess.READY)
        assertEquals(listOf(-62, -90), ui.results.map { it.rssi })
        assertTrue(ui.failed)
        assertEquals("V-Strom SX", ui.model?.name)
        assertEquals("alex", ui.riderName)
        assertEquals("Tap your bike to connect.", ui.scanLine)
        assertEquals("Looking for your bike…", ui.copy(scanning = true).scanLine)
        val ok = pairUi(PairInputs(BikeState(link = LinkState.Connected, saved = testSaved), "Alex", TEST_ADDRESS), BtAccess.READY)
        assertFalse(ok.failed)
        assertEquals(TEST_BIKE, ok.bikeName)
    }

    @Test
    fun proximityTurnsSignalBarsIntoWords() {
        assertEquals("Very close", proximity(4))
        assertEquals("Close by", proximity(3))
        assertEquals("Nearby", proximity(2))
        assertEquals("Far away", proximity(1))
        assertEquals("Far away", proximity(0))
    }

    @Test
    fun connectedToAnotherBikeIsNotSuccessForThePick() {
        val other = "00:11:22:33:44:66"
        assertEquals(PairPhase.CONNECTING, pairPhase(BtAccess.READY, LinkState.Connected, false, 2, other, TEST_ADDRESS))
        assertEquals(PairPhase.CONNECTING, pairPhase(BtAccess.READY, LinkState.Connected, false, 2, other, null))
        val picked = found.copy(name = "SBM01Z9Y8X7W", address = other)
        val ui = pairUi(PairInputs(BikeState(link = LinkState.Connected, scan = listOf(found, picked), saved = testSaved), "Alex", other), BtAccess.READY)
        assertEquals(PairPhase.CONNECTING, ui.phase)
        assertEquals("SBM01Z9Y8X7W", ui.bikeName)
        assertEquals(other, ui.address)
    }

    @Test
    fun theSameBikeAtANewAddressIsSuccess() {
        val moved = "00:11:22:33:44:77"
        val match = PickMatch(picked = moved, linked = TEST_ADDRESS, pickedName = TEST_BIKE, linkedName = TEST_BIKE)
        assertEquals(PairPhase.SUCCESS, pairPhase(BtAccess.READY, LinkState.Connected, false, 1, match))
        val ui = pairUi(PairInputs(BikeState(link = LinkState.Connected, saved = testSaved), "Alex", moved, pickedName = TEST_BIKE), BtAccess.READY)
        assertEquals(PairPhase.SUCCESS, ui.phase)
        assertEquals("V-Strom SX", ui.model?.name)
    }

    @Test
    fun aPickThatTimesOutGoesBackToTheListAndSaysSo() {
        val other = "00:11:22:33:44:66"
        val stuck = BikeState(link = LinkState.Connected, scan = listOf(found), saved = testSaved)
        val ui = pairUi(PairInputs(stuck, "Alex", other, attempted = true, pickedName = "SBM01Z9Y8X7W", timedOut = true), BtAccess.READY)
        assertEquals(PairPhase.RESULTS, ui.phase)
        assertTrue(ui.failed)
        val connecting = BikeState(link = LinkState.Connecting(TEST_BIKE))
        assertEquals(PairPhase.NOTHING, pairUi(PairInputs(connecting, "Alex", TEST_ADDRESS, timedOut = true), BtAccess.READY).phase)
    }

    @Test
    fun developerStepsShowTheNegotiatedMtu() {
        assertEquals("MTU · notify on", developerSteps(null)[2])
        assertEquals("MTU 517 · notify on", developerSteps(517)[2])
    }

    @Test
    fun aPickFailsOnlyAfterItsConnectRan() {
        val idle = BikeState(scan = listOf(found))
        assertFalse(pairUi(PairInputs(idle, "Alex", TEST_ADDRESS, attempted = false), BtAccess.READY).failed)
        assertTrue(pairUi(PairInputs(idle, "Alex", TEST_ADDRESS, attempted = true), BtAccess.READY).failed)
        val retrying = idle.copy(link = LinkState.Reconnecting(1, 2_000L))
        assertTrue(pairUi(PairInputs(retrying, "Alex", TEST_ADDRESS, attempted = true), BtAccess.READY).failed)
        assertTrue(pickAttempted(samePick = false, wasAttempted = false, link = LinkState.Connecting(TEST_BIKE)))
        assertTrue(pickAttempted(samePick = true, wasAttempted = true, link = LinkState.Idle))
        assertFalse(pickAttempted(samePick = false, wasAttempted = true, link = LinkState.Idle))
    }

    @Test
    fun rssiMapsToFourBars() {
        assertEquals(4, rssiBars(-50))
        assertEquals(3, rssiBars(-62))
        assertEquals(2, rssiBars(-80))
        assertEquals(1, rssiBars(-95))
    }

    @Test
    fun viewModelScansOnceAndStopsScanningOnPick() {
        val bike = FakeBike()
        val vm = PairViewModel(bike, FakeSettings())
        vm.autoScan()
        vm.autoScan()
        assertEquals(listOf("startScan"), bike.calls)
        vm.pick(TEST_ADDRESS)
        assertEquals(listOf("startScan", "stopScan", "connect:$TEST_ADDRESS"), bike.calls)
    }

    @Test
    fun viewModelMarksThePickAttemptedOnlyAfterConnecting() = runTest(UnconfinedTestDispatcher()) {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val bike = FakeBike(BikeState(scan = listOf(found)))
        val vm = PairViewModel(bike, FakeSettings())
        backgroundScope.launch { vm.inputs.collect {} }
        vm.pick(TEST_ADDRESS)
        assertEquals(false, vm.inputs.value?.attempted)
        bike.state.value = bike.state.value.copy(link = LinkState.Connecting(TEST_BIKE))
        bike.state.value = bike.state.value.copy(link = LinkState.Idle)
        assertEquals(true, vm.inputs.value?.attempted)
        vm.pick(TEST_ADDRESS)
        assertEquals(false, vm.inputs.value?.attempted)
    }

    @Test
    fun viewModelTimesOutAPickThatNeverConnects() = runTest(UnconfinedTestDispatcher()) {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val bike = FakeBike(BikeState(scan = listOf(found)))
        val vm = PairViewModel(bike, FakeSettings(), pickTimeoutMs = 1_000L)
        backgroundScope.launch { vm.inputs.collect {} }
        vm.pick(TEST_ADDRESS)
        bike.state.value = bike.state.value.copy(link = LinkState.Connecting(TEST_BIKE))
        assertEquals(TEST_BIKE, vm.inputs.value?.pickedName)
        assertEquals(false, vm.inputs.value?.timedOut)
        advanceTimeBy(1_001L)
        assertEquals(true, vm.inputs.value?.timedOut)
        vm.pick(TEST_ADDRESS)
        assertEquals(false, vm.inputs.value?.timedOut, "a new tap starts a new wait")
    }
}
