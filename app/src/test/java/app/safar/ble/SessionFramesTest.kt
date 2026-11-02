package app.safar.ble

import app.safar.core.BikeCatalog
import app.safar.core.NavSnapshot
import app.safar.core.NavStatusCode
import app.safar.core.PhoneSnapshot
import app.safar.data.Settings
import app.safar.protocol.ChecksumMode
import app.safar.protocol.ClusterData
import app.safar.protocol.NavStatus
import app.safar.protocol.encodeCluster
import app.safar.protocol.fromHex
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionFramesTest {
    private val zone = ZoneOffset.UTC
    private val moto = BikeCatalog.forBleName("SBM01A1B2C3D")
    private val scooter = BikeCatalog.forBleName("SAS01A1B2C3D")
    private val good = PhoneSnapshot(batteryPercent = 80, signalBars = 3, hasInternet = true, gpsOn = true)

    // ---- RX decode -------------------------------------------------------------------------------------

    /** Docs 3.4 real frame layout with synthetic values: neutral 'N' at byte 23, raw 0x1D at byte 25. */
    private fun clusterFrame(mode: ChecksumMode, b23: Int = 'N'.code, fuel: Char = '3'): ByteArray {
        val frame = encodeCluster(ClusterData(0, 462, 236.2, 462.3, fuel - '0'), mode)
        frame[23] = b23.toByte()
        frame[25] = 0x1D
        frame[26] = 0xFF.toByte()
        frame[27] = 0xFF.toByte()
        frame[28] = app.safar.protocol.computeChecksum(frame, mode).toByte()
        return frame
    }

    @Test
    fun decodesClusterFrameIntoReadingWithGear() {
        val r = assertIs<RxResult.Reading>(decodeRx(clusterFrame(ChecksumMode.SUM), ChecksumMode.SUM, false, 42L)).reading
        assertEquals(0, r.speedKmh)
        assertEquals(462, r.odometerKm)
        assertEquals(236.2, r.tripAKm)
        assertEquals(462.3, r.tripBKm)
        assertEquals(3, r.fuelBars)
        assertEquals('N', r.gear)
        assertEquals('N'.code, r.raw23)
        assertEquals(0x1D, r.raw25)
        assertEquals(42L, r.receivedAtMillis)
    }

    @Test
    fun docsRealFrameDecodes() {
        val frame = fromHex("A5 37 30 30 30 30 30 30 34 36 32 30 30 32 33 36 32 30 30 34 36 32 33 4E 33 1D FF FF EB 7F")
        val r = assertIs<RxResult.Reading>(decodeRx(frame, ChecksumMode.SUM, false, 0L)).reading
        assertEquals(462, r.odometerKm)
        assertEquals('N', r.gear)
    }

    @Test
    fun firstFrameHasNoFuelAndNonAlphanumericGearIsNull() {
        val r = assertIs<RxResult.Reading>(
            decodeRx(clusterFrame(ChecksumMode.SUM, b23 = 0xFF), ChecksumMode.SUM, true, 0L),
        ).reading
        assertNull(r.fuelBars)
        assertNull(r.gear)
        assertEquals(0xFF, r.raw23)
        val digit = assertIs<RxResult.Reading>(
            decodeRx(clusterFrame(ChecksumMode.SUM, b23 = '4'.code), ChecksumMode.SUM, false, 0L),
        ).reading
        assertEquals('4', digit.gear)
    }

    @Test
    fun usesTheVehicleChecksumMode() {
        val inverted = clusterFrame(ChecksumMode.INVERTED)
        assertIs<RxResult.Reading>(decodeRx(inverted, ChecksumMode.INVERTED, false, 0L))
        val wrong = assertIs<RxResult.Invalid>(decodeRx(inverted, ChecksumMode.SUM, false, 0L))
        assertTrue(wrong.reason.startsWith("checksum"))
    }

    @Test
    fun invalidFramesAreReportedNotThrown() {
        assertIs<RxResult.Invalid>(decodeRx(ByteArray(29), ChecksumMode.SUM, false, 0L))
        val badDigits = clusterFrame(ChecksumMode.SUM)
        badDigits[3] = 'x'.code.toByte()
        badDigits[28] = app.safar.protocol.computeChecksum(badDigits, ChecksumMode.SUM).toByte()
        val r = assertIs<RxResult.Invalid>(decodeRx(badDigits, ChecksumMode.SUM, false, 0L))
        assertTrue(r.reason.contains("speed"))
        val nav = clusterFrame(ChecksumMode.SUM).also { it[1] = '1'.code.toByte() }
        nav[28] = app.safar.protocol.computeChecksum(nav, ChecksumMode.SUM).toByte()
        assertEquals(RxResult.Other('1'), decodeRx(nav, ChecksumMode.SUM, false, 0L))
    }

    // ---- Navigation --------------------------------------------------------------------------------------

    private fun snap(status: NavStatusCode = NavStatusCode.NORMAL, icon: Int = 4, at: Long = 1L) = NavSnapshot(
        iconCode = icon,
        iconMatched = true,
        distanceToTurnM = 300,
        etaMillis = LocalDateTime.of(2026, 1, 2, 13, 1).toInstant(ZoneOffset.UTC).toEpochMilli(),
        remainingM = 41_600,
        status = status,
        instruction = "Turn right onto Main Rd",
        receivedAtMillis = at,
    )

    @Test
    fun mapsSnapshotToNavigationData() {
        val data = NavFrameMapper(zone).map(snap(), good, active = true)
        assertEquals(4, data.iconCode)
        assertEquals(300, data.distanceToTurnM)
        assertEquals(41_600, data.remainingM)
        assertEquals(LocalTime.of(13, 1), data.eta)
        assertEquals(NavStatus.NORMAL, data.status)
        assertTrue(data.active)
    }

    @Test
    fun statusPriorityFollowsDocs43() {
        val all = NavStatusCode.ARRIVED
        val noGps = good.copy(gpsOn = false, signalBars = 0, hasInternet = false)
        assertEquals(NavStatus.GPS_OFF, navStatusFor(all, noGps, false))
        assertEquals(NavStatus.NO_NETWORK_SIGNAL, navStatusFor(all, noGps.copy(gpsOn = true), false))
        assertEquals(NavStatus.NO_INTERNET, navStatusFor(all, good.copy(hasInternet = false), false))
        assertEquals(NavStatus.REROUTING, navStatusFor(NavStatusCode.RECALCULATING, good, false))
        assertEquals(NavStatus.NORMAL, navStatusFor(NavStatusCode.RECALCULATING, good, true))
        assertEquals(NavStatus.DESTINATION_REACHED, navStatusFor(NavStatusCode.ARRIVED, good, false))
        assertEquals(NavStatus.VIA_POINT_REACHED, navStatusFor(NavStatusCode.VIA_POINT, good, false))
        assertEquals(NavStatus.NORMAL, navStatusFor(NavStatusCode.NORMAL, good, false))
        assertEquals(NavStatus.GPS_OFF, navStatusFor(NavStatusCode.GPS_OFF, good, false))
        assertEquals(NavStatus.NO_NETWORK_SIGNAL, navStatusFor(NavStatusCode.NO_NETWORK, good, false))
        assertEquals(NavStatus.NO_INTERNET, navStatusFor(NavStatusCode.NO_INTERNET, good, false))
    }

    @Test
    fun reroutingIsSentForOneFramePerSnapshot() {
        val mapper = NavFrameMapper(zone)
        val first = snap(NavStatusCode.RECALCULATING, at = 10)
        assertEquals(NavStatus.REROUTING, mapper.map(first, good, true).status)
        assertEquals(NavStatus.NORMAL, mapper.map(first, good, true).status)
        assertEquals(NavStatus.REROUTING, mapper.map(snap(NavStatusCode.RECALCULATING, at = 20), good, true).status)
    }

    @Test
    fun outOfRangeIconKeepsThePreviousOne() {
        val mapper = NavFrameMapper(zone)
        assertEquals(NavFrameMapper.DEFAULT_ICON, mapper.map(snap(icon = 0), good, true).iconCode)
        assertEquals(21, mapper.map(snap(icon = 21), good, true).iconCode)
        assertEquals(21, mapper.map(snap(icon = 46), good, true).iconCode)
    }

    // ---- Status frame ---------------------------------------------------------------------------------

    @Test
    fun speedAlertIsOffOrClampedToTheModelRange() {
        assertEquals(0, speedAlertFor(Settings(speedAlertOn = false, speedAlertKmh = 100), moto))
        assertEquals(100, speedAlertFor(Settings(speedAlertOn = true, speedAlertKmh = 100), moto))
        assertEquals(120, speedAlertFor(Settings(speedAlertOn = true, speedAlertKmh = 200), scooter))
        assertEquals(10, speedAlertFor(Settings(speedAlertOn = true, speedAlertKmh = 3), moto))
    }

    @Test
    fun riderNameIsUppercaseWithDefault() {
        assertEquals("ALEX", riderNameFor(Settings(riderName = "Alex")))
        assertEquals("RIDER", riderNameFor(Settings(riderName = "")))
        assertEquals("RIDER", riderNameFor(Settings(riderName = "  ")))
    }

    @Test
    fun clearedFlagsStayYesForFourTicksAfterCountDropsToZero() {
        val t = ClearedFlagTracker()
        assertEquals(ClearedFlags(false, false), t.tick(messages = 0, missedCalls = 0))
        assertEquals(ClearedFlags(false, false), t.tick(messages = 2, missedCalls = 1))
        val after = (1..6).map { t.tick(messages = 0, missedCalls = if (it == 1) 1 else 0) }
        assertEquals(listOf(true, true, true, true, false, false), after.map { it.messages })
        assertEquals(listOf(false, true, true, true, true, false), after.map { it.missedCalls })
    }

    @Test
    fun newCountEndsTheClearedHold() {
        val t = ClearedFlagTracker()
        t.tick(3, 0)
        assertTrue(t.tick(0, 0).messages)
        assertEquals(false, t.tick(1, 0).messages)
        assertEquals(true, t.tick(0, 0).messages)
    }

    @Test
    fun phoneStatusClampsValues() {
        val s = buildPhoneStatus(
            PhoneSnapshot(batteryPercent = 140, charging = true, signalBars = 7),
            Settings(speedAlertOn = true, speedAlertKmh = 100),
            moto,
            LocalTime.of(10, 55, 23),
            ClearedFlags(messages = true, missedCalls = false),
        )
        assertEquals(100, s.batteryPercent)
        assertEquals(3, s.signalBars)
        assertEquals(100, s.speedAlertKmh)
        assertTrue(s.charging && s.messagesCleared && !s.missedCallsCleared)
    }
}
