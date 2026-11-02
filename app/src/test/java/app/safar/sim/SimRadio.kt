package app.safar.sim

import app.safar.ble.BikeScanner
import app.safar.ble.BleRadio
import app.safar.ble.BluetoothSwitch
import app.safar.ble.GattPort
import app.safar.core.BikeCatalog
import app.safar.core.DebugLog
import app.safar.core.ScannedBike
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The phone's Bluetooth adapter switch; [set] flips it like the quick settings tile. Turning it off also ends
 * every GATT link ([dropLinks]), as the adapter does on a phone.
 */
class FakeBluetooth(on: Boolean = true, private val dropLinks: () -> Unit = {}) : BluetoothSwitch {
    /** Whether the GATT disconnect callback or the adapter-off broadcast reaches the app first; either can. */
    enum class OffOrder { LINKS_FIRST, LISTENER_FIRST }

    override var isOn: Boolean = on
        private set
    private var listener: ((Boolean) -> Unit)? = null

    override fun register(onChange: (Boolean) -> Unit) {
        listener = onChange
    }

    fun set(on: Boolean, order: OffOrder = OffOrder.LINKS_FIRST) {
        isOn = on
        if (!on && order == OffOrder.LINKS_FIRST) dropLinks()
        listener?.invoke(on)
        if (!on && order == OffOrder.LISTENER_FIRST) dropLinks()
    }
}

/** A scan that finds every powered-on bike of [bikes] at its current address at once. [starts] counts scans. */
class FakeScanner(private val bikes: () -> List<FakeBikePeripheral>) : BikeScanner {
    private val found = MutableStateFlow<List<ScannedBike>>(emptyList())
    private val active = MutableStateFlow(false)
    override val results: StateFlow<List<ScannedBike>> = found
    override val scanning: StateFlow<Boolean> = active
    var starts = 0
        private set

    override fun nameOf(address: String): String? = found.value.firstOrNull { it.address == address }?.name

    override fun start(): Boolean {
        starts++
        active.value = true
        found.value = bikes().filter { it.poweredOn }.map {
            ScannedBike(it.name, it.address, rssi = SCAN_RSSI, model = BikeCatalog.forBleName(it.name))
        }
        return true
    }

    override fun stop() {
        active.value = false
    }

    private companion object {
        const val SCAN_RSSI = -58
    }
}

/** HCI 0x16, "terminated by local host": the status a link gets when the phone's adapter turns off. */
const val ADAPTER_OFF_STATUS = 0x16

/**
 * [BleRadio] whose GATT links reach the simulated [bikes]. [opened] lists every address a link was opened to;
 * an address a bike left still opens a link, which times out like a real connect to a vanished address.
 */
class SimRadio(private val bikes: List<FakeBikePeripheral>, private val log: DebugLog) : BleRadio {
    override val bluetooth = FakeBluetooth { bikes.forEach { it.dropLink(ADAPTER_OFF_STATUS) } }

    /** Every scanner handed out, in order: BikeManager asks for the rider's first, then its finder's. */
    val scanners = mutableListOf<FakeScanner>()

    /** The rider's scan on the Pair screen. */
    val scanner: FakeScanner get() = scanners[0]

    /** The reconnect fallback's scan (search by name). */
    val findScanner: FakeScanner get() = scanners[1]
    val opened = mutableListOf<String>()
    val links = mutableListOf<SimGattLink>()

    /** Set to a permission name to deny scanning, as a phone without BLUETOOTH_SCAN does. */
    var deniedScanPermission: String? = null

    override fun canConnect(): Boolean = true

    override fun missingScanPermission(): String? = deniedScanPermission

    override fun scanner(scope: CoroutineScope): BikeScanner = FakeScanner { bikes }.also { scanners += it }

    override fun open(address: String): GattPort? {
        opened += address
        val bike = bikes.firstOrNull { it.address == address || address in it.formerAddresses } ?: return null
        return SimGattLink(bike, log, address).also { links += it }
    }
}
