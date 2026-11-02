package app.safar.ui.home

import app.safar.core.AlertCounter
import app.safar.core.BikeCatalog
import app.safar.core.BikeState
import app.safar.core.LinkState
import app.safar.core.NavState
import app.safar.core.NavStatusCode
import app.safar.core.RideState
import app.safar.core.VehicleFamily
import app.safar.data.FuelMethod
import app.safar.data.FuelStats
import app.safar.ui.components.StampTone
import app.safar.ui.components.ValueOrigin
import app.safar.ui.copy.LinkCopy
import app.safar.ui.format.Formatters
import app.safar.ui.fuel.rangeNote
import app.safar.ui.format.ValueSource
import kotlin.math.roundToInt

/** The one large connection state (docs 12.5 rule 1). */
enum class ConnKind { SEARCHING, CONNECTING, CONNECTED, RECONNECTING, IDLE, BLUETOOTH_OFF }

/**
 * The connection card in the rider's words ([LinkCopy]). [link], [bikeName], [mtu] and [lastFrameAt] feed the
 * Developer mode line only.
 */
data class ConnUi(
    val kind: ConnKind,
    val title: String,
    val subtitle: String,
    val stamp: String,
    val tone: StampTone,
    val family: VehicleFamily,
    val bikeName: String?,
    val savedAddress: String?,
    val lastFrameAt: Long?,
    /** Upcoming reconnect attempt (1-based) and when it starts; only while reconnecting. */
    val attempt: Int = 0,
    val nextAttemptAt: Long? = null,
    val link: LinkState = LinkState.Idle,
    val mtu: Int? = null,
)

internal fun connUi(bike: BikeState): ConnUi {
    val link = bike.link
    val family = bike.saved?.model?.family
        ?: (link as? LinkState.Connecting)?.name?.let { BikeCatalog.forBleName(it).family }
        ?: VehicleFamily.MOTORCYCLE
    val (kind, tone) = when (link) {
        LinkState.Connected -> ConnKind.CONNECTED to StampTone.MOSS
        is LinkState.Connecting -> ConnKind.CONNECTING to StampTone.SLATE
        is LinkState.Reconnecting -> ConnKind.RECONNECTING to StampTone.AMBER
        LinkState.Searching -> ConnKind.SEARCHING to StampTone.SLATE
        LinkState.BluetoothOff -> ConnKind.BLUETOOTH_OFF to StampTone.RUST
        LinkState.Idle -> ConnKind.IDLE to StampTone.RUST
    }
    val reconnect = link as? LinkState.Reconnecting
    return ConnUi(
        kind, LinkCopy.headline(bike), LinkCopy.detail(bike), LinkCopy.stamp(link, bike.saved != null), tone, family,
        bike.saved?.name, bike.saved?.address, bike.cluster?.receivedAtMillis,
        attempt = reconnect?.attempt ?: 0, nextAttemptAt = reconnect?.nextAttemptAtMillis, link = link, mtu = bike.mtu,
    )
}

enum class FuelWarning { ONE_BAR, EMPTY }

/** Shown in place of the gear before the bike has reported one. */
internal const val NO_GEAR = "–"

/**
 * The ledger grid. [live] false = last known before the link dropped. [rangeNote] is the rider's note under the
 * range (null when the number speaks for itself); [rangeSource] is the Developer mode line.
 */
data class LedgerUi(
    val odometerKm: Int,
    val tripAKm: Double,
    val tripBKm: Double,
    val fuelBars: Int?,
    val fuelMax: Int,
    val gear: String,
    val readingAt: Long,
    val live: Boolean,
    val rangeText: String,
    val rangeNote: String?,
    val rangeSource: String,
    val fuelWarning: FuelWarning?,
    val warningText: String?,
) {
    /** Where odometer, trips, fuel and gear come from, for [app.safar.ui.components.Freshness]. */
    val bikeOrigin: ValueOrigin get() = ValueOrigin(ValueSource.BIKE, readingAt, connected = live)
}

