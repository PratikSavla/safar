package app.safar.service

import android.content.pm.ServiceInfo
import app.safar.ble.ReconnectPolicy
import app.safar.core.BikeCatalog
import app.safar.core.BikeState
import app.safar.core.ClusterReading
import app.safar.core.LinkState
import app.safar.core.NavSnapshot
import app.safar.core.NavState
import app.safar.core.NavStatusCode
import app.safar.core.SavedBike
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServiceRulesTest {
    private val saved = SavedBike("SBM01A1B2C3D", "00:11:22:33:44:55", BikeCatalog.forBleName("SBM01A1B2C3D"))
    private val reading = ClusterReading(0, 12345, 1.0, 2.0, 4, 'N', 'N'.code, 0x1D, 0L)
    private val device = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
    private val location = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION

    @Test
    fun runsWhileThePolicyWorksOnAnAddress() {
        val address = saved.address
        assertTrue(shouldRunService(ReconnectPolicy.State.Connecting(address, 0)))
        assertTrue(shouldRunService(ReconnectPolicy.State.Connected(address)))
        assertTrue(shouldRunService(ReconnectPolicy.State.Waiting(address, 2, 5_000L)))
        assertTrue(shouldRunService(ReconnectPolicy.State.BluetoothOff(resumeAddress = address)))
        assertFalse(shouldRunService(ReconnectPolicy.State.Idle), "user disconnect stops it")
    }

    @Test
    fun userDisconnectWhileBluetoothIsOffStopsIt() {
        val policy = ReconnectPolicy { 1_000L }
        policy.connect(saved.address)
        policy.onBluetoothOff()
        assertTrue(shouldRunService(policy.state), "waits for Bluetooth to resume the bike")
        policy.disconnect()
        assertEquals(ReconnectPolicy.State.BluetoothOff(resumeAddress = null), policy.state)
        assertFalse(shouldRunService(policy.state))
    }

    @Test
    fun typesFollowGrantedPermissionsOnApi34() {
        assertEquals(device or location, foregroundServiceTypes(34, bluetoothGranted = true, locationGranted = true))
        assertEquals(device, foregroundServiceTypes(35, bluetoothGranted = true, locationGranted = false))
        assertEquals(location, foregroundServiceTypes(36, bluetoothGranted = false, locationGranted = true))
        assertNull(foregroundServiceTypes(34, bluetoothGranted = false, locationGranted = false))
    }

    @Test
    fun olderVersionsNeedNoRuntimeCheckForConnectedDevice() {
        assertEquals(device, foregroundServiceTypes(31, bluetoothGranted = false, locationGranted = false))
        assertEquals(device or location, foregroundServiceTypes(29, bluetoothGranted = false, locationGranted = true))
        assertEquals(0, foregroundServiceTypes(28, bluetoothGranted = false, locationGranted = false))
    }

    @Test
    fun notificationShowsStateOdometerAndNextTurn() {
        val nav = NavState(
            active = true,
            snapshot = NavSnapshot(4, true, 300, 0L, 41_600, NavStatusCode.NORMAL, "Turn right onto Main Rd", 0L),
        )
        val connected = notificationContent(BikeState(link = LinkState.Connected, saved = saved, cluster = reading), nav)
        assertEquals("Connected to V-Strom SX", connected.title)
        assertEquals("Next: Turn right onto Main Rd in 300 m · Odometer 12,345 km", connected.text)
        assertTrue(connected.canDisconnect)

        val lost = notificationContent(
            BikeState(link = LinkState.Reconnecting(3, 0L), saved = saved, lastCluster = reading),
            NavState(),
        )
        assertEquals("Reconnecting to V-Strom SX · attempt 3", lost.title)
        assertEquals("Odometer 12,345 km (last seen)", lost.text)

        val idle = notificationContent(BikeState(), NavState())
        assertEquals("Not connected", idle.title)
        assertFalse(idle.canDisconnect)
    }
}
