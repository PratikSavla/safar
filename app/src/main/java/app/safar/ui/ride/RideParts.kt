package app.safar.ui.ride

import android.text.format.DateFormat
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
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
import app.safar.ui.components.Freshness
import app.safar.ui.components.Hairline
import app.safar.ui.components.rememberNow
import app.safar.ui.components.rememberStale
import app.safar.ui.copy.Phrases
import app.safar.ui.format.Formatters
import app.safar.ui.home.NO_TEMP
import app.safar.ui.home.engineTempSpoken
import app.safar.ui.theme.Atlas
import app.safar.ui.theme.AtlasShapes
import app.safar.ui.theme.FrauncesGlance

/** Heavy glance numerals sized in dp, so the huge figures fit the screen at any font scale. */
@Composable
internal fun glance(size: Dp, weight: Int = 800, color: Color = Atlas.colors.ink): TextStyle {
    val sp: TextUnit = with(LocalDensity.current) { size.toSp() }
    return TextStyle(fontFamily = FrauncesGlance, fontWeight = FontWeight(weight), fontSize = sp, lineHeight = sp, letterSpacing = (-0.04).em, color = color, fontFeatureSettings = "lnum, tnum")
}

/** "14:05": reads the shared clock at minute steps in its own scope, so a tick recomposes this text only. */
@Composable
private fun RideClock(modifier: Modifier) {
    val is24h = DateFormat.is24HourFormat(LocalContext.current)
    val now by rememberNow(periodMillis = 60_000)
    val clock = Formatters.clock(now, is24h = is24h).substringBefore(' ')
    BasicText(clock, modifier.clearAndSetSemantics { contentDescription = "Time $clock" }, style = glance(35.dp, 600), maxLines = 1)
}

/**
 * Clock, engine temperature in a ring, fuel bars, over a 2 dp ink rule (ride.css `.r-top`). Temperature and fuel
 * fade to ink-2 once stale or from the last ride, and only then say so underneath; nothing shows while fresh.
 */
@Composable
internal fun RideTop(ui: RideUi) {
    val c = Atlas.colors
    val stale = rememberStale(ui.bikeOrigin)
    val bikeInk = if (stale) c.ink2 else c.ink
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RideClock(Modifier.weight(1f))
        EngineTempRing(ui.engineTemp, bikeInk)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (ui.fuelBars != null) {
                FuelBars(ui.fuelBars, ui.fuelMax, Modifier.alpha(if (stale) STALE_ALPHA else 1f), height = 46.dp, barWidth = 13.dp, gap = 5.dp)
            } else {
                BasicText("FUEL —", Modifier.clearAndSetSemantics { contentDescription = "Fuel, no reading yet" }, style = Atlas.type.kicker.copy(color = c.ink3))
            }
        }
    }
    Box(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp), contentAlignment = Alignment.Center) {
        Freshness(ui.bikeOrigin)
    }
    Hairline(thickness = 2.dp, color = c.ink)
}

private const val STALE_ALPHA = 0.55f

/** Engine temperature in the 66 dp ring: the number, and "°C" under it once there is a reading. */
@Composable
private fun EngineTempRing(temp: String, ink: Color) {
    Column(
        Modifier.size(66.dp).border(3.dp, ink, CircleShape).clearAndSetSemantics { contentDescription = engineTempSpoken(temp) },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(temp, style = glance(if (temp.length > 2) 22.dp else 28.dp, color = ink), maxLines = 1)
        if (temp != NO_TEMP) BasicText("°C", Modifier.padding(top = 1.dp), style = glance(11.dp, 600, ink))
    }
}

/** Caller in big type: rust while ringing, ink once answered (ride.css `.r-call`). */
@Composable
internal fun RideCall(call: RideCallUi) {
    val c = Atlas.colors
    val bg = if (call.answered) c.ink else c.rust
    val fg = if (call.answered) c.paper else c.onRust
    Row(
        Modifier.fillMaxWidth().background(bg, AtlasShapes.small).padding(horizontal = 18.dp, vertical = 16.dp)
            .clearAndSetSemantics { contentDescription = "Call from ${call.name}, ${call.label.replace(" · ", ", ")}" },
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
        TurnUi.Waiting -> Phrases.WAITING_FOR_MAPS
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

/** Largest speed numeral, in dp. */
private val MAX_SPEED_DP = 208.dp
private const val UNIT_GAP_DP = 6

/** Speed numeral size, and whether "km/h" sits beside it (a wide, short box: landscape) instead of under it. */
internal data class SpeedLayout(val numeral: Dp, val unitBeside: Boolean)

/**
 * Sizes the numeral so the measured unit ([unitWidth] x [unitHeight], which grows with the font scale) always
 * fits: under the number, or beside it when that leaves a larger number.
 */
internal fun speedLayout(maxWidth: Dp, maxHeight: Dp, chars: Int, unitWidth: Dp, unitHeight: Dp): SpeedLayout {
    val perChar = 0.62f * chars.coerceAtLeast(2)
    val gap = UNIT_GAP_DP.dp
    val stacked = min(min(maxWidth / perChar, min(maxHeight * 0.7f, maxHeight - unitHeight - gap)), MAX_SPEED_DP)
    val beside = min(min((maxWidth - unitWidth - gap) / perChar, maxHeight * 0.85f), MAX_SPEED_DP)
    return if (beside > stacked) SpeedLayout(beside.coerceAtLeast(0.dp), true) else SpeedLayout(stacked.coerceAtLeast(0.dp), false)
}

/** The speed, as large as the space allows (ride.css `.r-speed`), rust above the speed alert. */
@Composable
internal fun RideSpeed(ui: RideUi, modifier: Modifier) {
    val c = Atlas.colors
    val text = ui.speedKmh?.toString() ?: "—"
    val unitStyle = Atlas.type.serifItalic.copy(fontSize = Atlas.type.serifItalic.fontSize * 1.6f, color = c.ink2)
    val unit = rememberTextMeasurer().measure("km/h", unitStyle).size
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = if (ui.speedKmh == null) Phrases.WAITING_FOR_GPS else "Speed ${ui.speedKmh} kilometres per hour" }, contentAlignment = Alignment.Center) {
        val layout = with(density) { speedLayout(maxWidth, maxHeight, text.length, unit.width.toDp(), unit.height.toDp()) }
        val number = @Composable { BasicText(text, style = glance(layout.numeral, 800, if (ui.overLimit) c.rust else c.ink).copy(letterSpacing = (-0.05).em), maxLines = 1) }
        if (layout.unitBeside) {
            Row(verticalAlignment = Alignment.Bottom) {
                number()
                BasicText("km/h", Modifier.padding(start = UNIT_GAP_DP.dp), style = unitStyle, maxLines = 1)
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                number()
                BasicText("km/h", Modifier.padding(top = UNIT_GAP_DP.dp), style = unitStyle, maxLines = 1)
            }
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
