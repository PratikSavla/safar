package app.safar.ui.nav

/**
 * Screens of the app (ux/BRIEF.md screen ids). Tabs: HOME, NAVIGATE, TRIPS, ALERTS, SETTINGS.
 * Detail routes carry their argument.
 */
sealed interface Route {
    data object Onboarding : Route
    data object Pair : Route
    data object Home : Route
    data object Ride : Route
    data object Navigate : Route
    /** Save or edit a place. [placeId] null = new; [sharedText] = text shared from Google Maps. */
    data class Place(val placeId: Long? = null, val sharedText: String? = null) : Route
    data object Alerts : Route
    data object Trips : Route
    data class Trip(val tripId: Long) : Route
    data object Fuel : Route
    /** Log or edit a fuel-up. [fuelUpId] null = new. */
    data class FuelUp(val fuelUpId: Long? = null) : Route
    data object Service : Route
    data object Parked : Route
    data object Bike : Route
    data object Log : Route
    /** Debug: send each turn icon code to the cluster (assignment task 4). */
    data object IconTest : Route
    data object Settings : Route
}

/** What every screen gets to move around. Implemented by the app shell (MainActivity). */
interface Navigator {
    fun go(route: Route)
    /** System back. At the root (HOME) it does nothing. */
    fun back()
    /** Clear the stack and show [route] as the new root (after onboarding or pairing). */
    fun resetTo(route: Route)
}
