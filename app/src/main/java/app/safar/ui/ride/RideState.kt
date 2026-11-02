package app.safar.ui.ride

import app.safar.core.AlertSource
import app.safar.core.AlertsState
import app.safar.core.BikeState
import app.safar.core.LinkState
import app.safar.core.NavState
import app.safar.core.NavStatusCode
import app.safar.core.RideState
import app.safar.data.Settings
import app.safar.ui.format.Formatters
import app.safar.ui.home.DEFAULT_TURN_CODE
import app.safar.ui.home.navStatusWord
import app.safar.ui.shell.callForwarded
import kotlin.math.roundToInt

/** The next-turn block (ride.js `turnBlock`). */
sealed interface TurnUi {
    /** Maps opened from Safar, no notification yet. */
    data object Waiting : TurnUi

    /** A status that replaces the distance: re-routing, GPS off, arrived... [code] is the icon to draw. */
    data class Status(val word: String, val code: Int, val arrived: Boolean) : TurnUi

    data class Turn(val code: Int, val value: String, val unit: String) : TurnUi
}

/** The call banner while riding: caller, and whether the cluster shows it. */
data class RideCallUi(val name: String, val source: AlertSource, val label: String, val answered: Boolean)

/** Everything on the ride screen. Few elements, all large (docs 12.5 rule 2). */
data class RideUi(
    val speedKmh: Int?,
    val overLimit: Boolean,
    val gear: String,
    val fuelBars: Int?,
    val fuelMax: Int,
    /** Gear and fuel come from a live cluster frame; false = the last frame before the link dropped. */
    val bikeLive: Boolean,
    /** When the cluster frame behind gear and fuel arrived; null before the first one. */
    val readingAt: Long?,
    val turn: TurnUi?,
    val call: RideCallUi?,
    /** "bike link · retrying" / "bike not connected", or null while linked. */
    val linkNote: String?,
    val moving: Boolean,
) {
    /** Source caption for gear and fuel, worded like the home ledger ("waiting" age before any frame). */
    val bikeSource: String get() = if (bikeLive || readingAt == null) "bike" else "bike · last known"
}

private const val ARRIVED_CODE = 9
private const val STATUS_CODE = 46
private const val DEFAULT_FUEL_BARS = 6

internal fun rideUi(bike: BikeState, ride: RideState, nav: NavState, alerts: AlertsState, settings: Settings): RideUi {
    val live = bike.link == LinkState.Connected && bike.cluster != null
    val reading = if (live) bike.cluster else bike.lastCluster ?: bike.cluster
    val speed = ride.speedKmh?.roundToInt()
    return RideUi(
        speedKmh = speed,
        overLimit = settings.speedAlertOn && speed != null && speed > settings.speedAlertKmh,
        gear = reading?.gear?.toString() ?: "–",
        fuelBars = reading?.fuelBars,
        fuelMax = bike.saved?.model?.fuelBars ?: DEFAULT_FUEL_BARS,
        bikeLive = live,
        readingAt = reading?.receivedAtMillis,
        turn = turnUi(nav),
        call = alerts.call?.let { c ->
            val label = when {
                c.answered -> "on call"
                callForwarded(c.source, bike.link, settings) -> "on cluster"
                else -> "ringing"
            }
            RideCallUi(c.name, c.source, label, c.answered)
        },
        linkNote = when (bike.link) {
            LinkState.Connected -> null
            is LinkState.Reconnecting -> "bike link · retrying"
            else -> "bike not connected"
        },
        moving = ride.moving,
    )
}

internal fun turnUi(nav: NavState): TurnUi? {
    if (!nav.active) return null
    val snap = nav.snapshot
    if (nav.waiting || snap == null) return TurnUi.Waiting
    val word = navStatusWord(snap.status)
    if (word != null) {
        val arrived = snap.status == NavStatusCode.ARRIVED
        return TurnUi.Status(word, if (arrived) ARRIVED_CODE else STATUS_CODE, arrived)
    }
    val d = Formatters.navDistance(snap.distanceToTurnM)
    return TurnUi.Turn(snap.iconCode.takeIf { it in 1..STATUS_CODE } ?: DEFAULT_TURN_CODE, d.value, d.unit)
}
