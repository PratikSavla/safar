package app.safar.ui.ride

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.min
import app.safar.core.AlertSource
import app.safar.ui.art.AtlasIcons
import app.safar.ui.art.TurnArrow
import app.safar.ui.components.AtlasIcon
import app.safar.ui.components.FuelBars
import app.safar.ui.components.Hairline
import app.safar.ui.components.SourceLine
import app.safar.ui.theme.Atlas
import app.safar.ui.theme.AtlasShapes
import app.safar.ui.theme.FrauncesGlance

/** Heavy glance numerals sized in dp, so the huge figures fit the screen at any font scale. */
@Composable
internal fun glance(size: Dp, weight: Int = 800, color: Color = Atlas.colors.ink): TextStyle {
    val sp: TextUnit = with(LocalDensity.current) { size.toSp() }
    return TextStyle(fontFamily = FrauncesGlance, fontWeight = FontWeight(weight), fontSize = sp, lineHeight = sp, letterSpacing = (-0.04).em, color = color, fontFeatureSettings = "lnum, tnum")
}

/**
 * Clock, gear in a ring, fuel bars, over a 2 dp ink rule (ride.css `.r-top`). Gear and fuel carry their
 * source and age underneath, and fade to ink-2 when they are the last known frame (docs 12.5 rule 4).
 */
@Composable
internal fun RideTop(clock: String, ui: RideUi) {
    val c = Atlas.colors
    val bikeInk = if (ui.bikeLive) c.ink else c.ink2
    val stale = if (ui.bikeLive) "" else ", last known"
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BasicText(clock, Modifier.weight(1f).clearAndSetSemantics { contentDescription = "Time $clock" }, style = glance(35.dp, 600), maxLines = 1)
        Box(
            Modifier.size(66.dp).border(3.dp, bikeInk, CircleShape).clearAndSetSemantics { contentDescription = "Gear ${ui.gear}, probable$stale" },
            contentAlignment = Alignment.Center,
        ) {
            BasicText(ui.gear, style = glance(42.dp, color = bikeInk))
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (ui.fuelBars != null) {
                FuelBars(ui.fuelBars, ui.fuelMax, Modifier.alpha(if (ui.bikeLive) 1f else STALE_ALPHA), height = 46.dp, barWidth = 13.dp, gap = 5.dp)
            } else {
                BasicText("fuel …", style = Atlas.type.kicker.copy(color = c.ink3))
            }
        }
    }
    Box(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp), contentAlignment = Alignment.Center) {
        SourceLine(ui.bikeSource, ui.readingAt)
    }
    Hairline(thickness = 2.dp, color = c.ink)
}

private const val STALE_ALPHA = 0.55f

/** Caller in big type: rust while ringing, ink once answered (ride.css `.r-call`). */
@Composable
internal fun RideCall(call: RideCallUi) {
    val c = Atlas.colors
    val bg = if (call.answered) c.ink else c.rust
    val fg = if (call.answered) c.paper else c.onRust
    Row(
        Modifier.fillMaxWidth().background(bg, AtlasShapes.small).padding(horizontal = 18.dp, vertical = 16.dp)
            .clearAndSetSemantics { contentDescription = "Call from ${call.name}, ${call.label}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AtlasIcon(if (call.source == AlertSource.WHATSAPP) AtlasIcons.wa else AtlasIcons.call, tint = fg, size = 40.dp)
        Column(Modifier.weight(1f)) {
            BasicText(call.name, style = glance(37.dp, 650, fg).copy(letterSpacing = (-0.02).em), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(call.label.uppercase(), Modifier.padding(top = 2.dp), style = Atlas.type.kicker.copy(color = fg.copy(alpha = 0.9f)))
        }
    }
}

/** Next turn: arrow and distance, or a status word, or "Maps…" while waiting (ride.css `.r-turn`). */
@Composable
internal fun RideTurn(turn: TurnUi) {
    val c = Atlas.colors
    val spoken = when (turn) {
        TurnUi.Waiting -> "Waiting for Google Maps"
        is TurnUi.Status -> turn.word
        is TurnUi.Turn -> "Next turn in ${turn.value} ${turn.unit}"
    }
    Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken }) {
        Row(Modifier.padding(top = 8.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            when (turn) {
                TurnUi.Waiting -> {
                    AtlasIcon(AtlasIcons.maps, tint = c.ink3, size = 80.dp)
                    BasicText("Maps…", style = glance(48.dp, 500).copy(fontStyle = FontStyle.Italic))
                }
                is TurnUi.Status -> {
                    TurnArrow(turn.code, size = 118.dp, color = if (turn.arrived) c.moss else c.amber, contentDescription = null)
                    BasicText(turn.word, style = glance(48.dp, 500).copy(fontStyle = FontStyle.Italic), maxLines = 2)
                }
                is TurnUi.Turn -> {
                    TurnArrow(turn.code, size = 118.dp, color = c.rust, contentDescription = null)
                    BasicText(
                        buildAnnotatedString {
                            append(turn.value)
                            withStyle(SpanStyle(fontSize = glance(32.dp).fontSize, fontStyle = FontStyle.Italic, fontWeight = FontWeight(400), color = c.ink2, letterSpacing = 0.em)) { append(" ${turn.unit}") }
                        },
                        style = glance(80.dp, 750),
                        maxLines = 1,
                    )
                }
            }
        }
        Hairline(thickness = 2.dp, color = c.rule2)
    }
}

/** The speed, as large as the space allows (ride.css `.r-speed`), rust above the speed alert. */
@Composable
internal fun RideSpeed(ui: RideUi, modifier: Modifier) {
    val c = Atlas.colors
    val text = ui.speedKmh?.toString() ?: "—"
    BoxWithConstraints(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = if (ui.speedKmh == null) "No GPS speed yet" else "Speed ${ui.speedKmh} kilometres per hour" }, contentAlignment = Alignment.Center) {
        val byWidth = maxWidth / (0.62f * text.length.coerceAtLeast(2))
        val size = min(min(byWidth, maxHeight * 0.7f), 208.dp)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText(text, style = glance(size, 800, if (ui.overLimit) c.rust else c.ink).copy(letterSpacing = (-0.05).em), maxLines = 1)
            BasicText("km/h", Modifier.padding(top = 6.dp), style = Atlas.type.serifItalic.copy(fontSize = Atlas.type.serifItalic.fontSize * 1.6f, color = c.ink2))
        }
    }
}

/** Ring glyph before "Hold anywhere to leave" (ride.css `.r-hold i`). */
@Composable
internal fun HoldGlyph(color: Color) {
    Box(Modifier.size(18.dp).border(2.dp, color, CircleShape), contentAlignment = Alignment.Center) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
    }
}
