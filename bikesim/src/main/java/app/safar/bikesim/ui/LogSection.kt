package app.safar.bikesim.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.safar.bikesim.sim.RECEIVED_LOG_CAPACITY
import app.safar.bikesim.sim.ReceivedEntry
import app.safar.bikesim.sim.ReceivedLog

@Composable
fun EventsSection(s: SimUiState) {
    val p = LocalPalette.current
    Section("Events") {
        if (s.events.isEmpty()) Txt("Nothing yet.", Type.small, color = p.muted)
        for (e in s.events) Txt("${e.time}  ${e.text}", Type.small, color = if (e.error) p.error else p.text)
    }
}

/** Header and one row per received frame, newest first. */
fun LazyListScope.receivedLog(s: SimUiState, onCopy: () -> Unit, onClear: () -> Unit) {
    item(key = "log-header") {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Txt("Received · ${s.log.entries.size}/$RECEIVED_LOG_CAPACITY", Type.title, Modifier.weight(1f))
            SimButton("Copy as hex", onCopy, enabled = s.log.entries.isNotEmpty())
            SimButton("Clear", onClear, enabled = s.log.entries.isNotEmpty())
        }
    }
    if (s.log.entries.isEmpty()) {
        item(key = "log-empty") {
            Txt("Frames Safar writes to FFF1 appear here.", Type.small, Modifier.padding(horizontal = 16.dp), LocalPalette.current.muted)
        }
    }
    items(s.log.entries, key = { it.id }) { ReceivedRow(it) }
}

@Composable
private fun ReceivedRow(entry: ReceivedEntry) {
    val p = LocalPalette.current
    val f = entry.frame
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .background(p.surface)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt("${ReceivedLog.timeText(entry.at)}  '${f.type ?: '?'}' ${f.typeName}", Type.body, Modifier.weight(1f))
            Txt(if (f.checksumOk) "checksum ok" else "checksum bad", Type.small, color = if (f.checksumOk) p.ok else p.error)
        }
        Txt(f.text, Type.body)
        if (!f.checksumOk) Txt(f.checksumText, Type.small, color = p.error)
        Txt(f.hex, Type.mono, color = p.muted)
    }
}
