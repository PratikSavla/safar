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
import app.safar.ui.components.DevOnly
import app.safar.ui.components.LinkRow
import app.safar.ui.components.ListRow
import app.safar.ui.components.Masthead
import app.safar.ui.components.SectionHeader
import app.safar.ui.components.SegmentedChoice
import app.safar.ui.components.Stamp
import app.safar.ui.components.StampTone
import app.safar.ui.components.SwitchRow
import app.safar.ui.components.TopoTransform
import app.safar.ui.copy.Phrases
import app.safar.ui.nav.Navigator
import app.safar.ui.nav.Route
import app.safar.ui.shell.LocalShellUi

class SettingsActions(
    val onName: (String) -> Unit = {},
    val onTank: (String) -> Unit = {},
    val onTankReset: () -> Unit = {},
    val onCurrency: (String) -> Unit = {},
    val onSpeed: (Int) -> Unit = {},
    /** A plain switch: a name for the log and the change. */
    val onSettings: (String, (Settings) -> Settings) -> Unit = { _, _ -> },
    val onTheme: (ThemeMode) -> Unit = {},
    val onAccess: () -> Unit = {},
    val onBackup: () -> Unit = {},
    val onOpen: (Route) -> Unit = {},
    val onDeveloperMode: (Boolean) -> Unit = {},
    val onVersionTap: () -> Unit = {},
)

/**
 * Settings tab (ux settings.js). Every alert and feature is switchable; text fields lock while moving. The
 * debug tools sit in a Developer section that only shows in Developer mode. [bottomBar] is the shell's tab strip.
 */
@Composable
fun SettingsScreen(navigator: Navigator, modifier: Modifier = Modifier, bottomBar: (@Composable () -> Unit)? = null) {
    val vm = viewModel { SettingsViewModel(AppContainer.settings, AppContainer.ride, AppContainer.bike, AppContainer.savedBike, AppContainer.appScope) }
    val state by vm.state.collectAsStateWithLifecycle()
    val access by rememberAccessStatus()
    val context = LocalContext.current
    val shell = LocalShellUi.current
    val actions = remember(vm, navigator, context, shell) {
        SettingsActions(
            onName = vm::setRiderName,
            onTank = vm::setTank,
            onTankReset = vm::resetTank,
            onCurrency = vm::setCurrency,
            onSpeed = vm::setSpeedAlert,
            onSettings = vm::toggle,
            onTheme = vm::setTheme,
            onAccess = { SystemAccess.openNotificationAccess(context) },
            onBackup = { if (!SystemAccess.openPhoneSettings(context)) shell.toast("Couldn’t open your phone’s settings") },
            onOpen = navigator::go,
            onDeveloperMode = { on ->
                vm.setDeveloperMode(on)
                shell.toast(if (on) Phrases.DEVELOPER_ON else Phrases.DEVELOPER_OFF)
            },
            onVersionTap = {
                vm.tapVersion(System.currentTimeMillis())?.let { on -> shell.toast(if (on) Phrases.DEVELOPER_ON else Phrases.DEVELOPER_OFF) }
            },
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
        ListRow("Kilometres", subtitle = "km and km/h, like your bike’s display. Miles are coming soon.", divider = false) {
            Stamp("km only", tone = StampTone.INK)
        }
        SectionHeader("Speed")
        SpeedBlock(state, actions.onSettings, actions.onSpeed)
        AlertSection(state.settings, actions.onSettings)
        RidingSection(state.settings, actions.onSettings)
        FuelSection(state, actions)
        SectionHeader("Theme")
        SegmentedChoice(
            listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Paper", ThemeMode.DARK to "Night map"),
            state.settings.theme,
            actions.onTheme,
        )
        SectionHeader("Phone access")
        NotificationAccessRow(access.notificationAccess, actions.onAccess, divider = false)
        SectionHeader("Your bike")
        LinkRow("Bike information", AtlasIcons.bike, { actions.onOpen(Route.Bike) }, subtitle = "Model, serial number and software version", divider = false)
        SectionHeader("Backup")
        BackupBlock(actions.onBackup)
        SectionHeader("About")
        AboutCard(actions.onVersionTap)
        DeveloperModeRow(state.settings.developerMode, actions.onDeveloperMode)
        DevOnly { DeveloperSection(state.settings, actions) }
    }
}

@Composable
private fun AlertSection(s: Settings, onSettings: (String, (Settings) -> Settings) -> Unit) {
    SectionHeader("Alerts on your bike")
    SwitchRow("Phone calls", s.alertCalls, { v -> onSettings("call alerts") { it.copy(alertCalls = v) } }, subtitle = "Caller name and missed calls")
    SwitchRow("WhatsApp calls", s.alertWhatsAppCalls, { v -> onSettings("WhatsApp call alerts") { it.copy(alertWhatsAppCalls = v) } }, subtitle = "Caller name")
    SwitchRow(
        "WhatsApp messages",
        s.alertWhatsAppMessages,
        { v -> onSettings("WhatsApp message alerts") { it.copy(alertWhatsAppMessages = v) } },
        subtitle = "Sender name and count, never the message",
    )
    SwitchRow("SMS", false, {}, subtitle = Phrases.COMING_SOON, enabled = false, divider = false)
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
        subtitle = "When a service item is due soon",
        divider = false,
    )
}

@Composable
private fun FuelSection(state: SettingsState, actions: SettingsActions) {
    SectionHeader("Fuel")
    TankBlock(state, actions.onTank, actions.onTankReset)
    CurrencyBlock(state, actions.onCurrency)
    SwitchRow(
        "Low-fuel reminder",
        state.settings.lowFuelAlert,
        { v -> actions.onSettings("low fuel") { it.copy(lowFuelAlert = v) } },
        subtitle = "When the fuel gauge drops to 1 bar",
        divider = false,
    )
}

/** Debug log, export privacy, and the debug screens. Developer mode only. */
@Composable
private fun DeveloperSection(s: Settings, actions: SettingsActions) {
    SectionHeader("Developer")
    SwitchRow(
        "Debug log",
        s.debugLog,
        { v -> actions.onSettings("debug log") { it.copy(debugLog = v) } },
        subtitle = "Record every frame to and from the bike. The last 500 entries are always kept in memory",
    )
    SwitchRow(
        "Hide names in exports",
        s.hideNames,
        { v -> actions.onSettings("hide names") { it.copy(hideNames = v) } },
        subtitle = "Default for each export; you can turn it off once",
    )
    LinkRow("Open debug log", AtlasIcons.log, { actions.onOpen(Route.Log) }, subtitle = "Filters, frame detail, export")
    LinkRow("Icon test", AtlasIcons.route, { actions.onOpen(Route.IconTest) }, subtitle = "Send each turn icon 1–45 to the cluster", divider = false)
}
