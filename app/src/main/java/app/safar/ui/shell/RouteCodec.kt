package app.safar.ui.shell

import app.safar.ui.nav.Route

/**
 * Turns a [Route] into one string and back, so the back stack survives process death in a Bundle.
 * Format: `name` or `name|arg|arg`. Free text (a shared Google Maps message) is the last field, so it may
 * contain the separator. Null arguments are empty fields; free text gets a one-character presence flag.
 */
internal object RouteCodec {
    private const val SEP = '|'

    private val plain: Map<String, Route> = listOf(
        Route.Onboarding, Route.Pair, Route.Home, Route.Ride, Route.Navigate, Route.Alerts, Route.Trips,
        Route.Fuel, Route.Service, Route.Parked, Route.Bike, Route.Log, Route.IconTest, Route.Settings,
        Route.Permissions, Route.Documents,
    ).associateBy { nameOf(it) }

    fun encode(route: Route): String = when (route) {
        is Route.Place -> listOf("place", route.placeId?.toString().orEmpty(), textField(route.sharedText)).joinToString(SEP.toString())
        is Route.Trip -> "trip$SEP${route.tripId}"
        is Route.FuelUp -> "fuelup$SEP${route.fuelUpId?.toString().orEmpty()}"
        is Route.Document -> "document$SEP${route.docId?.toString().orEmpty()}"
        else -> nameOf(route)
    }

    /** Null for anything this version cannot read (an older or newer saved stack). */
    fun decode(text: String): Route? {
        val parts = text.split(SEP, limit = 3)
        return when (parts[0]) {
            "place" -> if (parts.size == 3) Route.Place(parts[1].toLongOrNull(), readText(parts[2])) else null
            "trip" -> parts.getOrNull(1)?.toLongOrNull()?.let { Route.Trip(it) }
            "fuelup" -> if (parts.size >= 2) Route.FuelUp(parts[1].toLongOrNull()) else null
            "document" -> if (parts.size >= 2) Route.Document(parts[1].toLongOrNull()) else null
            else -> plain[parts[0]]
        }
    }

    private fun textField(text: String?): String = if (text == null) "0" else "1$text"

    private fun readText(field: String): String? = if (field.startsWith("1")) field.substring(1) else null

    private fun nameOf(route: Route): String = when (route) {
        Route.Onboarding -> "onboarding"
        Route.Pair -> "pair"
        Route.Home -> "home"
        Route.Ride -> "ride"
        Route.Navigate -> "navigate"
        Route.Alerts -> "alerts"
        Route.Trips -> "trips"
        Route.Fuel -> "fuel"
        Route.Service -> "service"
        Route.Parked -> "parked"
        Route.Bike -> "bike"
        Route.Log -> "log"
        Route.IconTest -> "icontest"
        Route.Settings -> "settings"
        Route.Permissions -> "permissions"
        Route.Documents -> "documents"
        is Route.Place -> "place"
        is Route.Trip -> "trip"
        is Route.FuelUp -> "fuelup"
        is Route.Document -> "document"
    }
}
