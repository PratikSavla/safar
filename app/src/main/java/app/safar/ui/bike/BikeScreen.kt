package app.safar.ui.bike

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.safar.core.AppContainer
import app.safar.ui.art.AtlasIcons
import app.safar.ui.art.BikeSilhouette
import app.safar.ui.components.AtlasBottomSheet
import app.safar.ui.components.AtlasCard
import app.safar.ui.components.AtlasPage
import app.safar.ui.components.BackTopBar
import app.safar.ui.components.CardStyle
import app.safar.ui.components.DevOnly
import app.safar.ui.components.DeveloperLine
import app.safar.ui.components.EmptyState
import app.safar.ui.components.Gap
import app.safar.ui.components.Kicker
import app.safar.ui.components.LeaderRow
import app.safar.ui.components.LinkRow
import app.safar.ui.components.Note
import app.safar.ui.components.PrimaryButton
import app.safar.ui.components.SecondaryButton
import app.safar.ui.components.SectionHeader
import app.safar.ui.components.Stamp
import app.safar.ui.components.StampTone
import app.safar.ui.components.byMode
import app.safar.ui.nav.Navigator
import app.safar.ui.nav.Route
import app.safar.ui.theme.Atlas

class BikeActions(
    val onBack: () -> Unit = {},
    val onPair: () -> Unit = {},
    val onLog: () -> Unit = {},
    val onIconTest: () -> Unit = {},
    val onForget: () -> Unit = {},
)

/**
 * Bike information (ux bike.js): model, serial number and versions read from the bike, and Forget with a confirm
 * sheet. Developer mode adds the raw Device Information (0x180A), the Bluetooth facts and the debug screens.
 */
@Composable
fun BikeScreen(navigator: Navigator, modifier: Modifier = Modifier) {
    val vm = viewModel { BikeViewModel(AppContainer.bike, AppContainer.savedBike) }
    val state by vm.state.collectAsStateWithLifecycle()
    val actions = BikeActions(
        onBack = navigator::back,
        onPair = { navigator.go(Route.Pair) },
        onLog = { navigator.go(Route.Log) },
        onIconTest = { navigator.go(Route.IconTest) },
        onForget = { if (vm.forget()) navigator.resetTo(Route.Pair) },
    )
    BikeContent(state, actions, modifier)
}

@Composable
internal fun BikeContent(state: BikeScreenState, actions: BikeActions, modifier: Modifier = Modifier) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    val info = state.info
    AtlasPage(
        modifier,
        overlay = {
            if (info != null) {
                AtlasBottomSheet(confirm, { confirm = false }) {
                    ForgetSheet(info.model.name, onKeep = { confirm = false }, onForget = { confirm = false; actions.onForget() })
                }
            }
        },
    ) {
        when {
            !state.loaded -> BackTopBar("Your bike", "Reading…", actions.onBack)
            info == null -> NoBike(actions)
            else -> {
                BackTopBar("Your bike", info.model.name, actions.onBack, titleEm = ", on paper.")
                RegistrationCard(info)
                DevOnly { DeveloperLink(info, actions) }
                SectionHeader("Forget this bike")
                Note("Safar stops connecting to it until you pair again. Your rides, fuel log and service dates stay.")
                Gap(14.dp)
                SecondaryButton("Forget this bike", { confirm = true }, Modifier.fillMaxWidth(), icon = AtlasIcons.cross)
            }
        }
    }
}

@Composable
private fun NoBike(actions: BikeActions) {
    BackTopBar("Your bike", "No bike", actions.onBack, titleEm = " yet.")
    EmptyState("Pair your bike to fill this page.", message = "Its model, serial number and software version show up here.")
    Gap(18.dp)
    PrimaryButton("Pair your bike", actions.onPair, Modifier.fillMaxWidth(), icon = AtlasIcons.search)
}

/** What the bike told Safar about itself, as a registration document (`.regdoc`). */
@Composable
private fun RegistrationCard(info: BikeInfo) {
    val c = Atlas.colors
    AtlasCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Kicker(byMode("Bike information", "Device Information · 0x180A"))
                BasicText("read from your bike, not from the internet", Modifier.padding(top = 4.dp), style = Atlas.type.serifItalic.copy(color = c.ink2))
            }
            Stamp(info.stamp, tone = if (info.connected) StampTone.MOSS else StampTone.INK)
        }
        BikeSilhouette(info.model.family, Modifier.align(Alignment.CenterHorizontally).padding(vertical = 12.dp), width = 160.dp, color = c.ink2)
        if (info.deviceInfo == null) {
            Note("Connect once and Safar reads these details from your bike.", color = c.ink3)
        }
        riderRows(info).forEach { (k, v) -> LeaderRow(k, v) }
        DevOnly {
            developerDeviceRows(info.deviceInfo).forEach { (k, v) -> LeaderRow(k, v) }
            DeveloperLine("↳ bike · Device Information · ${if (info.connected) "this connection" else "last connection"}", Modifier.padding(top = 6.dp))
            DeveloperLine("2A29 · 2A24 · 2A25 · 2A26 · 2A27 · 2A28 · 2A50", Modifier.padding(top = 8.dp))
        }
    }
}

/** The Bluetooth facts and the debug screens. Developer mode only. */
@Composable
private fun DeveloperLink(info: BikeInfo, actions: BikeActions) {
    SectionHeader("Bluetooth link") { Kicker(info.linkWord) }
    AtlasCard(style = CardStyle.LINE) { linkRows(info).forEach { (k, v) -> LeaderRow(k, v) } }
    Gap(8.dp)
    LinkRow("Debug log", AtlasIcons.log, actions.onLog, subtitle = "Every frame to and from this bike")
    LinkRow("Icon test", AtlasIcons.route, actions.onIconTest, subtitle = "Send each turn icon to the cluster", divider = false)
}

@Composable
private fun ForgetSheet(modelName: String, onKeep: () -> Unit, onForget: () -> Unit) {
    Kicker("Forget bike")
    BasicText("Forget the $modelName?", Modifier.padding(top = 6.dp, bottom = 8.dp), style = Atlas.type.displaySmall.copy(color = Atlas.colors.ink))
    Note("You can pair it again any time.")
    Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SecondaryButton("Keep", onKeep, Modifier.weight(1f))
        PrimaryButton("Forget", onForget, Modifier.weight(1f))
    }
}
