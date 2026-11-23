package app.safar.ble

import app.safar.protocol.DeviceInformationUuids
import app.safar.protocol.GattProperty.INDICATE
import app.safar.protocol.GattProperty.NOTIFY
import app.safar.protocol.GattProperty.READ
import app.safar.protocol.GattProperty.WRITE
import app.safar.protocol.GattProperty.WRITE_NO_RESPONSE
import app.safar.protocol.GattUuids
import app.safar.protocol.uuid16
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Frame channel lookup: by UUID, by the original app's positions, by properties (docs 2.2 step 6, 2.6). */
class ClusterChannelTest {
    private fun service(short: Int, vararg chars: Pair<Int, Int>) =
        GattServiceInfo(uuid16(short), chars.map { (uuid, props) -> GattCharInfo(uuid16(uuid), props) })

    private val access = service(0x1800, 0x2A00 to READ)
    private val attribute = service(0x1801)
    private val deviceInfo = GattServiceInfo(DeviceInformationUuids.SERVICE, listOf(GattCharInfo(uuid16(0x2A29), READ)))

    @Test
    fun theVStromLayoutIsFoundByUuidWithResponse() {
        val channel = findClusterChannel(listOf(access, attribute, deviceInfo, service(0xFFF0, 0xFFF1 to WRITE, 0xFFF2 to NOTIFY)))
        assertEquals(ChannelLookup.UUID, channel?.lookup)
        assertEquals(GattUuids.SERVICE, channel?.service)
        assertEquals(GattUuids.TX_CHARACTERISTIC, channel?.tx?.uuid)
        assertEquals(GattUuids.RX_CHARACTERISTIC, channel?.rx?.uuid)
        assertEquals(false, channel?.txWithoutResponse)
    }

    @Test
    fun uuidsWinOverPositionsEvenWhenTheOrderDiffers() {
        val fff0 = service(0xFFF0, 0xFFF2 to NOTIFY, 0xFFF1 to WRITE_NO_RESPONSE)
        val channel = findClusterChannel(listOf(fff0, access, attribute, deviceInfo))
        assertEquals(ChannelLookup.UUID, channel?.lookup)
        assertEquals(uuid16(0xFFF1), channel?.tx?.uuid)
        assertEquals(true, channel?.txWithoutResponse, "write without response, as the original app")
    }

    @Test
    fun otherUuidsUseTheFourthServiceLikeTheOriginalApp() {
        val channel = findClusterChannel(listOf(access, attribute, deviceInfo, service(0xFFE0, 0xFFE1 to WRITE_NO_RESPONSE, 0xFFE2 to NOTIFY)))
        assertEquals(ChannelLookup.POSITION, channel?.lookup)
        assertEquals(uuid16(0xFFE1), channel?.tx?.uuid)
        assertEquals(uuid16(0xFFE2), channel?.rx?.uuid)
    }

    @Test
    fun withoutAFourthServiceTheChannelIsFoundByProperties() {
        val custom = service(0xFFE0, 0xFFE4 to READ, 0xFFE1 to (WRITE_NO_RESPONSE or NOTIFY), 0xFFE2 to INDICATE)
        val channel = findClusterChannel(listOf(access, custom))
        assertEquals(ChannelLookup.PROPERTIES, channel?.lookup)
        assertEquals(uuid16(0xFFE1), channel?.tx?.uuid)
        assertEquals(uuid16(0xFFE2), channel?.rx?.uuid, "a separate notifying characteristic is preferred")
    }

    @Test
    fun aFourthServiceWhosePropertiesDoNotFitFallsThroughToProperties() {
        val wrongOrder = service(0xFFE0, 0xFFE2 to NOTIFY, 0xFFE1 to WRITE)
        val channel = findClusterChannel(listOf(access, attribute, deviceInfo, wrongOrder))
        assertEquals(ChannelLookup.PROPERTIES, channel?.lookup)
        assertEquals(uuid16(0xFFE1), channel?.tx?.uuid)
    }

    @Test
    fun standardServicesNeverCarryTheChannel() {
        val writableInfo = GattServiceInfo(DeviceInformationUuids.SERVICE, listOf(GattCharInfo(uuid16(0x2A29), WRITE or NOTIFY)))
        assertNull(findClusterChannel(listOf(access, attribute, writableInfo)))
        assertNull(findClusterChannel(emptyList()))
    }

    @Test
    fun layoutTextNamesEveryServiceAndProperty() {
        val text = layoutText(listOf(attribute, service(0xFFF0, 0xFFF1 to WRITE, 0xFFF2 to NOTIFY)))
        assertEquals("1801; fff0 [fff1 write, fff2 notify]", text)
        assertEquals("0x00", propertiesText(0))
    }
}
