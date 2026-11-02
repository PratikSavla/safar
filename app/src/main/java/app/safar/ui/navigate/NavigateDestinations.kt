package app.safar.ui.navigate

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.safar.ui.art.AtlasIcons
import app.safar.ui.components.AtlasCard
import app.safar.ui.components.AtlasIcon
import app.safar.ui.components.CardStyle
import app.safar.ui.components.DevOnly
import app.safar.ui.components.TicketShape
import app.safar.ui.components.developerMode
import app.safar.ui.components.liveAge
import app.safar.ui.components.rememberNow
import app.safar.ui.format.FreshnessText
import app.safar.ui.theme.Atlas
import app.safar.ui.theme.AtlasShapes
import java.time.ZoneId
import java.util.Locale

fun DestKind.icon(): ImageVector = when (this) {
    DestKind.HOME -> AtlasIcons.home
    DestKind.WORK -> AtlasIcons.work
    DestKind.FUEL -> AtlasIcons.fuel
    DestKind.STAR -> AtlasIcons.star
    DestKind.PARKED -> AtlasIcons.parked
}

/** Rows of saved places, the parked spot and "Somewhere else" (`.dests`). */
@Composable
internal fun DestinationList(state: NavigateState, actions: NavigateActions) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.destinations.forEach { d ->
            if (state.editing && d.placeId != null) {
                EditingRow(d, actions)
            } else {
                PickRow(d, d.key == state.selected?.key, onClick = { actions.onPick(d.key) })
            }
        }
        AtlasCard(style = CardStyle.LINE, contentPadding = PaddingValues(0.dp), onClick = actions.onOther, onClickLabel = "Search in Google Maps") {
            RowBody(AtlasIcons.search, "Somewhere else", "Search in Google Maps", selected = false) {
                AtlasIcon(AtlasIcons.chev, tint = Atlas.colors.ink3, size = 18.dp)
            }
        }
    }
}

@Composable
private fun PickRow(d: Destination, selected: Boolean, onClick: () -> Unit) {
    val c = Atlas.colors
    val shape = TicketShape()
    val mod = Modifier
        .offset(x = if (selected) 4.dp else 0.dp)
        .semantics { this.selected = selected }
        .then(if (selected) Modifier.border(2.dp, c.rust, shape) else Modifier)
    AtlasCard(mod, style = CardStyle.TICKET, contentPadding = PaddingValues(0.dp), onClick = onClick, onClickLabel = "Choose ${d.label}") {
        RowBody(d.kind.icon(), d.label, detailText(d), selected) {
            // Where the place came from is a source label: Developer mode only. The detail line already names the place.
            DevOnly { BasicText(d.via.uppercase(Locale.ROOT), style = Atlas.type.src.copy(color = c.ink3, letterSpacing = Atlas.type.kicker.letterSpacing)) }
        }
    }
}

/** The parked spot carries its age: calm ("12 min ago") normally, to the second in Developer mode. */
@Composable
private fun detailText(d: Destination): String {
    val at = d.atMillis ?: return d.detail
    if (developerMode()) return "${d.detail} · ${liveAge(at)}"
    val zone = remember { ZoneId.systemDefault() }
    val now by rememberNow(MINUTE_MS)
    return "${d.detail} · ${FreshnessText.calmAge(at, now, zone)}"
}

private const val MINUTE_MS = 60_000L

@Composable
private fun EditingRow(d: Destination, actions: NavigateActions) {
    val id = d.placeId ?: return
    AtlasCard(style = CardStyle.TICKET, contentPadding = PaddingValues(0.dp)) {
        RowBody(d.kind.icon(), d.label, d.detail, selected = false, endPadding = 8.dp) {
            Row {
                EditButton(AtlasIcons.arrow, "Move ${d.label} up", rotation = -90f) { actions.onMove(id, -1) }
                EditButton(AtlasIcons.arrow, "Move ${d.label} down", rotation = 90f) { actions.onMove(id, 1) }
                EditButton(AtlasIcons.edit, "Edit ${d.label}") { actions.onEditPlace(id) }
            }
        }
    }
}

@Composable
private fun EditButton(icon: ImageVector, label: String, rotation: Float = 0f, onClick: () -> Unit) {
    Box(
        Modifier.size(Atlas.dimens.touchTarget).clickable(onClickLabel = label, role = Role.Button, onClick = onClick).semantics(mergeDescendants = true) {},
        contentAlignment = Alignment.Center,
    ) {
        AtlasIcon(icon, Modifier.rotate(rotation), contentDescription = label, tint = Atlas.colors.ink2, size = 20.dp)
    }
}

@Composable
private fun RowBody(
    icon: ImageVector,
    label: String,
    detail: String,
    selected: Boolean,
    endPadding: androidx.compose.ui.unit.Dp = 22.dp,
    trailing: @Composable () -> Unit,
) {
    val c = Atlas.colors
    Row(
        Modifier.fillMaxWidth().padding(start = 22.dp, end = endPadding, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val box = Modifier.size(42.dp)
        Box(
            if (selected) box.background(c.rust, AtlasShapes.small) else box.border(1.5.dp, c.rule2, AtlasShapes.small),
            contentAlignment = Alignment.Center,
        ) { AtlasIcon(icon, tint = if (selected) c.onRust else c.ink) }
        Column(Modifier.weight(1f)) {
            BasicText(label, style = Atlas.type.serifTitle.copy(color = c.ink), maxLines = 2, overflow = TextOverflow.Ellipsis)
            BasicText(detail, style = Atlas.type.src.copy(color = c.ink3), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}
