package app.safar.ui.home

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
import androidx.compose.runtime.getValue
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
import app.safar.ui.components.Freshness
import app.safar.ui.components.Kicker
import app.safar.ui.components.developerMode
import app.safar.ui.components.rememberNow
import app.safar.ui.copy.Phrases
import app.safar.ui.format.Formatters
import app.safar.ui.format.FreshnessText
import app.safar.ui.format.ValueSource
import app.safar.ui.nav.Route
import app.safar.ui.theme.Atlas
import app.safar.ui.theme.AtlasShapes

private val TILE_ICONS: Map<String, ImageVector> by lazy {
    mapOf(
        "fuel" to AtlasIcons.fuel, "logfuel" to AtlasIcons.plus, "service" to AtlasIcons.wrench, "parked" to AtlasIcons.parked,
        "trips" to AtlasIcons.journal, "navigate" to AtlasIcons.route, "alerts" to AtlasIcons.alerts, "bike" to AtlasIcons.bike, "log" to AtlasIcons.log,
        "papers" to AtlasIcons.papers,
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
    val hint = t.hintAtMillis?.let { tileAge(it) } ?: t.hint
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
        // Two lines as well, so "log a full tank" or "next in 300 km" wraps at a large font scale instead of "log a full …".
        BasicText(hint, style = Atlas.type.src.copy(fontSize = Atlas.type.src.fontSize * 0.92f, color = if (t.warn) c.rust else c.ink3), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * "12 min ago" for a tile, in calm words with no seconds; the exact age in Developer mode. Reads the clock at
 * 30 s steps here, so only this tile recomposes.
 */
@Composable
private fun tileAge(atMillis: Long): String {
    val now by rememberNow(periodMillis = 30_000)
    return if (developerMode()) Formatters.age(atMillis, now) else FreshnessText.calmAge(atMillis, now)
}

/** Missed calls and WhatsApp counts (home.css `.counts`): a rust-washed strip that opens Alerts. */
@Composable
fun CountsStrip(counts: CountsUi, onOpen: () -> Unit) {
    if (!counts.visible) return
    val c = Atlas.colors
    val parts = buildList {
        if (counts.missed.count > 0) {
            val calls = if (counts.missed.count == 1) "missed call" else "missed calls"
            add(Triple(AtlasIcons.missed, counts.missed.count, "$calls · ${counts.missed.lastName.orEmpty()}"))
        }
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

/** Active navigation on Today: the next turn as your bike's display gets it, tap for the Route page. */
@Composable
fun NavStrip(nav: NavStripUi?, onOpen: () -> Unit) {
    if (nav == null) return
    val c = Atlas.colors
    AtlasCard(Modifier.padding(top = 10.dp), style = CardStyle.LINE, onClick = onOpen, onClickLabel = "Open Route") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TurnArrow(nav.iconCode, size = 44.dp, color = if (nav.waiting) c.ink3 else c.rust)
            Column(Modifier.weight(1f)) {
                Kicker(if (nav.destination != null) "Heading to ${nav.destination}" else "Navigating")
                val main = when {
                    nav.waiting -> Phrases.WAITING_FOR_MAPS
                    nav.statusWord != null -> nav.statusWord
                    nav.distanceM != null -> "Next turn in ${Formatters.navDistance(nav.distanceM)}"
                    else -> "—"
                }
                BasicText(main, Modifier.padding(vertical = 2.dp), style = Atlas.type.serifTitle.copy(color = c.ink), maxLines = 2)
                // Maps posts only when the turn changes, so this says nothing in normal mode; the age is for Developer mode.
                if (nav.receivedAt != null) Freshness(ValueSource.GOOGLE_MAPS, nav.receivedAt)
            }
        }
    }
}
