package app.safar.e2e

import app.safar.core.NavSnapshot
import app.safar.core.NavStatusCode
import app.safar.core.PhoneSnapshot
import app.safar.protocol.ChecksumMode
import app.safar.protocol.NavStatus
import app.safar.protocol.NavigationData
import app.safar.protocol.STATUS_ICON_CODE
import app.safar.protocol.Timing
import app.safar.protocol.encodeNavigation
import app.safar.sim.BikeRig
import app.safar.sim.CLOCK_BASE
import app.safar.sim.TxFrames
import app.safar.sim.fakes.FakePhoneStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalTime
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Navigation frames '1' on the session timer (docs 4.2-4.5). */
@OptIn(ExperimentalCoroutinesApi::class)
class NavigationTest {
    private val period = Timing.NAVIGATION_PERIOD_MS

    /** Turn right in 250 m, 4.3 km left, arrival 14:20. */
    private fun snapshot(rig: BikeRig, status: NavStatusCode = NavStatusCode.NORMAL, icon: Int = 4) = NavSnapshot(
        iconCode = icon,
        iconMatched = true,
        distanceToTurnM = 250,
        etaMillis = CLOCK_BASE + 15 * 60_000L,
        remainingM = 4_300,
        status = status,
        instruction = "Turn right onto NH 48",
        receivedAtMillis = rig.now(),
    )

    /** Connected rig, past the rider repeats; returns it with the session start time. */
    private fun TestScope.navRig(): Pair<BikeRig, Long> {
        val rig = BikeRig(this)
        val t0 = rig.pairFromScan()
        rig.advance(3_000)
        return rig to t0
    }

    private fun BikeRig.lastNav(): TxFrames.Nav = frames.nav(writes('1').last().bytes)

    @Test
    fun noFrameBeforeTheFirstSnapshotThenOneEveryPeriod() = runTest {
        val (rig, t0) = navRig()
        rig.navigation.waitForMaps("Main Rd")
        rig.advance(2_000)
        assertEquals(emptyList(), rig.writes('1'), "no navigation frame before the first parsed update")

        val shownAt = rig.time
        rig.navigation.show(snapshot(rig))
        rig.advance(2_000)
        val nav = rig.writes('1')
        // The tick at shownAt has already run, so the first frame goes on the next tick.
        val firstTick = t0 + ((shownAt - t0) / period + 1) * period
        assertEquals(List(nav.size) { firstTick + it * period }, nav.map { it.atMillis })
        assertEquals(8, nav.size)
        val expected = NavigationData(4, 250, LocalTime.of(14, 20), 4_300, NavStatus.NORMAL, active = true)
        nav.forEach { assertContentEquals(encodeNavigation(expected, ChecksumMode.SUM), it.bytes) }
        assertEquals(TxFrames.Nav(4, "0250M", "04.3K", "0220PM", '1', '1'), rig.lastNav())
    }

    @Test
    fun statusPriorityAndIcon46ForStatusesWithoutAnIcon() = runTest {
        val (rig, _) = navRig()
        val good = FakePhoneStatus.GOOD_PHONE
        val cases = listOf(
            Triple(good.copy(gpsOn = false, signalBars = 0, hasInternet = false), NavStatusCode.RECALCULATING, '4'),
            // No signal + no internet: Maps is active (snapshot delivered), so the cluster keeps the turn arrow.
            Triple(good.copy(signalBars = 0, hasInternet = false), NavStatusCode.RECALCULATING, '2'),
            Triple(good.copy(hasInternet = false), NavStatusCode.RECALCULATING, '2'),
            Triple(good, NavStatusCode.RECALCULATING, '2'),
            Triple(good, NavStatusCode.ARRIVED, '5'),
            Triple(good, NavStatusCode.VIA_POINT, '3'),
            Triple(good, NavStatusCode.NORMAL, '1'),
            Triple(good, NavStatusCode.GPS_OFF, '4'),
            // NO_NETWORK / NO_INTERNET codes from Maps itself: Maps is active, bypass them → '1'.
            Triple(good, NavStatusCode.NO_NETWORK, '1'),
            Triple(good, NavStatusCode.NO_INTERNET, '1'),
        )
        for ((phone, code, status) in cases) {
            rig.phone.set { phone }
            rig.navigation.show(snapshot(rig, code))
            rig.advance(period)
            val frame = rig.lastNav()
            assertEquals(status, frame.status, "$code with $phone")
            val showsIcon = NavStatus.fromCode(status)?.showsIcon == true
            assertEquals(if (showsIcon) 4 else STATUS_ICON_CODE, frame.icon, "icon for status $status")
        }
    }

    @Test
    fun recalculatingIsSentForOneFramePerUpdate() = runTest {
        val (rig, _) = navRig()
        rig.navigation.show(snapshot(rig, NavStatusCode.RECALCULATING))
        rig.advance(4 * period)
        assertEquals(listOf('2', '1', '1', '1'), rig.writes('1').map { rig.frames.nav(it.bytes).status })
        assertEquals(listOf(STATUS_ICON_CODE, 4, 4, 4), rig.writes('1').map { rig.frames.nav(it.bytes).icon })

        rig.advance(10)
        rig.navigation.show(snapshot(rig, NavStatusCode.RECALCULATING))
        rig.advance(2 * period)
        assertEquals(listOf('2', '1'), rig.writes('1').takeLast(2).map { rig.frames.nav(it.bytes).status })
    }

    @Test
    fun outOfRangeIconKeepsThePreviousIcon() = runTest {
        val (rig, _) = navRig()
        rig.navigation.show(snapshot(rig, icon = 23))
        rig.advance(period)
        rig.navigation.show(snapshot(rig, icon = 0))
        rig.advance(period)
        assertEquals(23, rig.lastNav().icon)
    }

    @Test
    fun endingNavigationSendsExactlyOneEndedFrame() = runTest {
        val (rig, _) = navRig()
        rig.navigation.show(snapshot(rig))
        rig.advance(1_000)
        val before = rig.writes('1').size
        rig.navigation.end()
        rig.advance(5_000)

        val after = rig.writes('1').drop(before)
        assertEquals(1, after.size, "one ended frame, then the timer sends nothing")
        assertEquals(TxFrames.Nav(4, "0250M", "04.3K", "0220PM", '1', '0'), rig.frames.nav(after.single().bytes))
        assertEquals(1, rig.recording.named("Navigation ended frame sent").size)
    }

    @Test
    fun navigationStopsWhenTheLinkDropsAndResumesOnReconnect() = runTest {
        val (rig, _) = navRig()
        rig.navigation.show(snapshot(rig))
        rig.advance(1_000)
        val dropAt = rig.time
        rig.bike.powerOff()
        rig.advance(10_000)
        assertEquals(emptyList(), rig.txAfter(dropAt), "no write attempted after the drop")

        rig.bike.powerOn()
        val back = rig.awaitConnected(limitMs = 60_000)
        rig.advance(1_000)
        val resumed = rig.writes('1', from = back)
        assertEquals(4, resumed.size, "the active route continues on the new link")
        assertEquals('1', rig.frames.nav(resumed.first().bytes).active)
    }

    @Test
    fun noNavigationFramesWhileMapsIsActiveWithoutASnapshot() = runTest {
        val (rig, _) = navRig()
        rig.phone.set { PhoneSnapshot(batteryPercent = 50, signalBars = 2) }
        rig.navigation.waitForMaps("NH 48")
        rig.advance(3_000)
        assertEquals(emptyList(), rig.writes('1'))
    }
}
