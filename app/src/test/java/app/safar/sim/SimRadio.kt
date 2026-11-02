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

/** The phone's Bluetooth adapter switch; [set] flips it like the quick settings tile. */
class FakeBluetooth(on: Boolean = true) : BluetoothSwitch {
    override var isOn: Boolean = on
        private set
    private var listener: ((Boolean) -> Unit)? = null

    override fun register(onChange: (Boolean) -> Unit) {
        listener = onChange
    }

    fun set(on: Boolean) {
        isOn = on
        listener?.invoke(on)
    }
}

/** A scan that finds every powered-on bike of [bikes] at once. [starts] counts scans. */
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

/** [BleRadio] whose GATT links reach the simulated [bikes]. [opened] lists every address a link was opened to. */
class SimRadio(private val bikes: List<FakeBikePeripheral>, private val log: DebugLog) : BleRadio {
    override val bluetooth = FakeBluetooth()
    val scanner = FakeScanner { bikes }
    val opened = mutableListOf<String>()
    val links = mutableListOf<SimGattLink>()

    override fun canConnect(): Boolean = true

    override fun scanner(scope: CoroutineScope): BikeScanner = scanner

    override fun open(address: String): GattPort? {
        opened += address
        val bike = bikes.firstOrNull { it.address == address } ?: return null
        return SimGattLink(bike, log).also { links += it }
    }
}
