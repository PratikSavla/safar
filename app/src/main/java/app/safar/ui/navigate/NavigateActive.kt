package app.safar.ui.navigate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.safar.protocol.Timing
import app.safar.ui.art.AtlasIcons
import app.safar.ui.art.LcdClusterPreview
import app.safar.ui.art.TurnArrow
import app.safar.ui.components.AtlasCard
import app.safar.ui.components.DashedRule
import app.safar.ui.components.Gap
import app.safar.ui.components.LeaderRow
import app.safar.ui.components.MarchingRule
import app.safar.ui.components.Note
import app.safar.ui.components.SecondaryButton
import app.safar.ui.components.SectionHeader
import app.safar.ui.components.SourceLine
import app.safar.ui.components.Stamp
import app.safar.ui.components.StampTone
import app.safar.ui.theme.Atlas

/** Card with a marching rust line, an italic serif title and a note (`.wait-card`). */
@Composable
internal fun WaitCard(title: String, note: String, modifier: Modifier = Modifier) {
    AtlasCard(modifier.semantics { liveRegion = LiveRegionMode.Polite }, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp)) {
        MarchingRule()
        BasicText(
            title,
            Modifier.padding(top = 16.dp, bottom = 8.dp),
            style = Atlas.type.displaySmall.copy(fontStyle = FontStyle.Italic, fontWeight = FontWeight(450), color = Atlas.colors.ink),
        )
        Note(note)
    }
}

@Composable
internal fun AwaitingMaps(onCancel: () -> Unit) {
    WaitCard(
        "Pick a place in Google Maps…",
        "Search, choose two-wheeler, tap Start. Safar joins the route by itself and sends turns to the cluster.",
    )
    Gap(14.dp)
    SecondaryButton("Cancel", onCancel, Modifier.fillMaxWidth())
}

@Composable
internal fun WaitingFirstUpdate(onEnd: () -> Unit) {
    WaitCard(
        "Waiting for the first Maps update…",
        "Nothing goes to the cluster until Maps posts its first turn. That avoids the empty navigation frame the official app sends.",
    )
    Gap(14.dp)
    SecondaryButton("End navigation", onEnd, Modifier.fillMaxWidth(), icon = AtlasIcons.cross)
}

/** Mirror of the next navigation frame, the LCD preview and End (`.mirror`, `.navlcd`). */
@Composable
internal fun ActiveRoute(m: NavMirror, bikeConnected: Boolean, onEnd: () -> Unit) {
    MirrorCard(m, bikeConnected)
    SectionHeader("On the cluster")
    LcdClusterPreview(
        turnCode = m.clusterIcon,
        toTurnMeters = m.toTurnMeters,
        eta = m.etaSent,
        remainingMeters = m.remainingMeters,
        status = m.status.code,
    )
    Gap(26.dp)
    SecondaryButton("End navigation", onEnd, Modifier.fillMaxWidth(), icon = AtlasIcons.cross)
}

@Composable
private fun MirrorCard(m: NavMirror, bikeConnected: Boolean) {
    val c = Atlas.colors
    AtlasCard(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TurnArrow(m.clusterIcon, size = 76.dp, color = c.rust, contentDescription = m.iconName)
            Column(Modifier.weight(1f)) {
                BasicText(
                    buildAnnotatedString {
                        append(m.toTurn.value)
                        withStyle(SpanStyle(fontSize = Atlas.type.numberLarge.fontSize * 0.4f, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Normal, color = c.ink2)) {
                            append(" ${m.toTurn.unit}")
                        }
                    },
                    style = Atlas.type.numberLarge.copy(color = c.ink, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                )
                if (m.instruction.isNotBlank()) {
                    BasicText(m.instruction, Modifier.padding(top = 4.dp), style = Atlas.type.serifItalic.copy(color = c.ink2), maxLines = 3)
                }
            }
        }
        Stamp("status ${m.status.code} · ${m.statusLabel}", Modifier.padding(top = 14.dp), tone = m.tone.stampTone())
        DashedRule(Modifier.padding(vertical = 10.dp))
        MirrorLeaders(m, bikeConnected)
        SourceLine("Google Maps notification", m.receivedAtMillis, Modifier.padding(top = 6.dp))
        Note(
            "The instruction text stays on your phone. The bike only gets the icon, distances, ETA and status.",
            Modifier.padding(top = 8.dp),
            color = c.ink3,
        )
    }
}

@Composable
private fun MirrorLeaders(m: NavMirror, bikeConnected: Boolean) {
    val how = if (m.iconMatched) "matched icon" else "from text"
    LeaderRow("Icon", "${m.clusterIcon} · ${if (m.clusterIcon == m.iconCode) m.iconName else "status"}")
    LeaderRow("Icon source", "$how · ${m.iconCode}")
    LeaderRow("To next turn", m.toTurn.toString())
    LeaderRow("ETA", "${m.etaShown} · sent as ${m.etaSent}")
    LeaderRow("Remaining", m.remaining.toString())
    LeaderRow("Frame", if (bikeConnected) "type 1 · every ${Timing.NAVIGATION_PERIOD_MS} ms" else "paused · bike not connected")
}

private fun StatusTone.stampTone(): StampTone = when (this) {
    StatusTone.FINE -> StampTone.MOSS
    StatusTone.INFO -> StampTone.SLATE
    StatusTone.WARN -> StampTone.AMBER
}
