package app.safar.ui.pair

import app.safar.core.BikeCatalog
import app.safar.core.BikeModel
import app.safar.core.BikeState
import app.safar.core.LinkState
import app.safar.core.ScannedBike

/** What the pair page shows (pair.js `render`), after the Bluetooth checks the prototype did not need. */
enum class PairPhase { NEEDS_PERMISSION, BLUETOOTH_OFF, UNSUPPORTED, LISTENING, RESULTS, NOTHING, CONNECTING, SUCCESS, ALREADY_LINKED }

/** Inputs from the BLE layer plus what the rider tapped. [picked] is the address of the tapped cluster. */
data class PairInputs(val bike: BikeState, val riderName: String, val picked: String?)

data class PairUi(
    val phase: PairPhase,
    val scanning: Boolean,
    val results: List<ScannedBike>,
    /** Model of the bike being connected or just paired. */
    val model: BikeModel?,
    val bikeName: String?,
    val address: String?,
    /** The rider tapped a cluster and the link fell back to idle: say so above the list. */
    val failed: Boolean,
    /** MTU negotiated: the connecting steps move on to the rider name. */
    val mtuSet: Boolean,
    val riderName: String,
) {
    /** Line under the radar (pair.js `radar`). */
    val scanLine: String
        get() = when {
            scanning -> "Listening for clusters…"
            results.isNotEmpty() -> "Scan finished."
            else -> "Turn the ignition on, then scan."
        }
}

internal fun pairPhase(access: BtAccess, link: LinkState, scanning: Boolean, results: Int, picked: String?): PairPhase = when {
    access == BtAccess.UNSUPPORTED -> PairPhase.UNSUPPORTED
    access == BtAccess.NEEDS_PERMISSION -> PairPhase.NEEDS_PERMISSION
    access == BtAccess.BLUETOOTH_OFF || link == LinkState.BluetoothOff -> PairPhase.BLUETOOTH_OFF
    link is LinkState.Connecting -> PairPhase.CONNECTING
    link == LinkState.Connected -> if (picked != null) PairPhase.SUCCESS else PairPhase.ALREADY_LINKED
    results > 0 -> PairPhase.RESULTS
    scanning -> PairPhase.LISTENING
    else -> PairPhase.NOTHING
}

internal fun pairUi(input: PairInputs, access: BtAccess): PairUi {
    val bike = input.bike
    val connectingName = (bike.link as? LinkState.Connecting)?.name
    val pickedScan = bike.scan.firstOrNull { it.address == input.picked }
    val model = bike.saved?.model?.takeIf { bike.link == LinkState.Connected }
        ?: pickedScan?.model
        ?: connectingName?.let(BikeCatalog::forBleName)
        ?: bike.saved?.model
    return PairUi(
        phase = pairPhase(access, bike.link, bike.scanning, bike.scan.size, input.picked),
        scanning = bike.scanning,
        results = bike.scan.sortedByDescending { it.rssi },
        model = model,
        bikeName = bike.saved?.name ?: pickedScan?.name ?: connectingName,
        address = bike.saved?.address ?: input.picked,
        failed = input.picked != null && bike.link == LinkState.Idle && !bike.scanning,
        mtuSet = bike.mtu != null,
        riderName = input.riderName.trim(),
    )
}
