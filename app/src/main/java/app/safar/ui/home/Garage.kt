package app.safar.ui.home

import app.safar.ui.components.liveAge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.safar.core.AlertSource
import app.safar.ui.art.AtlasIcons
import app.safar.ui.art.TurnArrow
import app.safar.ui.components.AtlasCard
import app.safar.ui.components.AtlasIcon
import app.safar.ui.components.CardStyle
import app.safar.ui.components.Kicker
import app.safar.ui.components.SourceLine
import app.safar.ui.format.Formatters
import app.safar.ui.nav.Route
import app.safar.ui.theme.Atlas
import app.safar.ui.theme.AtlasShapes

private val TILE_ICONS: Map<String, ImageVector> by lazy {
    mapOf(
        "fuel" to AtlasIcons.fuel, "logfuel" to AtlasIcons.plus, "service" to AtlasIcons.wrench, "parked" to AtlasIcons.parked,
        "trips" to AtlasIcons.journal, "navigate" to AtlasIcons.route, "alerts" to AtlasIcons.alerts, "bike" to AtlasIcons.bike, "log" to AtlasIcons.log,
    )
}

/** Garage tiles (home.css `.garage`): three across, two at large font sizes. */
@Composable
fun Garage(tiles: List<GarageTile>, onOpen: (Route) -> Unit) {
    val columns = if (LocalDensity.current.fontScale > 1.3f) 2 else 3
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { t -> Tile(t, Modifier.weight(1f).fillMaxHeight()) { onOpen(t.route) } }
                repeat(columns - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Tile(t: GarageTile, modifier: Modifier, onClick: () -> Unit) {
    val c = Atlas.colors
    val hint = t.hintAtMillis?.let { liveAge(it, periodMillis = 30_000) } ?: t.hint
    val outline = if (t.accent) c.rust else c.rule
    Column(
        modifier
            .heightIn(min = 76.dp)
            .background(c.card, AtlasShapes.small)
            .border(if (t.accent) 1.5.dp else 1.dp, outline, AtlasShapes.small)
            .clickable(onClickLabel = "Open ${t.name}", role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = "${t.name}, $hint"; role = Role.Button }
            .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 9.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        AtlasIcon(TILE_ICONS[t.key] ?: AtlasIcons.info, tint = if (t.accent) c.rust else c.ink2, size = 20.dp, modifier = Modifier.padding(bottom = 2.dp))
        // Two lines: at a large font scale "Debug log" wraps instead of being cut to "Debug l…".
        BasicText(t.name, style = Atlas.type.serifTitle.copy(fontSize = Atlas.type.serifTitle.fontSize * 0.88f, color = c.ink), maxLines = 2, overflow = TextOverflow.Ellipsis)
        BasicText(hint, style = Atlas.type.src.copy(fontSize = Atlas.type.src.fontSize * 0.92f, color = if (t.warn) c.rust else c.ink3), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Missed calls and WhatsApp counts (home.css `.counts`): a rust-washed strip that opens Alerts. */
@Composable
fun CountsStrip(counts: CountsUi, onOpen: () -> Unit) {
    if (!counts.visible) return
    val c = Atlas.colors
    val parts = buildList {
        if (counts.missed.count > 0) add(Triple(AtlasIcons.missed, counts.missed.count, "missed · ${counts.missed.lastName.orEmpty()}"))
        if (counts.messages.count > 0) {
            val what = if (counts.messages.source == AlertSource.WHATSAPP) "WhatsApp" else "messages"
            add(Triple(AtlasIcons.wa, counts.messages.count, "$what · ${counts.messages.lastName.orEmpty()}"))
        }
    }
    FlowRow(
        Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp).background(c.rustWash, AtlasShapes.small)
            .clickable(onClickLabel = "Open alerts", role = Role.Button, onClick = onOpen).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        parts.forEach { (icon, n, text) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AtlasIcon(icon, tint = c.rust, size = 18.dp)
                BasicText(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontFamily = Atlas.type.numberSmall.fontFamily, fontSize = Atlas.type.note.fontSize * 1.35f, color = c.ink)) { append("$n ") }
                        append(text.trimEnd(' ', '·'))
                    },
                    style = Atlas.type.note.copy(color = c.ink2),
                    maxLines = 1,
                )
            }
        }
    }
}

/** Active navigation on Today: the next turn as the cluster gets it, tap for the Route page. */
@Composable
fun NavStrip(nav: NavStripUi?, onOpen: () -> Unit) {
    if (nav == null) return
    val c = Atlas.colors
    AtlasCard(Modifier.padding(top = 10.dp), style = CardStyle.LINE, onClick = onOpen, onClickLabel = "Open navigation") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TurnArrow(nav.iconCode, size = 44.dp, color = if (nav.waiting) c.ink3 else c.rust)
            Column(Modifier.weight(1f)) {
                Kicker(if (nav.destination != null) "En route · ${nav.destination}" else "En route")
                val main = when {
                    nav.waiting -> "Waiting for Google Maps…"
                    nav.statusWord != null -> nav.statusWord
                    nav.distanceM != null -> Formatters.navDistance(nav.distanceM).toString()
                    else -> "—"
                }
                BasicText(main, Modifier.padding(vertical = 2.dp), style = Atlas.type.serifTitle.copy(color = c.ink), maxLines = 1)
                if (nav.receivedAt != null) SourceLine("Google Maps", nav.receivedAt) else SourceLine("Google Maps", age = "waiting")
            }
        }
    }
}
