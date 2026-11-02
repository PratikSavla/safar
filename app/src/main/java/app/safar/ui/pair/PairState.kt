package app.safar.ui.pair

import app.safar.core.BikeCatalog
import app.safar.core.BikeModel
import app.safar.core.BikeState
import app.safar.core.LinkState
import app.safar.core.ScannedBike
import app.safar.ui.copy.Phrases

/** What the pair page shows (pair.js `render`), after the Bluetooth checks the prototype did not need. */
enum class PairPhase { NEEDS_PERMISSION, BLUETOOTH_OFF, UNSUPPORTED, LISTENING, RESULTS, NOTHING, CONNECTING, SUCCESS, ALREADY_LINKED }

/**
 * Inputs from the BLE layer plus what the rider tapped. [picked] is the address of the tapped cluster;
 * [attempted] is true once a connect to it was seen running (a link in Connecting since the tap).
 */
data class PairInputs(val bike: BikeState, val riderName: String, val picked: String?, val attempted: Boolean = false)

data class PairUi(
    val phase: PairPhase,
    val scanning: Boolean,
    val results: List<ScannedBike>,
    /** Model of the bike being connected or just paired. */
    val model: BikeModel?,
    val bikeName: String?,
    val address: String?,
    /** The connect to the tapped cluster ran and gave up or is backing off: say so above the list. */
    val failed: Boolean,
    /** The link is set up (MTU negotiated): the connecting steps move on to the rider name. */
    val mtuSet: Boolean,
    val riderName: String,
) {
    /** Line under the radar (pair.js `radar`). */
    val scanLine: String
        get() = when {
            scanning -> "${Phrases.LOOKING_FOR_BIKE}…"
            results.isNotEmpty() -> "Tap your bike to connect."
            else -> "Keep your phone close to your bike."
        }
}

/** How close a found bike is, from its signal bars (0..4): helps pick the right one when two show up. */
fun proximity(bars: Int): String = when {
    bars >= 4 -> "Very close"
    bars == 3 -> "Close by"
    bars == 2 -> "Nearby"
    else -> "Far away"
}

/**
 * [linked] is the address of the saved bike. While Connected it is the bike on the link, so a pick only
 * succeeds once that bike is the one the rider tapped; until then the old link is still being replaced.
 */
internal fun pairPhase(access: BtAccess, link: LinkState, scanning: Boolean, results: Int, picked: String?, linked: String?): PairPhase = when {
    access == BtAccess.UNSUPPORTED -> PairPhase.UNSUPPORTED
    access == BtAccess.NEEDS_PERMISSION -> PairPhase.NEEDS_PERMISSION
    access == BtAccess.BLUETOOTH_OFF || link == LinkState.BluetoothOff -> PairPhase.BLUETOOTH_OFF
    link is LinkState.Connecting -> PairPhase.CONNECTING
    link == LinkState.Connected && picked == null -> PairPhase.ALREADY_LINKED
    link == LinkState.Connected -> if (linked == picked) PairPhase.SUCCESS else PairPhase.CONNECTING
    results > 0 -> PairPhase.RESULTS
    scanning -> PairPhase.LISTENING
    else -> PairPhase.NOTHING
}

/** A connect for the current pick was seen; a new pick (even of the same cluster) starts over. */
internal fun pickAttempted(samePick: Boolean, wasAttempted: Boolean, link: LinkState): Boolean =
    link is LinkState.Connecting || (samePick && wasAttempted)

internal fun pairUi(input: PairInputs, access: BtAccess): PairUi {
    val bike = input.bike
    val connectingName = (bike.link as? LinkState.Connecting)?.name
    val pickedScan = bike.scan.firstOrNull { it.address == input.picked }
    // The saved bike only describes this page when the rider picked nothing or picked that same bike.
    val linked = bike.saved?.takeIf { input.picked == null || it.address == input.picked }
    val model = linked?.model?.takeIf { bike.link == LinkState.Connected }
        ?: pickedScan?.model
        ?: connectingName?.let(BikeCatalog::forBleName)
        ?: linked?.model
    val gaveUp = bike.link == LinkState.Idle || bike.link is LinkState.Reconnecting
    return PairUi(
        phase = pairPhase(access, bike.link, bike.scanning, bike.scan.size, input.picked, bike.saved?.address),
        scanning = bike.scanning,
        results = bike.scan.sortedByDescending { it.rssi },
        model = model,
        bikeName = linked?.name ?: pickedScan?.name ?: connectingName,
        address = linked?.address ?: input.picked,
        failed = input.picked != null && input.attempted && gaveUp && !bike.scanning,
        mtuSet = bike.mtu != null,
        riderName = input.riderName.trim(),
    )
}
