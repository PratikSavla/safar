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
import app.safar.ui.format.Formatters
import kotlin.math.roundToInt

/** The one large connection state (docs 12.5 rule 1). */
enum class ConnKind { SEARCHING, CONNECTING, CONNECTED, RECONNECTING, IDLE, BLUETOOTH_OFF }

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
)

/** Model name for the paired or connecting bike, if known. */
internal fun modelName(bike: BikeState): String? = bike.saved?.model?.name
    ?: (bike.link as? LinkState.Connecting)?.name?.let { BikeCatalog.forBleName(it).name }

internal fun connUi(bike: BikeState): ConnUi {
    val model = modelName(bike) ?: "your bike"
    val family = bike.saved?.model?.family
        ?: (bike.link as? LinkState.Connecting)?.name?.let { BikeCatalog.forBleName(it).family }
        ?: VehicleFamily.MOTORCYCLE
    val base = ConnUi(
        ConnKind.IDLE, "Not connected", if (bike.saved != null) "$model is saved" else "No bike paired yet", "offline",
        StampTone.RUST, family, bike.saved?.name, bike.saved?.address, bike.cluster?.receivedAtMillis,
    )
    return when (val link = bike.link) {
        LinkState.Connected -> base.copy(kind = ConnKind.CONNECTED, title = "Connected", subtitle = "to the $model", stamp = "linked", tone = StampTone.MOSS)
        is LinkState.Connecting -> base.copy(kind = ConnKind.CONNECTING, title = "Connecting", subtitle = "to the $model…", stamp = "handshake", tone = StampTone.SLATE)
        is LinkState.Reconnecting -> base.copy(
            kind = ConnKind.RECONNECTING, title = "Reconnecting", subtitle = "to the $model", stamp = "retrying", tone = StampTone.AMBER,
            attempt = link.attempt, nextAttemptAt = link.nextAttemptAtMillis,
        )
        LinkState.Searching -> base.copy(kind = ConnKind.SEARCHING, title = "Searching", subtitle = "for Suzuki clusters nearby", stamp = "scanning", tone = StampTone.SLATE)
        LinkState.BluetoothOff -> base.copy(kind = ConnKind.BLUETOOTH_OFF, title = "Bluetooth is off", subtitle = "Turn it on to reach $model")
        LinkState.Idle -> base
    }
}

enum class FuelWarning { ONE_BAR, EMPTY }

/** The ledger grid: every bike value with its source. [live] false = last known before the link dropped. */
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
    val rangeSource: String,
    val fuelWarning: FuelWarning?,
    val warningText: String?,
) {
    val bikeSource: String get() = if (live) "bike" else "bike · last known"
}

internal fun rangeSource(method: FuelMethod): String = when (method) {
    FuelMethod.HISTORY -> "calculated · fuel-ups"
    FuelMethod.BARS -> "rough · bars"
    FuelMethod.NONE -> "log a full tank"
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
        gear = r.gear?.toString() ?: "–", readingAt = r.receivedAtMillis, live = live,
        rangeText = range, rangeSource = rangeSource(fuel.method), fuelWarning = warning, warningText = warnText,
    )
}

/** Phone GPS speed: whole km/h, "live" while moving, else the fix age. [fixAt] is null while live (not shown). */
data class SpeedUi(val kmh: Int?, val live: Boolean, val fixAt: Long?)

internal fun speedUi(ride: RideState): SpeedUi {
    val live = ride.moving && ride.speedKmh != null
    // While live the cell says "live": leaving the fix time out keeps a new fix at the same speed from re-emitting.
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
