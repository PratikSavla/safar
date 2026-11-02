package app.safar.ui.ride

import android.text.format.DateFormat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.safar.data.ThemeMode
import app.safar.ui.components.Stamp
import app.safar.ui.components.StampTone
import app.safar.ui.components.TopoBackground
import app.safar.ui.components.TopoTransform
import app.safar.ui.components.rememberNow
import app.safar.ui.format.Formatters
import app.safar.ui.nav.Navigator
import app.safar.ui.ride.preview.RidePreviewData
import app.safar.ui.shell.KeepScreenOn
import app.safar.ui.shell.LocalShellUi
import app.safar.ui.theme.Atlas
import app.safar.ui.theme.SafarTheme

/** How long the rider holds to leave (ride.js `HOLD_MS`). */
internal const val HOLD_MS = 900L

/** Ride mode (ride.js): huge numerals, next turn, fuel, gear, clock, call. Leave with a long-press anywhere. */
@Composable
fun RideScreen(navigator: Navigator, vm: RideViewModel = viewModel { RideViewModel.fromContainer() }) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val shell = LocalShellUi.current
    KeepScreenOn()
    RideContent(ui, shell.autoRide) {
        shell.leaveRide(stillMoving = ui?.moving == true)
        navigator.back()
    }
}

@Composable
internal fun RideContent(ui: RideUi?, auto: Boolean, onLeave: () -> Unit) {
    val c = Atlas.colors
    val leave by rememberUpdatedState(onLeave)
    var holding by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(if (holding) 1f else 0f, if (holding) tween(HOLD_MS.toInt(), easing = LinearEasing) else snap(), label = "hold")
    Box(
        Modifier
            .fillMaxSize()
            .background(c.paper)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    holding = true
                    var released = false
                    withTimeoutOrNull(HOLD_MS) {
                        waitForUpOrCancellation()
                        released = true
                    }
                    holding = false
                    if (!released) leave()
                }
            }
            .semantics { onLongClick("Leave ride mode") { leave(); true } },
    ) {
        TopoBackground(transform = TopoTransform.Ride)
        if (ui != null) RideColumn(ui, auto, holding)
        Box(Modifier.align(Alignment.BottomStart).padding(bottom = 24.dp).fillMaxWidth(progress).height(6.dp).background(c.rust))
    }
}

@Composable
private fun RideColumn(ui: RideUi, auto: Boolean, holding: Boolean) {
    val c = Atlas.colors
    val context = LocalContext.current
    val now by rememberNow()
    val clock = Formatters.clock(now, is24h = DateFormat.is24HourFormat(context))
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 34.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        RideTop(clock.substringBefore(' '), ui)
        ui.call?.let { RideCall(it) }
        ui.turn?.let { RideTurn(it) }
        RideSpeed(ui, Modifier.weight(1f))
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            ui.linkNote?.let { Stamp(it, tone = StampTone.AMBER) }
            if (auto) Stamp("auto", tone = StampTone.MOSS)
        }
        val hint = if (holding) c.rust else c.ink3
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            HoldGlyph(hint)
            BasicText("HOLD ANYWHERE TO LEAVE", style = Atlas.type.kicker.copy(color = hint))
        }
    }
}

@Preview(heightDp = 840)
@Composable
private fun RidePreview() = SafarTheme(ThemeMode.LIGHT) { RideContent(RidePreviewData.navigating(), auto = true) {} }

@Preview(heightDp = 840)
@Composable
private fun RideCallDarkPreview() = SafarTheme(ThemeMode.DARK) { RideContent(RidePreviewData.calling(), auto = false) {} }
