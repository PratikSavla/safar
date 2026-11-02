package app.safar.ui.navigate

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.safar.core.NavSnapshot
import app.safar.core.NavState
import app.safar.core.NavStatusCode
import app.safar.data.ParkedSpot
import app.safar.data.Place
import app.safar.data.PlaceKind
import app.safar.data.PlaceSource
import app.safar.data.ThemeMode
import app.safar.ui.components.rememberToastState
import app.safar.ui.settings.AccessStatus
import app.safar.ui.theme.SafarTheme

// Synthetic preview data only.
private const val NOW = 1_760_000_000_000L

internal val PreviewPlaces = listOf(
    Place(1, "Home", "Main Rd, Sample Town", "Main Rd, Sample Town", PlaceKind.HOME, PlaceSource.MAPS, 0),
    Place(2, "Fuel stop", "NH 48 · km 112", "NH 48 fuel station", PlaceKind.FUEL, PlaceSource.TYPED, 1),
)

private fun pickState(editing: Boolean = false): NavigateState {
    val dests = destinations(PreviewPlaces, ParkedSpot(12.9, 77.6, 8f, NOW - 600_000, "Main Rd"))
    return NavigateState(destinations = dests, selected = dests.first(), editing = editing)
}

private fun activeState(status: NavStatusCode): NavigateState {
    val snap = NavSnapshot(1, true, 850, NOW + 1_200_000, 11_200, status, "Turn left onto Main Rd", NOW - 2_000)
    val nav = NavState(active = true, destinationLabel = "Home", snapshot = snap)
    return NavigateState(phase = RoutePhase.ACTIVE, nav = nav, mirror = mirrorOf(snap), bikeConnected = true)
}

@Composable
private fun Frame(state: NavigateState, dark: Boolean = false, access: AccessStatus = AccessStatus(true, true)) =
    SafarTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
        NavigateContent(state, access, NavigateActions(), rememberToastState())
    }

@Preview(heightDp = 1400)
@Composable
private fun RoutePickPreview() = Frame(pickState(), access = AccessStatus(notificationAccess = false, mapsInstalled = true))

@Preview(heightDp = 1200)
@Composable
private fun RouteEditPreview() = Frame(pickState(editing = true), dark = true)

@Preview(heightDp = 1500)
@Composable
private fun RouteActivePreview() = Frame(activeState(NavStatusCode.NORMAL))

@Preview(heightDp = 1500, fontScale = 1.5f)
@Composable
private fun RouteGpsOffPreview() = Frame(activeState(NavStatusCode.GPS_OFF).copy(bikeConnected = false), dark = true)

@Preview(heightDp = 900)
@Composable
private fun RouteWaitingPreview() = Frame(NavigateState(phase = RoutePhase.WAITING_FIRST_UPDATE, nav = NavState(waiting = true, destinationLabel = "Home")))

@Preview(heightDp = 1000)
@Composable
private fun PlaceChooserPreview() = SafarTheme(ThemeMode.LIGHT) {
    PlaceContent(PlaceState(lockReason = "Locked while moving (42 km/h). Stop to type."), PlaceActions(), rememberToastState())
}

@Preview(heightDp = 1200)
@Composable
private fun PlaceSharedPreview() = SafarTheme(ThemeMode.DARK) {
    val draft = draftFromShare("Lake View Point\nMain Rd, Sample Town\nhttps://maps.app.goo.gl/AbCdEf123")
    PlaceContent(PlaceState(draft = draft), PlaceActions(), rememberToastState())
}

@Preview(heightDp = 1200, fontScale = 1.4f)
@Composable
private fun PlaceEditPreview() = SafarTheme(ThemeMode.LIGHT) {
    PlaceContent(PlaceState(editing = true, draft = draftFromPlace(PreviewPlaces[1]), confirmDelete = true), PlaceActions(), rememberToastState())
}
