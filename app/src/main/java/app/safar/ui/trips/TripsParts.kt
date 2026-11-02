package app.safar.ui.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.safar.ui.components.AtlasCard
import app.safar.ui.components.CardStyle
import app.safar.ui.components.NumberWithUnit
import app.safar.ui.components.SourceLine
import app.safar.ui.components.Stamp
import app.safar.ui.components.StatCell
import app.safar.ui.components.StatGrid
import app.safar.ui.components.atlasShadow
import app.safar.ui.format.Formatters
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
    note = "${t.rides} ride${if (t.rides == 1) "" else "s"} · ${durationText(t.minutes)} · top ${t.topKmh.roundToInt()}",
)

/** The ride being written right now (`.live-entry`). */
@Composable
internal fun LiveEntry(live: LiveRideUi, modifier: Modifier = Modifier, onOpen: (Long) -> Unit = {}) {
    val c = Atlas.colors
    val open = live.tripId?.let { id -> { onOpen(id) } }
    AtlasCard(modifier, style = CardStyle.LINE, contentPadding = PaddingValues(16.dp, 14.dp), onClick = open, onClickLabel = "Open the ride being recorded") {
        Stamp("recording", tilted = false, solid = true)
        BasicText(
            "Today’s ride, still being written.",
            Modifier.padding(top = 6.dp, bottom = 2.dp),
            style = Atlas.type.serifItalic.copy(color = c.ink, fontSize = Atlas.type.serifItalic.fontSize * 1.15f),
        )
        val speed = live.speedKmh?.roundToInt()?.toString() ?: "—"
        BasicText("GPS $speed km/h · top ${live.topKmh.roundToInt()} km/h", style = Atlas.type.src.copy(color = c.ink3))
        SourceLine("phone GPS", live.atMillis)
    }
}

/** One journal entry as a tilted postcard: ink track on squared paper, day, title and numbers. */
@Composable
internal fun Postcard(row: TripRowUi, tilt: Float, thumb: TrackThumb, onClick: () -> Unit) {
    val c = Atlas.colors
    val spoken = "Entry ${row.number}, ${row.title}, ${row.day}, ${Formatters.km1(row.km)} kilometres, " +
        "${durationText(row.minutes)}, top ${row.topKmh.roundToInt()} km/h"
    Box(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { rotationZ = tilt }
            .atlasShadow(AtlasShapes.tiny, c)
            .background(c.card, AtlasShapes.tiny)
            .clip(AtlasShapes.tiny)
            .clickable(onClickLabel = "Open ride", role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                onClick("Open ride") { onClick(); true }
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
        BasicText(
            entryNumber(row.number),
            Modifier.align(Alignment.TopEnd),
            style = Atlas.type.src.copy(color = c.rust, fontSize = Atlas.type.src.fontSize * 0.9f),
        )
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
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            val numStyle = Atlas.type.numberSmall.copy(fontSize = Atlas.type.numberSmall.fontSize * 0.81f)
            NumberWithUnit(Formatters.km1(row.km), "km", numStyle, unitScale = 0.55f, unitColor = c.ink3)
            val mono = Atlas.type.src.copy(color = c.ink2, fontSize = Atlas.type.src.fontSize * 1.09f)
            BasicText(durationText(row.minutes), Modifier.padding(bottom = 3.dp), style = mono, maxLines = 1)
            BasicText("top ${row.topKmh.roundToInt()}", Modifier.padding(bottom = 3.dp), style = mono, maxLines = 1)
        }
    }
}

/** Mono fine print at the end of a page (`.footnote`). */
@Composable
internal fun Footnote(text: String, modifier: Modifier = Modifier) {
    BasicText(text, modifier.padding(top = 22.dp), style = Atlas.type.src.copy(color = Atlas.colors.ink3))
}
