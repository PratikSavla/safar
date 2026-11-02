package app.safar.ble

import app.safar.core.BikeCatalog
import app.safar.core.ScannedBike
import app.safar.protocol.isSuzukiCluster

/**
 * Scan results (docs 2.2 step 2): only 12-character names with "SA" or "SB", one row per MAC address, in the
 * order found. A repeat advertisement updates the RSSI (and the name, if the first one had none).
 */
class ScanList {
    private val byAddress = LinkedHashMap<String, ScannedBike>()

    val items: List<ScannedBike> get() = byAddress.values.toList()

    /** Returns [Change.NEW] for a new cluster, [Change.UPDATED] for a changed RSSI, else [Change.NONE]. */
    fun add(name: String?, address: String, rssi: Int): Change {
        val old = byAddress[address]
        val bleName = name?.takeIf(::isSuzukiCluster) ?: old?.name ?: return Change.NONE
        if (old != null && old.rssi == rssi && old.name == bleName) return Change.NONE
        byAddress[address] = ScannedBike(bleName, address, rssi, BikeCatalog.forBleName(bleName))
        return if (old == null) Change.NEW else Change.UPDATED
    }

    fun clear() = byAddress.clear()

    fun nameOf(address: String): String? = byAddress[address]?.name

    enum class Change { NEW, UPDATED, NONE }
}
