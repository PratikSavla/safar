package app.safar.ui.trips

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.safar.ui.art.AtlasIcons
import app.safar.ui.components.AtlasCard
import app.safar.ui.components.AtlasIcon
import app.safar.ui.components.CardStyle
import app.safar.ui.components.Freshness
import app.safar.ui.components.NumberWithUnit
import app.safar.ui.components.PrimaryButton
import app.safar.ui.components.SecondaryButton
import app.safar.ui.components.Stamp
import app.safar.ui.components.StatCell
import app.safar.ui.components.StatGrid
import app.safar.ui.components.TextAction
import app.safar.ui.components.atlasShadow
import app.safar.ui.format.Formatters
import app.safar.ui.format.ValueSource
import app.safar.ui.theme.Atlas
import app.safar.ui.theme.AtlasShapes
import kotlin.math.roundToInt

/** "This week" and "Last 30 days" side by side (`.totals`). */
@Composable
internal fun TotalsRow(week: TripTotals, month: TripTotals) {
    val style = Atlas.type.numberLarge.copy(fontSize = Atlas.type.numberLarge.fontSize * 0.87f)
    StatGrid(listOf(totalsCell("This week", week), totalsCell("Last 30 days", month)), columns = 2, numberStyle = style)
}

private fun totalsCell(label: String, t: TripTotals) = StatCell(
    label = label,
    value = Formatters.groupDigits(t.km.roundToInt().toLong(), indian = false),
    unit = "km",
    note = totalsNote(t),
)

/** The ride being written right now (`.live-entry`). */
@Composable
internal fun LiveEntry(live: LiveRideUi, modifier: Modifier = Modifier, onOpen: (Long) -> Unit = {}) {
    val c = Atlas.colors
    val open = live.tripId?.let { id -> { onOpen(id) } }
    AtlasCard(modifier, style = CardStyle.LINE, contentPadding = PaddingValues(16.dp, 14.dp), onClick = open, onClickLabel = "Open today’s ride") {
        Stamp("recording", tilted = false, solid = true)
        BasicText(
            "Today’s ride, in progress.",
            Modifier.padding(top = 6.dp, bottom = 2.dp),
            style = Atlas.type.serifItalic.copy(color = c.ink, fontSize = Atlas.type.serifItalic.fontSize * 1.15f),
        )
        BasicText(liveLine(live), style = Atlas.type.src.copy(color = c.ink3))
        Freshness(ValueSource.PHONE_GPS, live.atMillis)
    }
}

/** One journal entry as a tilted postcard: ink track on squared paper, day, title and numbers. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Postcard(
    row: TripRowUi,
    tilt: Float,
    thumb: TrackThumb,
    selected: Boolean = false,
    selecting: Boolean = false,
    onLongPress: () -> Unit = {},
    onClick: () -> Unit,
) {
    val c = Atlas.colors
    val spoken = postcardSpoken(row)
    val borderMod = if (selected) Modifier.border(2.dp, c.rust, AtlasShapes.tiny) else Modifier
    Box(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { rotationZ = tilt }
            .atlasShadow(AtlasShapes.tiny, c)
            .background(c.card, AtlasShapes.tiny)
            .then(borderMod)
            .clip(AtlasShapes.tiny)
            .combinedClickable(
                onClickLabel = if (selected) "Deselect ride" else "Open ride",
                onLongClickLabel = "Select ride",
                onLongClick = onLongPress,
                role = Role.Button,
                onClick = onClick,
            )
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                onClick(if (selected) "Deselect ride" else "Open ride") { onClick(); true }
            }
            .padding(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TrackMap(
                thumb.lat, thumb.lng,
                Modifier.size(120.dp, 96.dp).background(c.card2, AtlasShapes.tiny).border(1.dp, c.rule, AtlasShapes.tiny),
            )
            PostcardText(row, Modifier.weight(1f))
        }
        if (selecting) {
            Box(
                Modifier
                    .size(24.dp)
                    .align(Alignment.TopEnd)
                    .background(if (selected) c.rust else Color.Transparent, CircleShape)
                    .border(1.5.dp, if (selected) c.rust else c.ink3, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    AtlasIcon(AtlasIcons.check, tint = Color.White, size = 14.dp)
                }
            }
        } else {
            BasicText(
                entryNumber(row.number),
                Modifier.align(Alignment.TopEnd),
                style = Atlas.type.src.copy(color = c.rust, fontSize = Atlas.type.src.fontSize * 0.9f),
            )
        }
    }
}

@Composable
private fun PostcardText(row: TripRowUi, modifier: Modifier) {
    val c = Atlas.colors
    Column(modifier.padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        BasicText(row.day, style = Atlas.type.src.copy(color = c.ink2))
        BasicText(
            row.title,
            style = Atlas.type.serifTitle.copy(color = c.ink, fontSize = Atlas.type.serifTitle.fontSize * 1.08f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // Wraps at a large font scale rather than clipping the top speed.
        FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), itemVerticalAlignment = Alignment.Bottom) {
            val numStyle = Atlas.type.numberSmall.copy(fontSize = Atlas.type.numberSmall.fontSize * 0.81f)
            NumberWithUnit(Formatters.km1(row.km), "km", numStyle, unitScale = 0.55f, unitColor = c.ink3)
            val mono = Atlas.type.src.copy(color = c.ink2, fontSize = Atlas.type.src.fontSize * 1.09f)
            BasicText(durationText(row.minutes), Modifier.padding(bottom = 3.dp), style = mono, maxLines = 1)
            BasicText("top ${row.topKmh.roundToInt()} km/h", Modifier.padding(bottom = 3.dp), style = mono, maxLines = 1)
        }
    }
}

/** Mono fine print at the end of a page (`.footnote`). */
@Composable
internal fun Footnote(text: String, modifier: Modifier = Modifier) {
    BasicText(text, modifier.padding(top = 22.dp), style = Atlas.type.src.copy(color = Atlas.colors.ink3))
}

@Composable
internal fun MergeBar(count: Int, enabled: Boolean, onMerge: () -> Unit, onCancel: () -> Unit) {
    AtlasCard(Modifier.fillMaxWidth().padding(horizontal = 0.dp, vertical = 0.dp), contentPadding = PaddingValues(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            SecondaryButton("Cancel", onCancel, Modifier.weight(1f))
            PrimaryButton("Merge ($count)", onMerge, Modifier.weight(1f), icon = AtlasIcons.check, enabled = enabled)
        }
    }
}

@Composable
internal fun UndoBar(onUndo: () -> Unit) {
    AtlasCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText("Rides merged", style = Atlas.type.src.copy(color = Atlas.colors.ink))
            TextAction("Undo", onUndo, color = Atlas.colors.rust)
        }
    }
}
