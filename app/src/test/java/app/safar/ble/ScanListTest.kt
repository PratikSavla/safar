package app.safar.ble

import app.safar.core.VehicleFamily
import org.junit.Test
import kotlin.test.assertEquals

class ScanListTest {
    @Test
    fun keepsOnlyClusterNamesOncePerAddress() {
        val list = ScanList()
        assertEquals(ScanList.Change.NEW, list.add("SBM01A1B2C3D", "00:11:22:33:44:55", -60))
        assertEquals(ScanList.Change.NONE, list.add("Headphones", "00:11:22:33:44:66", -40))
        assertEquals(ScanList.Change.NONE, list.add(null, "00:11:22:33:44:77", -40))
        assertEquals(ScanList.Change.NONE, list.add("SBM01A1B2C3", "00:11:22:33:44:88", -40)) // 11 chars
        assertEquals(ScanList.Change.NONE, list.add("SBM01A1B2C3D", "00:11:22:33:44:55", -60))
        assertEquals(1, list.items.size)
    }

    @Test
    fun repeatAdvertisementUpdatesRssiAndKeepsOrder() {
        val list = ScanList()
        list.add("SBM01A1B2C3D", "00:11:22:33:44:55", -60)
        list.add("SAS01A1B2C3D", "00:11:22:33:44:66", -70)
        assertEquals(ScanList.Change.UPDATED, list.add(null, "00:11:22:33:44:55", -52))
        val items = list.items
        assertEquals(listOf("SBM01A1B2C3D", "SAS01A1B2C3D"), items.map { it.name })
        assertEquals(-52, items[0].rssi)
        assertEquals("V-Strom SX", items[0].model.name)
        assertEquals(VehicleFamily.SCOOTER, items[1].model.family)
        assertEquals("SAS01A1B2C3D", list.nameOf("00:11:22:33:44:66"))
    }
}
