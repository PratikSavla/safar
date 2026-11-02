package app.safar.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LogTest {
    private val cluster = "a5 37 30 30 30 30 30 30 34 36 32 30 30 32 33 36 32 30 30 34 36 32 33 4e 33 1d ff ff eb 7f"

    private fun ms(millis: Long) = Instant.ofEpochMilli(millis)

    /** Port of the last test of docs/helper/tests/google-maps.test.ts. */
    @Test
    fun `log export writes one JSON object per line`() {
        val log = LogBuffer(2)
        val frame = fromHex(cluster)
        log.add(eventEntry("connected", mapOf("mtu" to JsonPrimitive(65)), ms(0)))
        log.add(frameEntry(LogDirection.RX, frame, true, ms(1000)))
        log.add(frameEntry(LogDirection.RX, frame, true, ms(2000)))
        val lines = toJsonLines(log.entries()).trim().split("\n")
        assertEquals(2, lines.size) // capacity 2 drops the oldest
        val first = Json.parseToJsonElement(lines[0]).jsonObject
        assertEquals(
            listOf("frame", "7", "1970-01-01T00:00:01.000Z"),
            listOf("kind", "type", "t").map { first.getValue(it).jsonPrimitive.content },
        )
    }

    @Test
    fun `lines match the helper output byte for byte`() {
        val short = fromHex("a5 37")
        val entries = listOf(
            eventEntry("connected", logDetail("mtu" to 65, "ok" to true, "name" to "a\"b"), ms(0)),
            eventEntry("x", emptyMap(), ms(5)),
            frameEntry(LogDirection.TX, short, false, ms(1), note = ""),
            frameEntry(LogDirection.TX, short, false, ms(1), note = "bad"),
            eventEntry("scan", ms(1_762_082_400_123)),
        )
        val expected = listOf(
            """{"t":"1970-01-01T00:00:00.000Z","kind":"event","name":"connected","detail":{"mtu":65,"ok":true,"name":"a\"b"}}""",
            """{"t":"1970-01-01T00:00:00.005Z","kind":"event","name":"x","detail":{}}""",
            """{"t":"1970-01-01T00:00:00.001Z","kind":"frame","dir":"tx","type":"7","hex":"a5 37","ok":false}""",
            """{"t":"1970-01-01T00:00:00.001Z","kind":"frame","dir":"tx","type":"7","hex":"a5 37","ok":false,"note":"bad"}""",
            """{"t":"2025-11-02T11:20:00.123Z","kind":"event","name":"scan"}""",
        ).joinToString("\n") + "\n"
        assertEquals(expected, toJsonLines(entries))
        assertEquals("\n", toJsonLines(emptyList()))
    }

    @Test
    fun `header line has the format and omits a missing bike name`() {
        val info = LogHeaderInfo("Safar", "0.1.0", "Test phone", "17", "SBM01A1B2C3D")
        assertEquals(
            """{"kind":"header","format":"suzuki-ble-log/1","app":"Safar","appVersion":"0.1.0",""" +
                """"phone":"Test phone","android":"17","bikeName":"SBM01A1B2C3D"}""",
            logHeader(info),
        )
        assertEquals(false, logHeader(info.copy(bikeName = null)).contains("bikeName"))
    }

    @Test
    fun `ring buffer evicts the oldest entries in order`() {
        val log = LogBuffer(3)
        (1..10).forEach { log.add(eventEntry("e$it", ms(it.toLong()))) }
        assertEquals(3, log.size)
        assertEquals(listOf("e8", "e9", "e10"), log.entries().map { (it as LogEntry.Event).name })
        log.clear()
        assertEquals(0, log.entries().size)
        assertFailsWith<IllegalArgumentException> { LogBuffer(0) }
        assertFailsWith<IllegalArgumentException> { logDetail("bad" to listOf(1)) }
    }

    @Test
    fun `ring buffer is safe under concurrent writers`() {
        val log = LogBuffer(500)
        val pool = Executors.newFixedThreadPool(8)
        repeat(8) { worker ->
            pool.execute { repeat(1000) { log.add(eventEntry("w$worker", ms(it.toLong()))) } }
        }
        pool.shutdown()
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        assertEquals(500, log.size)
        assertEquals(500, log.entries().size)
    }
}
