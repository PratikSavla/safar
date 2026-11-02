package app.safar.ui.shell

import androidx.compose.runtime.Composable
import app.safar.ui.alerts.AlertsScreen
import app.safar.ui.bike.BikeScreen
import app.safar.ui.fuel.FuelScreen
import app.safar.ui.fuel.FuelUpScreen
import app.safar.ui.home.HomeScreen
import app.safar.ui.icontest.IconTestScreen
import app.safar.ui.log.LogScreen
import app.safar.ui.nav.Navigator
import app.safar.ui.nav.Route
import app.safar.ui.navigate.NavigateScreen
import app.safar.ui.navigate.PlaceScreen
import app.safar.ui.onboarding.OnboardingScreen
import app.safar.ui.pair.PairScreen
import app.safar.ui.parked.ParkedScreen
import app.safar.ui.ride.RideScreen
import app.safar.ui.service.ServiceScreen
import app.safar.ui.settings.SettingsScreen
import app.safar.ui.trips.TripScreen
import app.safar.ui.trips.TripsScreen

/** The router: one line per route. Tab sections reserve room for the shell's stub bar ([TabBarSpace]). */
@Composable
fun RouteHost(route: Route, navigator: Navigator) {
    when (route) {
        Route.Onboarding -> OnboardingScreen(navigator)
        Route.Pair -> PairScreen(navigator)
        Route.Home -> HomeScreen(navigator)
        Route.Ride -> RideScreen(navigator)
        Route.Navigate -> NavigateScreen(navigator, bottomBar = TabBarSpace)
        is Route.Place -> PlaceScreen(navigator, route.placeId, route.sharedText)
        Route.Alerts -> AlertsScreen(navigator, bottomBar = TabBarSpace)
        Route.Trips -> TripsScreen(navigator, bottomBar = TabBarSpace)
        is Route.Trip -> TripScreen(navigator, route.tripId)
        Route.Fuel -> FuelScreen(navigator)
        is Route.FuelUp -> FuelUpScreen(navigator, route.fuelUpId)
        Route.Service -> ServiceScreen(navigator)
        Route.Parked -> ParkedScreen(navigator)
        Route.Bike -> BikeScreen(navigator)
        Route.Log -> LogScreen(navigator)
        Route.IconTest -> IconTestScreen(navigator)
        Route.Settings -> SettingsScreen(navigator, bottomBar = TabBarSpace)
    }
}
