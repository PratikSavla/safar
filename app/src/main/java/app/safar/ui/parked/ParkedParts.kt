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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.safar.data.ParkedSpot
import app.safar.ui.components.AtlasCard
import app.safar.ui.components.CardStyle
import app.safar.ui.components.DisplayTitle
import app.safar.ui.components.InkButton
import app.safar.ui.components.Note
import app.safar.ui.components.SourceLine
import app.safar.ui.components.Stamp
import app.safar.ui.components.dashedOutline
import app.safar.ui.components.rememberNow
import app.safar.ui.format.Formatters
import app.safar.ui.theme.Atlas
import kotlin.math.min
import kotlin.math.roundToInt

private const val ACCURACY_FULL_M = 25f

/** Stamp, place and "2 h ago · accuracy ±8 m" (`.park-place`). */
@Composable
internal fun PlaceHeader(spot: ParkedSpot, place: String) {
    val now = rememberNow()
    Column(Modifier.padding(bottom = 6.dp)) {
        Stamp("parked", tilted = false)
        DisplayTitle(place, null, Atlas.type.displaySmall.copy(fontSize = Atlas.type.displaySmall.fontSize * 1.19f), Modifier.padding(top = 8.dp, bottom = 4.dp))
        BasicText(
            "${Formatters.age(spot.atMillis, now.value)} · accuracy ±${spot.accuracyM.roundToInt()} m",
            style = Atlas.type.src.copy(color = Atlas.colors.ink3),
        )
    }
}

/** The compass, the distance and the direction in words (`.compass-wrap`, `.cw-read`). */
@Composable
internal fun CompassBlock(state: ParkedUiState, actions: ParkedActions) {
    val c = Atlas.colors
    val distance = state.distanceM
    val bearing = state.bearingDeg
    val description = when {
        distance == null || bearing == null -> "Compass. Direction to the bike unknown: no phone location yet."
        state.here -> "You are at the bike."
        else -> "The bike is ${distanceText(distance)} away, ${dirWord(bearing.toDouble())}."
    }
    Column(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ParkedCompass(if (state.here) null else state.pointerDeg, state.roseDeg, description)
        val big = Atlas.type.numberLarge.copy(textAlign = TextAlign.Center, color = c.ink)
        val words = Atlas.type.serifItalic.copy(color = c.ink2, textAlign = TextAlign.Center)
        when {
            !state.locationAllowed -> LocationOff(actions.onAllowLocation, Modifier.padding(top = 10.dp))
            distance == null || bearing == null -> BasicText("Finding the phone’s location…", Modifier.padding(top = 10.dp), style = words)
            state.here -> {
                BasicText("here", Modifier.padding(top = 10.dp), style = big)
                BasicText("you are at the bike", style = words)
            }
            else -> {
                BasicText(distanceText(distance), Modifier.padding(top = 10.dp), style = big)
                BasicText("$bearing° · ${dirWord(bearing.toDouble())}", style = words)
            }
        }
        val fixAt = state.phoneFix?.atMillis
        if (fixAt != null) SourceLine("phone location", fixAt, Modifier.padding(top = 4.dp))
        if (state.spot != null) SourceLine("parked spot · phone location", state.spot.atMillis, Modifier.padding(top = 2.dp))
    }
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
        val hold = if (hasCompass) {
            "Hold the phone flat and the arrow points to the bike."
        } else {
            "No compass reading on this phone: the arrow shows the direction from north, with north at the top."
        }
        Note("The spot is good to about ${spot.accuracyM.roundToInt()} m. $hold", Modifier.weight(1f))
    }
}

/** Location permission is off: say what it is for and offer to allow it. */
@Composable
internal fun LocationOff(onAllow: () -> Unit, modifier: Modifier = Modifier) {
    AtlasCard(modifier, style = CardStyle.LINE) {
        Note("Safar needs the phone’s location to show how far the bike is and which way to walk.")
        InkButton("Allow location", onAllow, Modifier.padding(top = 10.dp), small = true)
    }
}

@Composable
internal fun ParkedFootnote(text: String) {
    BasicText(text, Modifier.padding(top = 22.dp), style = Atlas.type.src.copy(color = Atlas.colors.ink3))
}
