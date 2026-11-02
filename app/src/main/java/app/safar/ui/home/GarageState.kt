package app.safar.ui.home

import app.safar.core.LinkState
import app.safar.data.FuelStats
import app.safar.data.ParkedSpot
import app.safar.data.ServiceItem
import app.safar.data.ServiceState
import app.safar.data.Trip
import app.safar.data.serviceStatuses
import app.safar.ui.format.Formatters
import app.safar.ui.nav.Route

/** One garage tile: a feature that is not a bottom tab, one tap away, with its live status. */
data class GarageTile(
    val key: String,
    val name: String,
    val hint: String,
    val route: Route,
    val warn: Boolean = false,
    val accent: Boolean = false,
    /** When set, the hint is "<age>" of this time (kept live by the screen) instead of [hint]. */
    val hintAtMillis: Long? = null,
)

/** Everything the garage tiles show, from data the screen already has. */
data class GarageInputs(
    val fuel: FuelStats,
    val odometerKm: Int?,
    val services: List<ServiceItem>,
    val parked: ParkedSpot?,
    val trips: List<Trip>,
    val placesCount: Int,
    val nav: NavStripUi?,
    val alertCount: Int,
    val link: LinkState,
    val debugLog: Boolean,
    val logCount: Int,
)

/** Fuel, Log fuel, Service / Parked, Trips, Navigate / Alerts, Bike, Debug log (home.js `garage`). */
internal fun garageTiles(g: GarageInputs): List<GarageTile> = listOf(
    GarageTile("fuel", "Fuel", fuelHint(g.fuel), Route.Fuel, warn = (g.fuel.bars ?: Int.MAX_VALUE) <= 1),
    GarageTile("logfuel", "Log fuel", "at the pump", Route.FuelUp(), accent = true),
    serviceTile(g.services, g.odometerKm),
    GarageTile("parked", "Parked", if (g.parked == null) "nothing saved" else "", Route.Parked, hintAtMillis = g.parked?.atMillis),
    GarageTile("trips", "Trips", tripsHint(g.trips), Route.Trips),
    GarageTile("navigate", "Navigate", navHint(g.nav, g.placesCount), Route.Navigate),
    GarageTile("alerts", "Alerts", if (g.alertCount > 0) "${g.alertCount} new" else "all quiet", Route.Alerts, warn = g.alertCount > 0),
    GarageTile("bike", "Bike", if (g.link == LinkState.Connected) "firmware, serial" else "saved info", Route.Bike),
    GarageTile("log", "Debug log", if (g.debugLog) "recording" else "${g.logCount} in memory", Route.Log),
)

private fun fuelHint(f: FuelStats): String = if (f.rangeKm == null) "log a full tank" else "${Formatters.approx(f.rangeKm.toDouble())} km left"

private fun serviceTile(items: List<ServiceItem>, odo: Int?): GarageTile {
    if (items.isEmpty() || odo == null) return GarageTile("service", "Service", if (items.isEmpty()) "not set up" else "needs the odometer", Route.Service)
    val statuses = serviceStatuses(items, odo)
    val overdue = statuses.count { it.state == ServiceState.OVERDUE }
    val next = statuses.first()
    val hint = if (overdue > 0) "$overdue overdue" else "${next.item.label.substringBefore(' ')} in ${Formatters.odometer(next.kmToNext)} km"
    return GarageTile("service", "Service", hint, Route.Service, warn = overdue > 0)
}

private fun tripsHint(trips: List<Trip>): String {
    val last = trips.firstOrNull() ?: return "no rides yet"
    return "last ${Formatters.km1(last.distanceKm)} km"
}

private fun navHint(nav: NavStripUi?, places: Int): String = when {
    nav == null -> if (places == 1) "1 place" else "$places places"
    nav.waiting -> "waiting for Maps"
    nav.statusWord != null -> nav.statusWord.lowercase()
    nav.distanceM != null -> "live · ${Formatters.navDistance(nav.distanceM)}"
    else -> "live"
}
