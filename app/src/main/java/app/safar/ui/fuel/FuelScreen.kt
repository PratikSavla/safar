package app.safar.ui.fuel

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.safar.core.AppContainer
import app.safar.data.FuelStats
import app.safar.data.ThemeMode
import app.safar.ui.art.AtlasIcons
import app.safar.ui.components.AtlasLazyPage
import app.safar.ui.components.AtlasToastHost
import app.safar.ui.components.AtlasToastState
import app.safar.ui.components.BackTopBar
import app.safar.ui.components.EmptyState
import app.safar.ui.components.Kicker
import app.safar.ui.components.Note
import app.safar.ui.components.PrimaryButton
import app.safar.ui.components.SectionHeader
import app.safar.ui.components.SwitchRow
import app.safar.ui.components.TopoTransform
import app.safar.ui.components.rememberToastState
import app.safar.ui.copy.SwitchCopy
import app.safar.ui.fuel.preview.FuelPreviewData
import app.safar.ui.nav.Navigator
import app.safar.ui.nav.Route
import app.safar.ui.theme.SafarTheme
import app.safar.ui.vm.NoticeToasts
import java.time.ZoneId

/** Fuel page: range left, mileage full-to-full, cost and the receipts (fuel.js). */
@Composable
fun FuelScreen(navigator: Navigator) {
    val vm: FuelViewModel = viewModel {
        FuelViewModel(AppContainer.fuel, AppContainer.settings, AppContainer.bike.state)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val toast = rememberToastState()
    NoticeToasts(vm.notices, toast)
    LaunchedEffect(Unit) { FuelNotices.take()?.let(toast::show) }
    FuelContent(
        state = state,
        toast = toast,
        onBack = navigator::back,
        onLog = { FuelNotices.markOpenedFromFuel(); navigator.go(Route.FuelUp(null)) },
        onEdit = { FuelNotices.markOpenedFromFuel(); navigator.go(Route.FuelUp(it)) },
        onLowFuelAlert = vm::setLowFuelAlert,
    )
}

/** Stateless fuel page. */
@Composable
fun FuelContent(
    state: FuelUiState,
    toast: AtlasToastState,
    onBack: () -> Unit,
    onLog: () -> Unit,
    onEdit: (Long) -> Unit,
    onLowFuelAlert: (Boolean) -> Unit,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    AtlasLazyPage(topo = TopoTransform.Quiet, overlay = { AtlasToastHost(toast, bottomPadding = 40.dp) }) {
        item(key = "top", contentType = "top") {
            Column {
                BackTopBar("Fuel · mileage and cost", "The tank", onBack, titleEm = ", by the litre.")
                PrimaryButton("Log fuel", onLog, Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(bottom = 18.dp), icon = AtlasIcons.fuel)
            }
        }
        val stats = state.stats ?: return@AtlasLazyPage
        item(key = "stats", contentType = "stats") { FuelStatsBlock(state, stats, zone, onLog) }
        items(state.rows, key = { it.id }, contentType = { "fuelUp" }) { row -> FuelUpRow(row, state.currency) { onEdit(row.id) } }
        item(key = "alert", contentType = "alert") { FuelAlertBlock(state.lowFuelAlert, onLowFuelAlert) }
    }
}

/** Range, mileage and cost, then the head of the receipts list. */
@Composable
private fun FuelStatsBlock(state: FuelUiState, stats: FuelStats, zone: ZoneId, onLog: () -> Unit) {
    Column {
        RangeHero(stats, state.reading)
        SectionHeader("Mileage") { Kicker("full tank to full tank") }
        MileageSection(stats, zone)
        Note("Mileage is counted only between two full tanks. Top-ups in between are added to the next full one.", Modifier.padding(top = 12.dp))
        SectionHeader("Cost")
        CostCard(stats, state.currency)
        SectionHeader("Fuel-ups") { Kicker("${state.rows.size} logged · tap to edit") }
        if (state.rows.isEmpty()) {
            EmptyState("No fuel-ups yet.", message = "Log a full tank at the pump to start tracking mileage.", actionLabel = "Log fuel", onAction = onLog)
        }
    }
}

@Composable
private fun FuelAlertBlock(on: Boolean, onLowFuelAlert: (Boolean) -> Unit) {
    Column {
        SectionHeader("Reminder")
        SwitchRow("Low-fuel reminder", on, onLowFuelAlert, subtitle = SwitchCopy.LOW_FUEL)
        Note(
            "Your bike shows fuel only as bars, so litres and prices come from your fuel-ups. The odometer comes from your bike.",
            Modifier.padding(top = 12.dp),
        )
    }
}

@Preview
@Composable
private fun FuelPreview() = SafarTheme(ThemeMode.LIGHT) {
    FuelContent(FuelPreviewData.page, rememberToastState(), {}, {}, {}, {})
}

@Preview
@Composable
private fun FuelDarkPreview() = SafarTheme(ThemeMode.DARK) {
    FuelContent(FuelPreviewData.page, rememberToastState(), {}, {}, {}, {})
}

@Preview
@Composable
private fun FuelEmptyPreview() = SafarTheme(ThemeMode.LIGHT) {
    FuelContent(FuelPreviewData.empty, rememberToastState(), {}, {}, {}, {})
}
