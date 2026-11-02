package app.safar.ble

import app.safar.core.LinkState
import org.junit.Test
import kotlin.test.assertEquals

class LinkStatesTest {
    private val mac = "00:11:22:33:44:55"
    private val name = "SBM01A1B2C3D"

    @Test
    fun mapsEveryPolicyState() {
        assertEquals(LinkState.Idle, linkStateFor(ReconnectPolicy.State.Idle, false, "", 0))
        assertEquals(LinkState.Searching, linkStateFor(ReconnectPolicy.State.Idle, true, "", 0))
        assertEquals(LinkState.BluetoothOff, linkStateFor(ReconnectPolicy.State.BluetoothOff(mac), true, name, 0))
        assertEquals(LinkState.Connecting(name), linkStateFor(ReconnectPolicy.State.Connecting(mac, 2), false, name, 0))
        assertEquals(LinkState.Connected, linkStateFor(ReconnectPolicy.State.Connected(mac), true, name, 0))
        assertEquals(
            LinkState.Reconnecting(3, 12_345L),
            linkStateFor(ReconnectPolicy.State.Waiting(mac, 3, 10_000), false, name, 12_345L),
        )
    }

    @Test
    fun deviceInfoTrimsPaddingAndKeepsPnpAsHex() {
        val info = deviceInfoFrom(
            mapOf(
                app.safar.protocol.DeviceInformationUuids.MANUFACTURER_NAME to "Suzuki\u0000\u0000".toByteArray(),
                app.safar.protocol.DeviceInformationUuids.FIRMWARE_REVISION to " 1.02 ".toByteArray(),
                app.safar.protocol.DeviceInformationUuids.SERIAL_NUMBER to ByteArray(0),
                app.safar.protocol.DeviceInformationUuids.PNP_ID to byteArrayOf(0x01, 0x0D, 0x00),
            ),
        )
        assertEquals("Suzuki", info.manufacturer)
        assertEquals("1.02", info.firmware)
        assertEquals(null, info.serial)
        assertEquals(null, info.modelNumber)
        assertEquals("01 0d 00", info.pnpId)
    }
}
