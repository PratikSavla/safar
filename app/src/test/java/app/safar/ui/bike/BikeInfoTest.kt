package app.safar.ui.bike

import app.safar.core.BikeState
import app.safar.core.DeviceInfo
import app.safar.core.LinkState
import app.safar.data.SavedBikeRecord
import app.safar.ui.testing.FakeBike
import app.safar.ui.testing.FakeSavedBike
import app.safar.ui.testing.MainDispatcherRule
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BikeInfoTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val record = SavedBikeRecord("SBM01A1B2C3D", "00:00:00:00:00:01")

    @Test
    fun noSavedBikeMeansNoInfo() {
        assertNull(bikeInfo(BikeState(), null))
    }

    @Test
    fun savedRecordFillsTheLinkCard() {
        val info = bikeInfo(BikeState(link = LinkState.Reconnecting(2, 0L), framesReceived = 42), record)!! // asserted non-null by this test
        assertEquals("V-Strom SX", info.model.name)
        assertEquals("Reconnecting", info.linkWord)
        assertEquals("waiting", info.stamp)
        assertFalse(info.connected)
        val rows = linkRows(info).toMap()
        assertEquals("SBM01A1B2C3D", rows["BLE name"])
        assertEquals("00:00:00:00:00:01", rows["Address"])
        assertEquals("sum of bytes 1–27", rows["Checksum"])
        assertEquals("65 requested", rows["MTU"])
        assertEquals("FFF0", rows["Service"])
        assertEquals("FFF1", rows["Write (TX)"])
        assertEquals("FFF2", rows["Notify (RX)"])
        assertEquals("10–299 km/h", rows["Speed alert range"])
        assertEquals("42", rows["Frames received"])
    }

    @Test
    fun riderRowsUsePlainWords() {
        val device = DeviceInfo(manufacturer = "SUZUKI", serial = "A1B2C3D0042", firmware = "2.4.1", hardware = "HW-03")
        val info = bikeInfo(BikeState(link = LinkState.Connected, deviceInfo = device), record)!! // asserted non-null by this test
        assertEquals("connected", info.stamp)
        val rows = riderRows(info)
        assertEquals(listOf("Model", "Type", "Serial number", "Software version", "Hardware version", "Fuel gauge"), rows.map { it.first })
        val values = rows.toMap()
        assertEquals("V-Strom SX", values["Model"])
        assertEquals("Motorcycle", values["Type"])
        assertEquals("A1B2C3D0042", values["Serial number"])
        assertEquals("2.4.1", values["Software version"])
        assertEquals("HW-03", values["Hardware version"])
        assertTrue(rows.none { (k, v) -> "BLE" in k || "MTU" in k || "0x" in v })
    }

    @Test
    fun scooterNamesUseTheInvertedChecksum() {
        assertEquals("inverted (0xFF − sum)", checksumLabel("SAS01A1B2C3D"))
    }

    @Test
    fun missingDeviceFieldsShowADash() {
        val rows = developerDeviceRows(DeviceInfo(manufacturer = "SUZUKI", software = " ")).toMap()
        assertEquals("SUZUKI", rows["Manufacturer"])
        assertEquals("—", rows["Software rev."])
        assertEquals(4, developerDeviceRows(null).size)
        val offline = bikeInfo(BikeState(), record)!! // asserted non-null by this test
        assertEquals("—", riderRows(offline).toMap()["Software version"])
        assertEquals("offline", offline.stamp)
    }

    @Test
    fun viewModelForgetsTheBike() = runTest {
        val bike = FakeBike(BikeState(link = LinkState.Connected, mtu = 65))
        val vm = BikeViewModel(bike, FakeSavedBike(record))
        backgroundScope.launch(main.dispatcher) { vm.state.collect {} }
        assertTrue(vm.state.value.loaded)
        assertEquals("65", vm.state.value.info?.mtu)
        assertTrue(vm.forget())
        assertEquals(1, bike.forgotten)
    }
}
