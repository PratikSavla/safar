package app.safar.e2e

import app.safar.ble.WriteQueue
import app.safar.core.AlertSource
import app.safar.core.NavSnapshot
import app.safar.core.NavStatusCode
import app.safar.log.AppLog
import app.safar.log.LogFileStore
import app.safar.protocol.ChecksumMode
import app.safar.protocol.LOG_FORMAT
import app.safar.protocol.LogHeaderInfo
import app.safar.protocol.fromHex
import app.safar.protocol.toHex
import app.safar.protocol.validateFrame
import app.safar.sim.BadFrames
import app.safar.sim.BikeRig
import app.safar.sim.CLOCK_BASE
import app.safar.sim.MOTO_NAME
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The real debug log (AppLog + LogFileStore) under a full session, and its export (docs 12.6, 12.7). */
@OptIn(ExperimentalCoroutinesApi::class)
class DebugLogTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun TestScope.loggedRig(): BikeRig = BikeRig(this) { now ->
        val header = { LogHeaderInfo("Safar", "0.1.0", "Test phone", "17 (API 37)", bikeName = MOTO_NAME) }
        val files = LogFileStore(tmp.newFolder("logs"), tmp.newFolder("exports"), header, StandardTestDispatcher(testScheduler))
        AppLog(backgroundScope, flowOf(true), files, now)
    }

    /** Rider, status, navigation, call, missed call and message frames out; cluster frames and a bad one in. */
    private fun BikeRig.rideWithTraffic() {
        pairFromScan()
        advance(3_000)
        navigation.show(
            NavSnapshot(4, true, 250, CLOCK_BASE + 900_000, 4_300, NavStatusCode.NORMAL, "Turn right", now()),
        )
        alerts.callRinging("Alex", AlertSource.PHONE)
        advance(1_500)
        alerts.callMissed("Alex", AlertSource.PHONE)
        alerts.messageReceived("Rider")
        bike.inject(BadFrames.badChecksum(bike.clusterFrame()))
        advance(8_000)
        manager.disconnect()
        advance(100)
    }

    private suspend fun BikeRig.exportLines(hideNames: Boolean): List<JsonObject> =
        log.export(hideNames).readLines().map { Json.parseToJsonElement(it).jsonObject }

    private fun List<JsonObject>.frames(dir: String) =
        filter { it.str("kind") == "frame" && it.str("dir") == dir }

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.content

    @Test
    fun everyFrameIsLoggedAsHexAndEveryLineIsJson() = runTest {
        val rig = loggedRig()
        rig.rideWithTraffic()
        val lines = rig.exportLines(hideNames = false)

        val header = lines.first()
        assertEquals("header", header.str("kind"))
        assertEquals(LOG_FORMAT, header.str("format"))
        assertEquals("suzuki-ble-log/1", header.str("format"))
        assertEquals(MOTO_NAME, header.str("bikeName"))

        val tx = lines.frames("tx")
        assertEquals(rig.bike.writes.map { toHex(it.bytes) }, tx.map { it.str("hex") }, "every TX frame, in order")
        assertTrue("123456".all { type -> tx.any { it.str("type") == type.toString() } }, "every TX type present")
        assertEquals("write failed: ${WriteQueue.CANCELLED}", tx.last().str("note"), "the write the disconnect cut off")
        assertTrue(tx.dropLast(1).all { it.str("ok") == "true" })
        val rx = lines.frames("rx")
        assertEquals(rig.bike.notified.map(::toHex), rx.map { it.str("hex") }, "every RX frame, in order")
        assertEquals(1, rx.count { it.str("ok") == "false" }, "the corrupt frame is logged as invalid")
        assertTrue(lines.drop(1).all { it.str("t")?.startsWith("2026-11-0") == true }, "every line has a time")
    }

    @Test
    fun hideNamesMasksNamesAndKeepsChecksumsValid() = runTest {
        val rig = loggedRig()
        rig.rideWithTraffic()
        val plain = rig.exportLines(hideNames = false)
        val masked = rig.exportLines(hideNames = true)

        assertEquals("SBM01*******", masked.first().str("bikeName"))
        assertEquals(plain.size, masked.size)
        val plainTx = plain.frames("tx")
        val maskedTx = masked.frames("tx")
        for ((p, m) in plainTx.zip(maskedTx)) {
            val type = p.str("type")?.single()
            val before = fromHex(p.str("hex").orEmpty())
            val after = fromHex(m.str("hex").orEmpty())
            assertTrue(validateFrame(after, ChecksumMode.SUM).ok, "masked '$type' frame keeps a valid checksum")
            if (type in listOf('2', '4', '5', '6')) {
                val text = String(after, Charsets.ISO_8859_1)
                assertFalse(text.contains("Alex", ignoreCase = true) || text.contains("Rider", ignoreCase = true))
                assertTrue(after.count { it == '*'.code.toByte() } >= 4, "name bytes become '*' in '$type'")
            } else {
                assertEquals(toHex(before), toHex(after), "'$type' frames are not changed")
            }
        }
        val connecting = masked.first { it.str("name") == "Connecting" }["detail"]?.jsonObject
        assertEquals("SBM01*******", connecting?.str("bike"))
        assertEquals("0A:1B:2C:**:**:**", connecting?.str("address"))
    }
}
