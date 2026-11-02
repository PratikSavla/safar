package app.safar.protocol

import kotlin.test.Test
import kotlin.test.assertEquals

class ConnectionTest {
    @Test
    fun uuid16UsesTheBluetoothBaseUuid() {
        assertEquals("0000180a-0000-1000-8000-00805f9b34fb", DeviceInformationUuids.SERVICE.toString())
        assertEquals("00002a50-0000-1000-8000-00805f9b34fb", DeviceInformationUuids.PNP_ID.toString())
        assertEquals(GattUuids.SERVICE, uuid16(0xFFF0))
        assertEquals(GattUuids.CCCD, uuid16(0x2902))
    }

    @Test
    fun deviceInformationCoversAllCharacteristics() {
        assertEquals(9, DeviceInformationUuids.ALL.toSet().size)
        assertEquals((0x2A23..0x2A2A).map(::uuid16) + uuid16(0x2A50), DeviceInformationUuids.ALL.sortedBy { it.toString() })
    }

    @Test
    fun connectionValuesMatchDocs() {
        assertEquals(20_000L, ConnectTiming.CONNECT_TIMEOUT_MS)
        assertEquals(500L, ConnectTiming.DISCOVER_DELAY_MS)
        assertEquals(250, ConnectTiming.REQUEST_MTU)
        assertEquals(10_000L, ConnectTiming.SCAN_DURATION_MS)
    }
}
