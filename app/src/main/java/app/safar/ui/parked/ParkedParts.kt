package app.safar.ui.parked

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.safar.data.ParkedSpot
import app.safar.ui.components.AtlasCard
import app.safar.ui.components.CardStyle
import app.safar.ui.components.DisplayTitle
import app.safar.ui.components.Freshness
import app.safar.ui.components.FreshnessNote
import app.safar.ui.components.InkButton
import app.safar.ui.components.Note
import app.safar.ui.components.Stamp
import app.safar.ui.components.byMode
import app.safar.ui.components.dashedOutline
import app.safar.ui.components.developerMode
import app.safar.ui.components.liveAge
import app.safar.ui.components.rememberNow
import app.safar.ui.format.ValueSource
import app.safar.ui.theme.Atlas
import java.time.ZoneId
import kotlin.math.min
import kotlin.math.roundToInt

private const val ACCURACY_FULL_M = 25f

/**
 * "Parked 2 h ago"; Developer mode: "↳ phone GPS · 2 h ago · ±8 m". Its own scope, so a clock
 * tick recomposes this line only, and in normal mode only when the words change.
 */
@Composable
private fun ParkedAge(spot: ParkedSpot) {
    val style = Atlas.type.src.copy(color = Atlas.colors.ink3)
    if (developerMode()) {
        BasicText("↳ phone GPS · ${liveAge(spot.atMillis)} · ±${spot.accuracyM.roundToInt()} m", style = style)
        return
    }
    val zone = remember { ZoneId.systemDefault() }
    val now = rememberNow()
    val text by remember(spot.atMillis, now) { derivedStateOf { parkedAgo(spot.atMillis, now.value, zone) } }
    BasicText(text, style = style)
}

/** Stamp, place and "Parked 2 h ago" (`.park-place`). */
@Composable
internal fun PlaceHeader(spot: ParkedSpot, place: String) {
    Column(Modifier.padding(bottom = 6.dp)) {
        Stamp("parked", tilted = false)
        DisplayTitle(place, null, Atlas.type.displaySmall.copy(fontSize = Atlas.type.displaySmall.fontSize * 1.19f), Modifier.padding(top = 8.dp, bottom = 4.dp))
        ParkedAge(spot)
    }
}

/** The compass, the distance and the direction in words (`.compass-wrap`, `.cw-read`). */
@Composable
internal fun CompassBlock(state: ParkedUiState, actions: ParkedActions) {
    val c = Atlas.colors
    val distance = state.distanceM
    val bearing = state.bearingDeg
    val description = compassDescription(state)
    Column(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ParkedCompass(if (state.here) null else state.pointerDeg, state.roseDeg, description)
        val big = Atlas.type.numberLarge.copy(textAlign = TextAlign.Center, color = c.ink)
        val words = Atlas.type.serifItalic.copy(color = c.ink2, textAlign = TextAlign.Center)
        when {
            !state.locationAllowed -> LocationOff(actions.onAllowLocation, Modifier.padding(top = 10.dp))
            distance == null || bearing == null -> BasicText("Finding your location…", Modifier.padding(top = 10.dp), style = words)
            state.here -> {
                BasicText("here", Modifier.padding(top = 10.dp), style = big)
                BasicText("you’re at your bike", style = words)
            }
            else -> {
                BasicText(distanceText(distance), Modifier.padding(top = 10.dp), style = big)
                BasicText(byMode(directionText(bearing), "$bearing° · ${dirWord(bearing.toDouble())}"), style = words)
            }
        }
        val fixAt = state.phoneFix?.atMillis
        if (fixAt != null) PhoneFixAge(fixAt)
    }
}

/** How old the phone's own position is, said as such; the exact age in Developer mode. Nothing while fresh. */
@Composable
private fun PhoneFixAge(fixAt: Long) {
    if (developerMode()) {
        Freshness(ValueSource.PHONE_GPS, fixAt, Modifier.padding(top = 4.dp))
        return
    }
    val zone = remember { ZoneId.systemDefault() }
    val now = rememberNow()
    val note by remember(fixAt, now) { derivedStateOf { phoneFixNote(fixAt, now.value, zone) } }
    note?.let { FreshnessNote(it, Modifier.padding(top = 4.dp)) }
}

/** Accuracy ring and how to hold the phone (`.acc`). */
@Composable
internal fun AccuracyNote(spot: ParkedSpot, hasCompass: Boolean) {
    val c = Atlas.colors
    val a = min(1f, spot.accuracyM / ACCURACY_FULL_M)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 18.dp).dashedOutline(c.rule2).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Canvas(Modifier.size(40.dp)) {
            drawCircle(c.rustWash, size.minDimension / 2 * 0.7f * a.coerceAtLeast(0.15f))
            drawCircle(c.rule2, size.minDimension / 2, style = Stroke(1.dp.toPx()))
            drawCircle(c.rust, 4.dp.toPx())
        }
        Note(accuracyText(spot.accuracyM, hasCompass), Modifier.weight(1f))
    }
}

/** Location permission is off: say what it is for and offer to allow it. */
@Composable
internal fun LocationOff(onAllow: () -> Unit, modifier: Modifier = Modifier) {
    AtlasCard(modifier, style = CardStyle.LINE) {
        Note("Allow location to see how far away your bike is and which way to walk.")
        InkButton("Allow location", onAllow, Modifier.padding(top = 10.dp), small = true)
    }
}

@Composable
internal fun ParkedFootnote(text: String) {
    BasicText(text, Modifier.padding(top = 22.dp), style = Atlas.type.src.copy(color = Atlas.colors.ink3))
}
