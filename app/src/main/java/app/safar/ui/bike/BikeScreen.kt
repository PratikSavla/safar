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
import app.safar.ui.components.EmptyState
import app.safar.ui.components.Gap
import app.safar.ui.components.Kicker
import app.safar.ui.components.LeaderRow
import app.safar.ui.components.LinkRow
import app.safar.ui.components.Note
import app.safar.ui.components.PrimaryButton
import app.safar.ui.components.SecondaryButton
import app.safar.ui.components.SectionHeader
import app.safar.ui.components.SourceLine
import app.safar.ui.components.Stamp
import app.safar.ui.components.StampTone
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

/** Bike information (ux bike.js): Device Information 0x180A, BLE facts, links, and Forget with a confirm sheet. */
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
            !state.loaded -> BackTopBar("Bike information", "Reading…", actions.onBack)
            info == null -> NoBike(actions)
            else -> {
                BackTopBar("Bike information", info.model.name, actions.onBack, titleEm = ", on paper.")
                RegistrationCard(info)
                SectionHeader("Bluetooth link") { Kicker(info.linkWord) }
                AtlasCard(style = CardStyle.LINE) { linkRows(info).forEach { (k, v) -> LeaderRow(k, v) } }
                Gap(8.dp)
                LinkRow("Debug log", AtlasIcons.log, actions.onLog, subtitle = "Every frame to and from this bike")
                LinkRow("Icon test", AtlasIcons.route, actions.onIconTest, subtitle = "Send each turn icon to the cluster", divider = false)
                SectionHeader("Forget")
                Note("Removes the saved bike. Safar stops reconnecting until you pair again. Trips and service dates stay.")
                Gap(14.dp)
                SecondaryButton("Forget this bike", { confirm = true }, Modifier.fillMaxWidth(), icon = AtlasIcons.cross)
            }
        }
    }
}

@Composable
private fun NoBike(actions: BikeActions) {
    BackTopBar("Bike information", "No bike", actions.onBack, titleEm = " on file.")
    EmptyState("Pair a bike to fill this page.", message = "Model, serial and firmware are read from the cluster.")
    Gap(18.dp)
    PrimaryButton("Find my bike", actions.onPair, Modifier.fillMaxWidth(), icon = AtlasIcons.search)
}

/** Device Information as a registration document (`.regdoc`). */
@Composable
private fun RegistrationCard(info: BikeInfo) {
    val c = Atlas.colors
    AtlasCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Kicker("Device Information · 0x180A")
                BasicText("read from the cluster, not from the internet", Modifier.padding(top = 4.dp), style = Atlas.type.serifItalic.copy(color = c.ink2))
            }
            Stamp(if (info.connected) "read on connect" else "last read", tone = if (info.connected) StampTone.MOSS else StampTone.INK)
        }
        BikeSilhouette(info.model.family, Modifier.align(Alignment.CenterHorizontally).padding(vertical = 12.dp), width = 160.dp, color = c.ink2)
        if (info.deviceInfo == null) {
            Note("Not read yet. Connect the bike once and Safar reads these fields from the cluster.", color = c.ink3)
        }
        deviceRows(info.deviceInfo).forEach { (k, v) -> LeaderRow(k, v) }
        SourceLine("bike · Device Information", Modifier.padding(top = 6.dp), age = if (info.connected) "this connection" else "last connection")
        BasicText("2A29 · 2A24 · 2A25 · 2A26 · 2A27 · 2A28 · 2A50", Modifier.padding(top = 8.dp), style = Atlas.type.src.copy(color = c.ink3))
    }
}

@Composable
private fun ForgetSheet(modelName: String, onKeep: () -> Unit, onForget: () -> Unit) {
    Kicker("Forget bike")
    BasicText("Forget the $modelName?", Modifier.padding(top = 6.dp, bottom = 8.dp), style = Atlas.type.displaySmall.copy(color = Atlas.colors.ink))
    Note("You can pair it again any time. No PIN needed.")
    Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SecondaryButton("Keep", onKeep, Modifier.weight(1f))
        PrimaryButton("Forget", onForget, Modifier.weight(1f))
    }
}
