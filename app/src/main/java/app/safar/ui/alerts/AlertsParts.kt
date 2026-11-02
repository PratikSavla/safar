package app.safar.ui.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.safar.core.ActiveCall
import app.safar.core.AlertCounter
import app.safar.core.AlertSource
import app.safar.data.Settings
import app.safar.ui.art.AtlasIcons
import app.safar.ui.components.AtlasCard
import app.safar.ui.components.AtlasIcon
import app.safar.ui.components.Kicker
import app.safar.ui.components.SecondaryButton
import app.safar.ui.components.dashedOutline
import app.safar.ui.components.rememberNow
import app.safar.ui.format.Formatters
import app.safar.ui.theme.Atlas
import app.safar.ui.theme.AtlasShapes

fun FeedIcon.vector(): ImageVector = when (this) {
    FeedIcon.CALL -> AtlasIcons.call
    FeedIcon.WHATSAPP -> AtlasIcons.wa
    FeedIcon.MISSED -> AtlasIcons.missed
    FeedIcon.MESSAGE -> AtlasIcons.msg
    FeedIcon.CLEARED -> AtlasIcons.check
}

/** Incoming call card (`.callcard`): rust while ringing, ink once answered. Announced right away. */
@Composable
internal fun CallCard(call: ActiveCall, settings: Settings) {
    val c = Atlas.colors
    val (kicker, line) = callCardText(call, settings)
    val bg = if (call.answered) c.ink else c.rust
    val fg = if (call.answered) c.paper else c.onRust
    Box(
        Modifier.fillMaxWidth().padding(bottom = 18.dp).clip(AtlasShapes.card).background(bg)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive },
    ) {
        AtlasIcon(
            if (call.source == AlertSource.WHATSAPP) AtlasIcons.wa else AtlasIcons.call,
            Modifier.align(Alignment.BottomEnd).offset(10.dp, 14.dp).alpha(0.18f),
            tint = fg,
            size = 110.dp,
        )
        Column(Modifier.padding(20.dp)) {
            Kicker(kicker, color = fg.copy(alpha = 0.8f))
            BasicText(call.name, Modifier.padding(top = 8.dp, bottom = 6.dp), style = Atlas.type.displayLarge.copy(color = fg), maxLines = 2, overflow = TextOverflow.Ellipsis)
            BasicText(line, style = Atlas.type.note.copy(color = fg.copy(alpha = 0.9f)))
            BasicText(
                "Answer or decline on the phone or the handlebar. Safar only relays the name.",
                Modifier.padding(top = 10.dp),
                style = Atlas.type.src.copy(color = fg.copy(alpha = 0.75f)),
            )
        }
    }
}

/** Missed calls and WhatsApp counters with Clear (`.tally`). */
@Composable
internal fun Tally(missed: AlertCounter, messages: AlertCounter, onClearMissed: () -> Unit, onClearMessages: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TallyCard("Missed", AtlasIcons.missed, missed, onClearMissed, "Clear missed calls", Modifier.weight(1f))
        TallyCard("WhatsApp", AtlasIcons.wa, messages, onClearMessages, "Clear WhatsApp messages", Modifier.weight(1f))
    }
}

@Composable
private fun TallyCard(title: String, icon: ImageVector, counter: AlertCounter, onClear: () -> Unit, clearLabel: String, modifier: Modifier) {
    val c = Atlas.colors
    AtlasCard(modifier, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AtlasIcon(icon, tint = c.ink3, size = 14.dp)
            Kicker(title)
        }
        BasicText(
            counter.count.toString(),
            Modifier.padding(top = 6.dp, bottom = 2.dp),
            style = Atlas.type.numberLarge.copy(color = c.ink, fontWeight = FontWeight.SemiBold),
        )
        BasicText(tallyLine(counter.count, counter.lastName), style = Atlas.type.uiSmall.copy(color = c.ink3), maxLines = 1, overflow = TextOverflow.Ellipsis)
        SecondaryButton(
            "Clear",
            onClear,
            Modifier.fillMaxWidth().padding(top = 6.dp).semantics { contentDescription = clearLabel },
            enabled = counter.count > 0,
            small = true,
        )
    }
}

/** Dashed privacy note: only names and counts reach the bike (`.privacy`). */
@Composable
internal fun PrivacyNote() {
    val c = Atlas.colors
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp).dashedOutline(c.rule2).padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AtlasIcon(AtlasIcons.lock, tint = c.moss)
        BasicText(
            buildAnnotatedString {
                append("Only ")
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = c.ink)) { append("names and counts") }
                append(" go to the bike, never message text. Names are cleaned to plain letters and cut to 19 characters.")
            },
            style = Atlas.type.note.copy(color = c.ink2),
        )
    }
}

/** Timeline of what went to the cluster (`.feed`): dashed spine, a round mark per line. */
@Composable
internal fun Feed(rows: List<FeedRow>) {
    val c = Atlas.colors
    val now = rememberNow()
    Column(
        Modifier.fillMaxWidth().drawBehind {
            val x = 17.dp.toPx()
            drawLine(
                c.rule2, Offset(x, 6.dp.toPx()), Offset(x, size.height - 6.dp.toPx()), strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
            )
        },
    ) {
        rows.forEach { r ->
            Row(Modifier.fillMaxWidth().padding(vertical = 9.dp).semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                val ink = if (r.alarm) c.rust else c.ink2
                Box(Modifier.size(34.dp).background(c.paper, CircleShape).border(1.5.dp, ink, CircleShape), contentAlignment = Alignment.Center) {
                    AtlasIcon(r.icon.vector(), tint = if (r.alarm) c.rust else c.ink, size = 16.dp)
                }
                Column(Modifier.weight(1f)) {
                    BasicText(r.name, style = Atlas.type.serifTitle.copy(color = c.ink, fontSize = Atlas.type.serifTitle.fontSize * 0.88f))
                    BasicText(r.what, Modifier.padding(top = 2.dp), style = Atlas.type.src.copy(color = c.ink3))
                    BasicText("${r.time} · ${Formatters.age(r.atMillis, now.value)}", style = Atlas.type.src.copy(color = c.ink3))
                }
            }
        }
    }
}
