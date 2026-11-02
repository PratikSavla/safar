package app.safar.ui.service

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
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
import app.safar.ui.components.Kicker
import app.safar.ui.components.NumberWithUnit
import app.safar.ui.components.SectionHeader
import app.safar.ui.components.SourceLine
import app.safar.ui.components.SwitchRow
import app.safar.ui.components.TextAction
import app.safar.ui.components.TopoTransform
import app.safar.ui.components.rememberToastState
import app.safar.ui.format.Formatters
import app.safar.ui.nav.Navigator
import app.safar.ui.service.preview.ServicePreviewData
import app.safar.ui.theme.Atlas
import app.safar.ui.theme.SafarTheme
import app.safar.ui.vm.NoticeToasts

/** Logbook actions, bundled for the stateless content. */
data class ServiceActions(
    val onBack: () -> Unit = {},
    val onDone: (Long) -> Unit = {},
    val onInterval: (Long, Int) -> Unit = { _, _ -> },
    val onDelete: (Long) -> Unit = {},
    val onAdd: (String, Int) -> Unit = { _, _ -> },
    val onReminders: (Boolean) -> Unit = {},
)

/** Service reminders by odometer (service.js). */
@Composable
fun ServiceScreen(navigator: Navigator) {
    val vm: ServiceViewModel = viewModel {
        ServiceViewModel(AppContainer.service, AppContainer.settings, AppContainer.bike.state, AppContainer.ride.state)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val toast = rememberToastState()
    NoticeToasts(vm.notices, toast)
    ServiceContent(state, toast, ServiceActions(navigator::back, vm::markDone, vm::setInterval, vm::delete, vm::add, vm::setReminders))
}

/** Stateless logbook page. */
@Composable
fun ServiceContent(state: ServiceUiState, toast: AtlasToastState, actions: ServiceActions) {
    var editing by rememberSaveable { mutableStateOf<Long?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    val editItem = state.rows.firstOrNull { it.status.item.id == editing }?.status?.item
    AtlasPage(
        topo = TopoTransform.Quiet,
        overlay = {
            IntervalSheet(
                editItem, { editing = null },
                onSave = { km -> editItem?.let { actions.onInterval(it.id, km) }; editing = null },
                onDelete = { editItem?.let { actions.onDelete(it.id) }; editing = null },
            )
            AddItemSheet(adding, state.odo.km, state.lockReason, { adding = false }) { label, km -> actions.onAdd(label, km); adding = false }
            AtlasToastHost(toast, bottomPadding = 40.dp)
        },
    ) {
        BackTopBar("Service · by odometer", "The logbook", actions.onBack, titleEm = titleTail(state.needYou))
        OdometerNow(state.odo)
        if (state.rows.isEmpty() && !state.loading) {
            EmptyState("No service items yet.", Modifier.padding(top = 18.dp), message = "Pair the bike to start the default list, or add your own.")
        }
        Column {
            state.rows.forEach { row ->
                val id = row.status.item.id
                ServiceItemCard(row, onDone = { actions.onDone(id) }, onInterval = { editing = id })
            }
        }
        TextAction("Add a custom item", { adding = true }, Modifier.padding(top = 14.dp), icon = AtlasIcons.plus)
        SectionHeader("Reminders")
        SwitchRow("Service reminders", state.reminders, actions.onReminders, subtitle = "A notification when an item is due soon or overdue")
    }
}

/** "Odometer now 12,301 km" with its source (`.odo-now`). */
@Composable
private fun OdometerNow(odo: OdoNowUi) {
    val c = Atlas.colors
    Column(
        Modifier.fillMaxWidth()
            .drawBehind { drawLine(c.ink, Offset(0f, size.height), Offset(size.width, size.height), 1.5.dp.toPx()) }
            .padding(bottom = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Kicker("Odometer now", Modifier.padding(bottom = 6.dp))
            NumberWithUnit(odo.km?.let(Formatters::odometer) ?: "—", "km", Atlas.type.numberSmall.copy(fontSize = Atlas.type.numberSmall.fontSize * 1.12f))
        }
        if (odo.atMillis != null) SourceLine(odo.source, odo.atMillis) else SourceLine(odo.source)
    }
}

@Preview
@Composable
private fun ServicePreview() = SafarTheme(ThemeMode.LIGHT) {
    ServiceContent(ServicePreviewData.state, rememberToastState(), ServiceActions())
}

@Preview
@Composable
private fun ServiceDarkPreview() = SafarTheme(ThemeMode.DARK) {
    ServiceContent(ServicePreviewData.state, rememberToastState(), ServiceActions())
}
