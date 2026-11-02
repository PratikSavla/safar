package app.safar.ui.pair

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.safar.core.LinkState
import app.safar.ui.art.AtlasIcons
import app.safar.ui.components.AtlasIcon
import app.safar.ui.components.AtlasPage
import app.safar.ui.components.BackTopBar
import app.safar.ui.components.DashedRule
import app.safar.ui.components.EmptyState
import app.safar.ui.components.InkButton
import app.safar.ui.components.LockLine
import app.safar.ui.components.PrimaryButton
import app.safar.ui.components.SecondaryButton
import app.safar.ui.components.byMode
import app.safar.ui.copy.LinkCopy
import app.safar.ui.copy.Phrases
import app.safar.ui.nav.Navigator
import app.safar.ui.nav.Route
import app.safar.ui.theme.Atlas

/** What the pair page can do. */
class PairActions(
    val scan: () -> Unit,
    val pick: (String) -> Unit,
    val done: () -> Unit,
    val home: () -> Unit,
    val requestPermission: () -> Unit,
    val enableBluetooth: () -> Unit,
)

/** Pairing (pair.js): look for Suzuki bikes, pick one, connect, success. No code to type. */
@Composable
fun PairScreen(navigator: Navigator, vm: PairViewModel = viewModel { PairViewModel.fromContainer() }) {
    val inputs by vm.inputs.collectAsStateWithLifecycle()
    val access = rememberBtAccess()
    val fixes = rememberBtFixes(onChanged = {})
    LaunchedEffect(access.value) { if (access.value == BtAccess.READY) vm.autoScan() }
    val actions = remember(navigator, vm, fixes) {
        PairActions(
            scan = vm::scan, pick = vm::pick, done = { navigator.resetTo(Route.Home) }, home = { navigator.go(Route.Home) },
            requestPermission = fixes.requestPermission, enableBluetooth = fixes.enableBluetooth,
        )
    }
    val ui = inputs?.let { remember(it, access.value) { pairUi(it, access.value) } }
    AtlasPage {
        BackTopBar("Pairing", "Find your", onBack = navigator::back, titleEm = " bike.")
        if (ui != null) PairBody(ui, actions)
        if (ui?.phase != PairPhase.SUCCESS) PairNotes()
    }
}

@Composable
internal fun ColumnScope.PairBody(ui: PairUi, a: PairActions) {
    when (ui.phase) {
        PairPhase.NEEDS_PERMISSION -> Blocker("Allow Nearby devices", "Android asks for this before Safar can look for your bike.", "Allow", a.requestPermission)
        PairPhase.BLUETOOTH_OFF -> Blocker(
            LinkCopy.headline(LinkState.BluetoothOff, null, hasSavedBike = false),
            "${LinkCopy.detail(LinkState.BluetoothOff, null, hasSavedBike = false)} Safar never turns it on without asking.",
            Phrases.TURN_ON_BLUETOOTH, a.enableBluetooth,
        )
        PairPhase.UNSUPPORTED -> EmptyState("This phone can’t connect.", message = "It doesn’t have the Bluetooth your Suzuki needs.")
        PairPhase.CONNECTING -> Connecting(ui)
        PairPhase.SUCCESS -> PairSuccess(ui, a.done)
        PairPhase.ALREADY_LINKED -> AlreadyLinked(ui, a)
        PairPhase.LISTENING, PairPhase.RESULTS, PairPhase.NOTHING -> Scanning(ui, a)
    }
}

@Composable
private fun ColumnScope.Scanning(ui: PairUi, a: PairActions) {
    val c = Atlas.colors
    Radar(ui.scanning, Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp))
    Row(Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(ui.scanLine, style = Atlas.type.serifItalic.copy(fontSize = Atlas.type.serifItalic.fontSize * 1.05f, color = c.ink2))
        if (ui.scanning) BasicText("  ${ui.results.size} found", style = Atlas.type.src.copy(color = c.rust))
    }
    if (ui.failed) LockLine("Couldn’t reach your bike. Is the ignition on? Tap it to try again.", Modifier.padding(bottom = 10.dp))
    Results(ui, a)
    if (!ui.scanning) {
        val again = ui.results.isNotEmpty()
        val modifier = Modifier.fillMaxWidth()
        if (again) {
            SecondaryButton(Phrases.LOOK_AGAIN, a.scan, modifier, icon = AtlasIcons.search)
        } else {
            PrimaryButton(Phrases.LOOK_FOR_BIKE, a.scan, modifier, icon = AtlasIcons.search)
        }
    }
}

@Composable
private fun Results(ui: PairUi, a: PairActions) {
    if (ui.results.isEmpty()) {
        if (!ui.scanning) {
            EmptyState("No bike found yet.", Modifier.padding(bottom = 16.dp), message = "Is the ignition on? Your bike only shows up while the key is turned.")
        }
        return
    }
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ui.results.forEach { bike -> ScanResult(bike) { a.pick(bike.address) } }
    }
}

@Composable
private fun Blocker(title: String, why: String, action: String, onAction: () -> Unit) {
    val c = Atlas.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Radar(on = false)
        BasicText(title, Modifier.padding(top = 8.dp), style = Atlas.type.displaySmall.copy(color = c.ink, textAlign = TextAlign.Center))
        BasicText(why, Modifier.padding(top = 6.dp, bottom = 18.dp), style = Atlas.type.note.copy(color = c.ink2, textAlign = TextAlign.Center))
        PrimaryButton(action, onAction, Modifier.fillMaxWidth(), icon = AtlasIcons.bt)
    }
}

@Composable
private fun PairNotes() {
    Column(Modifier.fillMaxWidth().padding(top = 22.dp)) {
        DashedRule(Modifier.padding(bottom = 6.dp), thickness = 1.dp, dash = 4.dp, gap = 3.dp)
        Note(AtlasIcons.lock, Phrases.NO_CODE_NEEDED, "")
        Note(AtlasIcons.bike, "Only Suzuki bikes show up here.", byMode("", " Names have 12 characters and start with SA or SB."))
    }
}

@Composable
private fun Note(icon: ImageVector, bold: String, rest: String) {
    val c = Atlas.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AtlasIcon(icon, tint = c.moss, size = 18.dp, modifier = Modifier.padding(top = 1.dp))
        BasicText(
            buildAnnotatedString {
                withStyle(SpanStyle(color = c.ink, fontWeight = Atlas.type.uiStrong.fontWeight)) { append(bold) }
                append(rest)
            },
            style = Atlas.type.note.copy(color = c.ink2),
        )
    }
}
