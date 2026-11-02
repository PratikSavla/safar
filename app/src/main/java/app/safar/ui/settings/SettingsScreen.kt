package app.safar.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.safar.core.AppContainer
import app.safar.data.Settings
import app.safar.data.ThemeMode
import app.safar.ui.alerts.NotificationAccessRow
import app.safar.ui.art.AtlasIcons
import app.safar.ui.components.AtlasPage
import app.safar.ui.components.LinkRow
import app.safar.ui.components.ListRow
import app.safar.ui.components.Masthead
import app.safar.ui.components.SectionHeader
import app.safar.ui.components.SegmentedChoice
import app.safar.ui.components.Stamp
import app.safar.ui.components.StampTone
import app.safar.ui.components.SwitchRow
import app.safar.ui.components.TopoTransform
import app.safar.ui.nav.Navigator
import app.safar.ui.nav.Route

class SettingsActions(
    val onName: (String) -> Unit = {},
    val onTank: (String) -> Unit = {},
    val onTankReset: () -> Unit = {},
    val onSpeed: (Int) -> Unit = {},
    /** A plain switch: a name for the log and the change. */
    val onSettings: (String, (Settings) -> Settings) -> Unit = { _, _ -> },
    val onTheme: (ThemeMode) -> Unit = {},
    val onAccess: () -> Unit = {},
    val onOpen: (Route) -> Unit = {},
)

/**
 * Settings tab (ux settings.js). Every alert and feature is switchable; text fields lock while moving.
 * [bottomBar] is the shell's tab strip.
 */
@Composable
fun SettingsScreen(navigator: Navigator, modifier: Modifier = Modifier, bottomBar: (@Composable () -> Unit)? = null) {
    val vm = viewModel { SettingsViewModel(AppContainer.settings, AppContainer.ride, AppContainer.bike, AppContainer.savedBike) }
    val state by vm.state.collectAsStateWithLifecycle()
    val access by rememberAccessStatus()
    val context = LocalContext.current
    val actions = remember(vm, navigator, context) {
        SettingsActions(
            onName = vm::setRiderName,
            onTank = vm::setTank,
            onTankReset = vm::resetTank,
            onSpeed = vm::setSpeedAlert,
            onSettings = vm::toggle,
            onTheme = vm::setTheme,
            onAccess = { SystemAccess.openNotificationAccess(context) },
            onOpen = navigator::go,
        )
    }
    SettingsContent(state, access, actions, modifier, bottomBar)
}

@Composable
internal fun SettingsContent(
    state: SettingsState,
    access: AccessStatus,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    bottomBar: (@Composable () -> Unit)? = null,
) {
    AtlasPage(modifier, topo = TopoTransform.Quiet, bottomBar = bottomBar) {
        Masthead("Settings", "Margins", kickerAside = "saved on this phone", titleEm = " & notes.")
        SectionHeader("Rider")
        RiderBlock(state, actions.onName)
        SectionHeader("Units")
        ListRow("Kilometres", subtitle = "km and km/h, as the cluster shows them. Miles are not in version 1.", divider = false) {
            Stamp("km only", tone = StampTone.INK)
        }
        SectionHeader("Speed")
        SpeedBlock(state, actions.onSettings, actions.onSpeed)
        AlertSection(state.settings, actions.onSettings)
        RidingSection(state.settings, actions.onSettings)
        SectionHeader("Fuel")
        TankBlock(state, actions.onTank, actions.onTankReset)
        SwitchRow("Low-fuel reminder", state.settings.lowFuelAlert, { v -> actions.onSettings("low fuel") { it.copy(lowFuelAlert = v) } }, subtitle = "At 1 bar", divider = false)
        SectionHeader("Theme")
        SegmentedChoice(
            listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Paper", ThemeMode.DARK to "Night map"),
            state.settings.theme,
            actions.onTheme,
        )
        SectionHeader("Access")
        NotificationAccessRow(access.notificationAccess, actions.onAccess, divider = false)
        DiagnosticsSection(state.settings, actions)
        SectionHeader("About")
        AboutCard()
    }
}

@Composable
private fun AlertSection(s: Settings, onSettings: (String, (Settings) -> Settings) -> Unit) {
    SectionHeader("Alerts to the cluster")
    SwitchRow("Phone calls", s.alertCalls, { v -> onSettings("call alerts") { it.copy(alertCalls = v) } }, subtitle = "Caller name and missed calls")
    SwitchRow("WhatsApp calls", s.alertWhatsAppCalls, { v -> onSettings("WhatsApp call alerts") { it.copy(alertWhatsAppCalls = v) } }, subtitle = "Caller name")
    SwitchRow(
        "WhatsApp messages",
        s.alertWhatsAppMessages,
        { v -> onSettings("WhatsApp message alerts") { it.copy(alertWhatsAppMessages = v) } },
        subtitle = "Sender name and count, never text",
    )
    SwitchRow("SMS", false, {}, subtitle = "Not in version 1", enabled = false, divider = false)
}

@Composable
private fun RidingSection(s: Settings, onSettings: (String, (Settings) -> Settings) -> Unit) {
    SectionHeader("Riding")
    SwitchRow("Auto ride mode", s.autoRideMode, { v -> onSettings("auto ride mode") { it.copy(autoRideMode = v) } }, subtitle = "Open Ride mode by itself above 10 km/h")
    SwitchRow("Keep screen on", s.keepScreenOn, { v -> onSettings("keep screen on") { it.copy(keepScreenOn = v) } }, subtitle = "While Ride mode is open")
    SwitchRow(
        "Service reminders",
        s.serviceReminders,
        { v -> onSettings("service reminders") { it.copy(serviceReminders = v) } },
        subtitle = "When an item is due soon",
        divider = false,
    )
}

@Composable
private fun DiagnosticsSection(s: Settings, actions: SettingsActions) {
    SectionHeader("Diagnostics")
    SwitchRow(
        "Debug log",
        s.debugLog,
        { v -> actions.onSettings("debug log") { it.copy(debugLog = v) } },
        subtitle = "Record every frame. Off by default; the last 500 entries are always kept",
    )
    SwitchRow(
        "Hide names in exports",
        s.hideNames,
        { v -> actions.onSettings("hide names") { it.copy(hideNames = v) } },
        subtitle = "Default for each export; you can turn it off once",
    )
    LinkRow("Open debug log", AtlasIcons.log, { actions.onOpen(Route.Log) }, subtitle = "Filters, frame detail, export")
    LinkRow("Bike information", AtlasIcons.bike, { actions.onOpen(Route.Bike) }, subtitle = "Model, serial, firmware, BLE")
    LinkRow("Icon test", AtlasIcons.route, { actions.onOpen(Route.IconTest) }, subtitle = "Send each turn icon 1–45 to the cluster", divider = false)
}
