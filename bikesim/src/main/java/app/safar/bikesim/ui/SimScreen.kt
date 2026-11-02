package app.safar.bikesim.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** What the activity does for the screen: permission request, Bluetooth enable request, clipboard copy. */
class ScreenActions(
    val requestPermissions: () -> Unit,
    val enableBluetooth: () -> Unit,
    val copyLog: () -> Unit,
)

@Composable
fun SimScreen(vm: SimViewModel, actions: ScreenActions) {
    val s by vm.state.collectAsStateWithLifecycle()
    val p = LocalPalette.current
    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(p.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        item(key = "title") {
            Txt("Safar BikeSim", Type.title, Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp))
        }
        item(key = "access") { AccessBanner(s, actions) }
        item(key = "link") { LinkSection(s, vm) }
        item(key = "name") { NameSection(s, vm) }
        item(key = "cluster") { ClusterSection(s, vm) }
        item(key = "faults") { ActionsSection(s, vm) }
        item(key = "events") { EventsSection(s) }
        receivedLog(s, actions.copyLog, vm::clearLog)
    }
}

/** Shown while a permission is missing or Bluetooth is off; nothing else can work until then. */
@Composable
private fun AccessBanner(s: SimUiState, actions: ScreenActions) {
    val (text, button, action) = when {
        !s.permissionsOk -> Triple("Bluetooth permission is needed to advertise and accept connections.", "Grant", actions.requestPermissions)
        !s.bluetoothOn -> Triple("Bluetooth is off.", "Turn on", actions.enableBluetooth)
        else -> return
    }
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).background(p.surface).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) { Txt(text, Type.body, color = p.error) }
        SimButton(button, action, primary = true)
    }
}
