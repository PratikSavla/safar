package app.safar.ui.fuel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.safar.core.AppContainer
import app.safar.data.ThemeMode
import app.safar.ui.art.AtlasIcons
import app.safar.ui.components.AtlasPage
import app.safar.ui.components.AtlasToastHost
import app.safar.ui.components.AtlasToastState
import app.safar.ui.components.BackTopBar
import app.safar.ui.components.EmptyState
import app.safar.ui.components.PrimaryButton
import app.safar.ui.components.SecondaryButton
import app.safar.ui.components.SwitchRow
import app.safar.ui.components.TextAction
import app.safar.ui.components.TopoTransform
import app.safar.ui.components.rememberToastState
import app.safar.ui.fuel.preview.FuelPreviewData
import app.safar.ui.nav.Navigator
import app.safar.ui.theme.Atlas
import app.safar.ui.theme.SafarTheme
import app.safar.ui.vm.NoticeToasts
import java.time.ZoneId

/** Form actions, bundled so the stateless content stays readable. */
data class FuelUpActions(
    val onLitres: (String) -> Unit = {},
    val onTotal: (String) -> Unit = {},
    val onOdo: (String) -> Unit = {},
    val onEditOdo: () -> Unit = {},
    val onFull: (Boolean) -> Unit = {},
    val onSave: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onClose: () -> Unit = {},
)

/** Log fuel, or edit / delete a fuel-up (fuel-form.js). [fuelUpId] null = a new receipt. */
@Composable
fun FuelUpScreen(navigator: Navigator, fuelUpId: Long?) {
    val vm: FuelUpViewModel = viewModel(key = "fuel-up-${fuelUpId ?: "new"}") {
        FuelUpViewModel(fuelUpId, AppContainer.fuel, AppContainer.settings, AppContainer.bike.state, AppContainer.ride.state)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val done by vm.done.collectAsStateWithLifecycle()
    val toast = rememberToastState()
    NoticeToasts(vm.notices, toast)
    LaunchedEffect(done) {
        val message = done ?: return@LaunchedEffect
        FuelNotices.post(message)
        navigator.back()
    }
    val actions = FuelUpActions(
        vm::setLitres, vm::setTotal, vm::setOdo, vm::editOdo, vm::setFull, vm::save, vm::delete, navigator::back,
    )
    FuelUpContent(state, toast, actions)
}

/** Stateless form page. While the phone moves it shows "Stop to log fuel." instead of the fields. */
@Composable
fun FuelUpContent(state: FuelUpUiState, toast: AtlasToastState, actions: FuelUpActions) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    val entry = state.entry
    AtlasPage(
        topo = TopoTransform.Quiet,
        overlay = {
            if (entry != null) DeleteFuelUpSheet(confirm, entry, state.currency, { confirm = false }) { confirm = false; actions.onDelete() }
            AtlasToastHost(toast, bottomPadding = 40.dp)
        },
    ) {
        val kicker = if (entry != null) "Edit fuel-up · ${fuelDate(entry.atMillis, ZoneId.systemDefault())}" else "Log fuel · today"
        BackTopBar(kicker, "At the pump", actions.onClose, titleEm = if (entry != null) ", corrected." else ", one receipt.")
        when {
            state.loading -> Unit
            state.missing -> EmptyState("This fuel-up is gone.", message = "It may have been deleted.", actionLabel = "Back", onAction = actions.onClose)
            state.locked != null -> LockedForm(state.speedKmh, actions.onClose)
            else -> FormBody(state, actions, onAskDelete = { confirm = true })
        }
    }
}

@Composable
private fun FormBody(state: FuelUpUiState, actions: FuelUpActions, onAskDelete: () -> Unit) {
    AmountFields(state, actions)
    OdometerBlock(state, actions)
    SwitchRow(
        "Full tank", state.form.full, actions.onFull,
        subtitle = "Filled to the top. Mileage is counted only between two full tanks.",
        modifier = Modifier.padding(top = 8.dp),
    )
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SecondaryButton("Cancel", actions.onClose, Modifier.weight(1f))
        PrimaryButton("Save", actions.onSave, Modifier.weight(1f), icon = AtlasIcons.check, enabled = state.canSave)
    }
    if (state.entry != null) {
        TextAction("Delete this fuel-up", onAskDelete, Modifier.padding(top = 10.dp), icon = AtlasIcons.cross, color = Atlas.colors.rust)
    }
}

@Preview
@Composable
private fun FuelUpPreview() = SafarTheme(ThemeMode.LIGHT) {
    FuelUpContent(FuelPreviewData.form, rememberToastState(), FuelUpActions())
}

@Preview
@Composable
private fun FuelUpErrorsPreview() = SafarTheme(ThemeMode.DARK) {
    FuelUpContent(FuelPreviewData.formErrors, rememberToastState(), FuelUpActions())
}

@Preview
@Composable
private fun FuelUpLockedPreview() = SafarTheme(ThemeMode.LIGHT) {
    FuelUpContent(FuelPreviewData.formLocked, rememberToastState(), FuelUpActions())
}
