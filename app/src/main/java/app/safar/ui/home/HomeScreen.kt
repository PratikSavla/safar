package app.safar.ui.home

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.safar.ui.components.AtlasPage
import app.safar.ui.components.Kicker
import app.safar.ui.components.Masthead
import app.safar.ui.components.SectionHeader
import app.safar.ui.components.rememberNow
import app.safar.ui.home.preview.HomePreviewData
import app.safar.ui.nav.Navigator
import app.safar.ui.nav.Route
import app.safar.ui.pair.rememberBtFixes
import app.safar.ui.shell.TabBarSpace
import app.safar.ui.theme.SafarTheme
import app.safar.data.ThemeMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_LINE = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", Locale.UK)

/** Today (home.js): connection, live navigation and alert counts, the ledger, the garage. */
@Composable
fun HomeScreen(navigator: Navigator, vm: HomeViewModel = viewModel { HomeViewModel.fromContainer() }) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val speed = vm.speed.collectAsStateWithLifecycle()
    val fixes = rememberBtFixes(onChanged = {})
    val actions = remember(navigator, vm, fixes) {
        ConnActions(
            openRide = { navigator.go(Route.Ride) },
            connectSaved = vm::connectSaved,
            openPair = { navigator.go(Route.Pair) },
            enableBluetooth = fixes.enableBluetooth,
        )
    }
    val missing by rememberMissingPermissions()
    val permissions = ui?.let { permissionsCardUi(missing, it.dismissedPermissions) }
    val dismiss = { vm.dismissPermissions(missing.mapTo(HashSet()) { it.name }) }
    HomeContent(ui, { speed.value }, actions, navigator::go) {
        permissions?.let { PermissionsCard(it, { navigator.go(Route.Permissions) }, dismiss) }
    }
}

/** [notice] sits above the connection card: the permissions card, when something is off. */
@Composable
internal fun HomeContent(ui: HomeUi?, speed: () -> SpeedUi, actions: ConnActions, go: (Route) -> Unit, notice: @Composable () -> Unit = {}) {
    AtlasPage(bottomBar = TabBarSpace) {
        TodayMasthead(ui?.riderName?.ifEmpty { null } ?: "rider", ui?.rideCount)
        if (ui == null) return@AtlasPage
        notice()
        ConnectionCard(ui.conn, ui.autoRideOn, actions)
        NavStrip(ui.nav) { go(Route.Navigate) }
        CountsStrip(ui.counts) { go(Route.Alerts) }
        SectionHeader("Readings", Modifier.padding(top = 0.dp)) { ui.modelName?.let { Kicker(it) } }
        Ledger(ui.ledger, speed)
        SectionHeader("Garage")
        Garage(ui.garage, go)
    }
}

/** "MON, 03 NOV 2026 · 12 rides" over "Today, Alex.": the only part of the page the minute clock recomposes. */
@Composable
private fun TodayMasthead(name: String, rideCount: Int?) {
    val now by rememberNow(periodMillis = 60_000)
    val dateLine = DATE_LINE.format(Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())).uppercase(Locale.UK)
    Masthead(dateLine, "Today", kickerAside = rideCount?.takeIf { it > 0 }?.let(::ridesLabel), titleEm = ", $name.")
}

/** "1 ride", "12 rides". */
internal fun ridesLabel(count: Int): String = if (count == 1) "1 ride" else "$count rides"

@Preview(heightDp = 1400)
@Composable
private fun HomePreview() = SafarTheme(ThemeMode.LIGHT) {
    HomeContent(HomePreviewData.connected(), { HomePreviewData.speed }, HomePreviewData.noActions, {})
}

@Preview(heightDp = 1400)
@Composable
private fun HomeReconnectingDarkPreview() = SafarTheme(ThemeMode.DARK) {
    HomeContent(HomePreviewData.reconnecting(), { HomePreviewData.speed }, HomePreviewData.noActions, {})
}
