package app.safar.ui.shell

import androidx.compose.runtime.Composable
import app.safar.ui.nav.Navigator
import app.safar.ui.nav.Route

/** The router: one line per route. Replace a `PendingScreen` line with the real screen when it lands. */
@Composable
fun RouteHost(route: Route, navigator: Navigator) {
    when (route) {
        Route.Onboarding -> PendingScreen(navigator, "Welcome", "Safar")
        Route.Pair -> PendingScreen(navigator, "Pairing", "Find your bike")
        Route.Home -> PendingScreen(navigator, "Today", "Today", tab = true)
        Route.Ride -> PendingScreen(navigator, "Ride", "Ride mode")
        Route.Navigate -> PendingScreen(navigator, "Route · via Google Maps", "Where to?", tab = true)
        is Route.Place -> PendingScreen(navigator, "Route", "Save a place")
        Route.Alerts -> PendingScreen(navigator, "Alerts · sent to the cluster", "Alerts", tab = true)
        Route.Trips -> PendingScreen(navigator, "Journal", "Trips", tab = true)
        is Route.Trip -> PendingScreen(navigator, "Journal", "Trip")
        Route.Fuel -> PendingScreen(navigator, "Garage", "Fuel")
        is Route.FuelUp -> PendingScreen(navigator, "Fuel", "Log fuel")
        Route.Service -> PendingScreen(navigator, "Garage", "Service")
        Route.Parked -> PendingScreen(navigator, "Garage", "Parked")
        Route.Bike -> PendingScreen(navigator, "Garage", "Bike")
        Route.Log -> PendingScreen(navigator, "Debug", "Debug log")
        Route.IconTest -> PendingScreen(navigator, "Debug", "Icon test")
        Route.Settings -> PendingScreen(navigator, "Settings", "Settings", tab = true)
    }
}
