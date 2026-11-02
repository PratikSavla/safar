package app.safar.ble

import app.safar.core.BikeCatalog
import app.safar.core.ScannedBike
import app.safar.protocol.isSuzukiCluster

/**
 * Scan results (docs 2.2 step 2): only 12-character names with "SA" or "SB", one row per bike, in the order
 * found. A repeat advertisement updates the RSSI (and the name, if the first one had none). The name is the
 * bike's own, so a bike that rotated its address shows once: the newest address replaces the old row.
 */
class ScanList {
    private val byAddress = LinkedHashMap<String, ScannedBike>()

    /** Every address seen with its name, so [nameOf] still answers for an address a bike has moved from. */
    private val names = HashMap<String, String>()

    val items: List<ScannedBike> get() = byAddress.values.toList()

    /** Returns [Change.NEW] for a new cluster, [Change.UPDATED] for a changed RSSI, else [Change.NONE]. */
    fun add(name: String?, address: String, rssi: Int): Change {
        val old = byAddress[address]
        val bleName = name?.takeIf(::isSuzukiCluster) ?: old?.name ?: return Change.NONE
        if (old != null && old.rssi == rssi && old.name == bleName) return Change.NONE
        val moved = byAddress.values.firstOrNull { it.name == bleName && it.address != address }
        if (moved != null) byAddress.remove(moved.address)
        byAddress[address] = ScannedBike(bleName, address, rssi, BikeCatalog.forBleName(bleName))
        names[address] = bleName
        return if (old == null && moved == null) Change.NEW else Change.UPDATED
    }

    fun clear() {
        byAddress.clear()
        names.clear()
    }

    fun nameOf(address: String): String? = byAddress[address]?.name ?: names[address]

    enum class Change { NEW, UPDATED, NONE }
}