/** Developer mode: how the range was worked out. */
internal fun rangeSource(method: FuelMethod): String = when (method) {
    FuelMethod.HISTORY -> "calculated · fuel-ups"
    FuelMethod.BARS -> "rough · fuel bars"
    FuelMethod.NONE -> "no full-tank fuel-ups yet"
}

/** Null until the first cluster frame ever (the ledger shows its empty page). */
internal fun ledgerUi(bike: BikeState, fuel: FuelStats): LedgerUi? {
    val live = bike.link == LinkState.Connected && bike.cluster != null
    val r = (if (live) bike.cluster else bike.lastCluster ?: bike.cluster) ?: return null
    val bars = r.fuelBars
    val warning = when {
        bars == null -> null
        bars == 0 -> FuelWarning.EMPTY
        bars == 1 -> FuelWarning.ONE_BAR
        else -> null
    }
    val range = Formatters.approx(fuel.rangeKm?.toDouble())
    val warnText = when (warning) {
        FuelWarning.EMPTY -> "Tank reads empty. Refuel now."
        FuelWarning.ONE_BAR -> if (fuel.rangeKm != null) "One bar left · about ${fuel.rangeKm} km." else "One bar left."
        null -> null
    }
    return LedgerUi(
        odometerKm = r.odometerKm, tripAKm = r.tripAKm, tripBKm = r.tripBKm, fuelBars = bars, fuelMax = fuel.barsMax,
        gear = r.gear?.toString() ?: NO_GEAR, readingAt = r.receivedAtMillis, live = live,
        rangeText = range, rangeNote = rangeNote(fuel), rangeSource = rangeSource(fuel.method),
        fuelWarning = warning, warningText = warnText,
    )
}

/** Phone GPS speed: whole km/h, [live] while moving, else the fix time. [fixAt] is null while live (not shown). */
data class SpeedUi(val kmh: Int?, val live: Boolean, val fixAt: Long?)

internal fun speedUi(ride: RideState): SpeedUi {
    val live = ride.moving && ride.speedKmh != null
    // While live the cell shows no age: leaving the fix time out keeps a new fix at the same speed from re-emitting.
    return SpeedUi(ride.speedKmh?.roundToInt(), live, if (live) null else ride.lastFixAtMillis)
}

/** Missed calls and unread WhatsApp: shown above the ledger when either is non-zero. */
data class CountsUi(val missed: AlertCounter, val messages: AlertCounter) {
    val visible: Boolean get() = missed.count > 0 || messages.count > 0
}

/** Active navigation strip on Today. */
data class NavStripUi(
    val waiting: Boolean,
    val destination: String?,
    val iconCode: Int,
    val distanceM: Int?,
    val statusWord: String?,
    val receivedAt: Long?,
)

internal fun navStripUi(nav: NavState): NavStripUi? {
    if (!nav.active) return null
    val snap = nav.snapshot
    return NavStripUi(
        waiting = nav.waiting || snap == null,
        destination = nav.destinationLabel,
        iconCode = snap?.iconCode ?: DEFAULT_TURN_CODE,
        distanceM = snap?.distanceToTurnM,
        statusWord = snap?.status?.let(::navStatusWord),
        receivedAt = snap?.receivedAtMillis,
    )
}

/** Plain words for statuses that replace the distance (ride.js `STATUS_WORD`); null for a normal turn. */
fun navStatusWord(status: NavStatusCode): String? = when (status) {
    NavStatusCode.NO_NETWORK -> "No signal"
    NavStatusCode.RECALCULATING -> "Re-routing"
    NavStatusCode.GPS_OFF -> "GPS off"
    NavStatusCode.ARRIVED -> "Arrived"
    NavStatusCode.NO_INTERNET -> "No internet"
    NavStatusCode.NORMAL, NavStatusCode.VIA_POINT -> null
}

/** Straight-ahead arrow while no instruction has arrived. */
internal const val DEFAULT_TURN_CODE = 40
