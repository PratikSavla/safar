package app.safar.e2e

import app.safar.phone.MapsFields
import app.safar.protocol.ChecksumMode
import app.safar.protocol.NavStatus
import app.safar.protocol.NavigationData
import app.safar.protocol.STATUS_ICON_CODE
import app.safar.protocol.Timing
import app.safar.protocol.encodeNavigation
import app.safar.sim.BikeRig
import app.safar.sim.MapsFeed
import app.safar.sim.TxFrames
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalTime
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Google Maps notification extras (the confirmed layout of docs 11.2, place names replaced) through the phone
 * layer's parsing into the session, down to the '1' frame bytes the bike receives.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MapsToClusterTest {
    private val first = MapsFields(
        title = "Turn right at Main Rd onto NH 48",
        subText = "Arrive 11:56 am",
        progress = 162,
        progressMax = 1607,
    )
    private val next = MapsFields(
        title = "200 m · Turn right toward NH 48",
        shortCriticalText = "200 m",
        subText = "Arrive 11:56 am",
        progress = 338,
        progressMax = 1723,
    )
    private val depart = MapsFields(
        title = "Head southwest on Main Rd",
        shortCriticalText = "",
        subText = "Arrive 12:55 pm",
        progress = 0,
        progressMax = 23_708,
    )
    private val period = Timing.NAVIGATION_PERIOD_MS

    private fun TestScope.mapsRig(): Pair<BikeRig, MapsFeed> {
        val rig = BikeRig(this)
        val maps = MapsFeed(rig.phone.state, backgroundScope, rig.now)
        rig.navSource = maps
        rig.pairFromScan()
        rig.advance(3_000)
        return rig to maps
    }

    private fun BikeRig.lastNavBytes(): ByteArray = writes('1').last().bytes

    private fun expected(icon: Int, turnM: Int, eta: LocalTime, leftM: Int, status: NavStatus = NavStatus.NORMAL) =
        encodeNavigation(NavigationData(icon, turnM, eta, leftM, status, active = true), ChecksumMode.SUM)

    @Test
    fun departureGivesHeadingIconRemainingDistanceAndEta() = runTest {
        val (rig, maps) = mapsRig()
        maps.post(depart)
        rig.advance(period)
        assertContentEquals(expected(17, 0, LocalTime.of(12, 55), 23_708), rig.lastNavBytes())
        assertEquals(TxFrames.Nav(17, "0000M", "23.7K", "1255PM", '1', '1'), rig.frames.nav(rig.lastNavBytes()))
    }

    @Test
    fun firstAndNextNotificationsBecomeTurnFrames() = runTest {
        val (rig, maps) = mapsRig()
        maps.post(first)
        rig.advance(period)
        assertContentEquals(expected(4, 0, LocalTime.of(11, 56), 1_445), rig.lastNavBytes())

        maps.post(next)
        rig.advance(period)
        assertContentEquals(expected(4, 200, LocalTime.of(11, 56), 1_385), rig.lastNavBytes())
        assertEquals(TxFrames.Nav(4, "0200M", "01.4K", "1156AM", '1', '1'), rig.frames.nav(rig.lastNavBytes()))
    }

    @Test
    fun fieldsThatDoNotParseKeepTheirPreviousValues() = runTest {
        val (rig, maps) = mapsRig()
        maps.post(next)
        rig.advance(period)
        maps.post(MapsFields(title = "Odd text", subText = ""), matchedIcon = 31)
        rig.advance(period)
        assertContentEquals(expected(31, 200, LocalTime.of(11, 56), 1_385), rig.lastNavBytes(), "icon match wins")

        maps.post(MapsFields(title = "", shortCriticalText = "soon", subText = "Arrive later"))
        rig.advance(period)
        assertContentEquals(expected(31, 200, LocalTime.of(11, 56), 1_385), rig.lastNavBytes(), "nothing parsed")
    }

    @Test
    fun reroutingIsSentOnceWithTheStatusIcon() = runTest {
        val (rig, maps) = mapsRig()
        maps.post(next)
        rig.advance(period)
        val before = rig.writes('1').size
        maps.post(MapsFields(title = "Rerouting…", subText = ""))
        rig.advance(3 * period)

        val frames = rig.writes('1').drop(before).map { rig.frames.nav(it.bytes) }
        assertEquals(listOf('2', '1', '1'), frames.map { it.status })
        assertEquals(listOf(STATUS_ICON_CODE, 4, 4), frames.map { it.icon })
    }

    @Test
    fun phoneConditionsOverrideTheRouteStatus() = runTest {
        val (rig, maps) = mapsRig()
        maps.post(next)
        rig.advance(period)
        // GPS off always suppresses the route, even when Maps is active.
        rig.phone.set { it.copy(gpsOn = false) }
        rig.advance(period)
        assertContentEquals(expected(4, 200, LocalTime.of(11, 56), 1_385, NavStatus.GPS_OFF), rig.lastNavBytes())
        // No internet but Maps is actively delivering turns (e.g. offline maps): keep the turn arrow.
        rig.phone.set { it.copy(gpsOn = true, hasInternet = false) }
        rig.advance(period)
        assertEquals('1', rig.frames.nav(rig.lastNavBytes()).status)
    }

    @Test
    fun removedNotificationSendsOneEndedFrame() = runTest {
        val (rig, maps) = mapsRig()
        maps.post(next)
        rig.advance(1_000)
        val before = rig.writes('1').size
        maps.remove()
        rig.advance(3_000)
        val after = rig.writes('1').drop(before)
        assertEquals(1, after.size)
        assertEquals(TxFrames.Nav(4, "0200M", "01.4K", "1156AM", '1', '0'), rig.frames.nav(after.single().bytes))
    }
}
