package app.safar.bikesim.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.safar.bikesim.sim.Gear
import app.safar.bikesim.sim.MAX_FUEL_BARS
import app.safar.bikesim.sim.MAX_SIM_SPEED_KMH
import app.safar.bikesim.sim.NAME_PRESETS
import app.safar.protocol.hex
import java.util.Locale

@Composable
fun LinkSection(s: SimUiState, vm: SimViewModel) {
    val p = LocalPalette.current
    Section("Link") {
        Toggle("Advertise", s.wanted, vm::setSimulatorOn, enabled = s.permissionsOk && s.bluetoothOn)
        InfoRow("Advertising", if (s.advertising) "on as ${s.name}" else "off", if (s.advertising) p.ok else null)
        InfoRow("Central", s.central ?: "none")
        InfoRow("MTU", s.mtu?.toString() ?: "not negotiated")
        InfoRow("Notify on FFF2", if (s.notifyOn) "enabled" else "off", if (s.notifyOn) p.ok else null)
        val frames = when {
            s.framesPaused -> "paused (bike off)"
            s.sendingFrames -> "every 5 s"
            else -> "waiting for notify"
        }
        InfoRow("Cluster frames", "$frames · ${s.framesSent} sent")
        s.lastSent?.let { Txt(it, Type.mono, color = p.muted) }
    }
}

@Composable
fun NameSection(s: SimUiState, vm: SimViewModel) {
    val p = LocalPalette.current
    val locked = s.wanted
    Section("Advertised name") {
        Choices(NAME_PRESETS, s.name.takeIf { it in NAME_PRESETS } ?: "", { it }, vm::setName, enabled = !locked)
        BasicTextField(
            value = s.name,
            onValueChange = { vm.setName(it.uppercase(Locale.ROOT)) },
            enabled = !locked,
            singleLine = true,
            textStyle = Type.value.copy(color = if (locked) p.muted else p.text),
            cursorBrush = SolidColor(p.accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, p.line, RoundedCornerShape(8.dp))
                .padding(10.dp),
        )
        val info = s.nameInfo
        Txt(info.summary, Type.small, color = if (info.matchesFilter) p.muted else p.error)
        if (locked) Txt("Switch advertising off to change the name.", Type.small, color = p.muted)
    }
}

@Composable
fun ClusterSection(s: SimUiState, vm: SimViewModel) {
    val c = s.cluster
    Section("Cluster") {
        InfoRow("Speed", "${c.speedKmh} km/h")
        ValueSlider(c.speedKmh, 0, MAX_SIM_SPEED_KMH, vm::setSpeed)
        Toggle("Ride (odometer and trips grow every second)", s.riding, vm::setRiding)
        InfoRow("Odometer", String.format(Locale.ROOT, "%.2f km (sends %d)", c.odometerKm, c.odometerKm.toLong()))
        InfoRow("Trip A / Trip B", String.format(Locale.ROOT, "%.1f / %.1f km", c.tripAKm, c.tripBKm))
        Txt("Fuel bars", Type.body, color = LocalPalette.current.muted)
        Choices((0..MAX_FUEL_BARS).toList(), c.fuelBars, { it.toString() }, vm::setFuel)
        Toggle("Drain fuel while riding (1 bar per 5 km)", s.autoDrain, vm::setAutoDrain)
        Txt("Gear (byte 23)", Type.body, color = LocalPalette.current.muted)
        Choices(Gear.entries, c.gear, { it.label }, vm::setGear)
        Raw25Row(c.raw25, vm::setRaw25)
    }
}

@Composable
private fun Raw25Row(value: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Txt("Byte 25", Type.body, Modifier.weight(1f), color = LocalPalette.current.muted)
        SimButton("-10", { onChange(value - 10) })
        SimButton("-1", { onChange(value - 1) })
        Txt("0x${hex(value)} ($value)", Type.mono)
        SimButton("+1", { onChange(value + 1) })
        SimButton("+10", { onChange(value + 10) })
    }
}

@Composable
fun ActionsSection(s: SimUiState, vm: SimViewModel) {
    val linked = s.central != null
    Section("Faults") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SimButton("Send corrupt frame", vm::sendCorrupt, enabled = s.notifyOn, modifier = Modifier.weight(1f))
            SimButton("Send wrong checksum", vm::sendWrongChecksum, enabled = s.notifyOn, modifier = Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SimButton("Drop link", vm::dropLink, enabled = linked, modifier = Modifier.weight(1f))
            SimButton(
                if (s.framesPaused) "Resume frames" else "Pause frames (bike off)",
                { vm.setPaused(!s.framesPaused) },
                primary = s.framesPaused,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
