package app.safar.ui.pair

import app.safar.core.BikeCatalog
import app.safar.core.BikeModel
import app.safar.core.BikeState
import app.safar.core.LinkState
import app.safar.core.ScannedBike
import app.safar.protocol.ConnectTiming
import app.safar.ui.copy.Phrases

/**
 * How long the pair page waits on one pick before it says it couldn't reach the bike: two full connect
 * attempts. The link may still come up later; the page then moves on to success by itself.
 */
const val PICK_TIMEOUT_MS = 2 * ConnectTiming.CONNECT_TIMEOUT_MS

/** What the pair page shows (pair.js `render`), after the Bluetooth checks the prototype did not need. */
enum class PairPhase { NEEDS_PERMISSION, BLUETOOTH_OFF, UNSUPPORTED, LISTENING, RESULTS, NOTHING, CONNECTING, SUCCESS, ALREADY_LINKED }

/**
 * Inputs from the BLE layer plus what the rider tapped. [picked] is the address of the tapped cluster and
 * [pickedName] its BLE name; [attempted] is true once a connect to it was seen running (a link in Connecting
 * since the tap); [timedOut] once the pick has waited [PICK_TIMEOUT_MS] without success.
 */
data class PairInputs(
    val bike: BikeState,
    val riderName: String,
    val picked: String?,
    val attempted: Boolean = false,
    val pickedName: String? = null,
    val timedOut: Boolean = false,
)

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
    /** The negotiated MTU, once the link is set up: the connecting steps move on to the rider name. */
    val mtu: Int?,
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

/** The pick and the bike on the link: a BLE name is the bike's own, so the same name is the same bike at a new address. */
internal data class PickMatch(val picked: String?, val linked: String?, val pickedName: String? = null, val linkedName: String? = null) {
    val same: Boolean get() = linked == picked || (pickedName != null && pickedName == linkedName)
}

/**
 * [match] holds the saved bike, which while Connected is the bike on the link: a pick only succeeds once that
 * bike is the one the rider tapped; until then the old link is still being replaced. A pick that [timedOut]
 * goes back to the list, where the page says it couldn't reach the bike.
 */
internal fun pairPhase(access: BtAccess, link: LinkState, scanning: Boolean, results: Int, match: PickMatch, timedOut: Boolean = false): PairPhase {
    val phase = when {
        access == BtAccess.UNSUPPORTED -> PairPhase.UNSUPPORTED
        access == BtAccess.NEEDS_PERMISSION -> PairPhase.NEEDS_PERMISSION
        access == BtAccess.BLUETOOTH_OFF || link == LinkState.BluetoothOff -> PairPhase.BLUETOOTH_OFF
        link is LinkState.Connecting -> PairPhase.CONNECTING
        link == LinkState.Connected && match.picked == null -> PairPhase.ALREADY_LINKED
        link == LinkState.Connected -> if (match.same) PairPhase.SUCCESS else PairPhase.CONNECTING
        else -> null
    }
    if (phase != null && !(timedOut && phase == PairPhase.CONNECTING)) return phase
    return when {
        results > 0 -> PairPhase.RESULTS
        scanning -> PairPhase.LISTENING
        else -> PairPhase.NOTHING
    }
}

/** [pairPhase] for addresses only, as before a name is known. */
internal fun pairPhase(access: BtAccess, link: LinkState, scanning: Boolean, results: Int, picked: String?, linked: String?): PairPhase =
    pairPhase(access, link, scanning, results, PickMatch(picked, linked))

/** A connect for the current pick was seen; a new pick (even of the same cluster) starts over. */
internal fun pickAttempted(samePick: Boolean, wasAttempted: Boolean, link: LinkState): Boolean =
    link is LinkState.Connecting || (samePick && wasAttempted)

internal fun pairUi(input: PairInputs, access: BtAccess): PairUi {
    val bike = input.bike
    val connectingName = (bike.link as? LinkState.Connecting)?.name
    val pickedScan = bike.scan.firstOrNull { it.address == input.picked }
    val match = PickMatch(input.picked, bike.saved?.address, input.pickedName ?: pickedScan?.name, bike.saved?.name)
    // The saved bike only describes this page when the rider picked nothing or picked that same bike.
    val linked = bike.saved?.takeIf { input.picked == null || match.same }
    val phase = pairPhase(access, bike.link, bike.scanning, bike.scan.size, match, input.timedOut)
    val model = linked?.model?.takeIf { bike.link == LinkState.Connected }
        ?: pickedScan?.model
        ?: connectingName?.let(BikeCatalog::forBleName)
        ?: linked?.model
    val gaveUp = bike.link == LinkState.Idle || bike.link is LinkState.Reconnecting
    return PairUi(
        phase = phase,
        scanning = bike.scanning,
        results = bike.scan.sortedByDescending { it.rssi },
        model = model,
        bikeName = linked?.name ?: pickedScan?.name ?: connectingName,
        address = linked?.address ?: input.picked,
        failed = input.picked != null && !bike.scanning && phase != PairPhase.SUCCESS && ((input.attempted && gaveUp) || input.timedOut),
        mtu = bike.mtu,
        riderName = input.riderName.trim(),
    )
}
