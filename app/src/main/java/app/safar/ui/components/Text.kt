package app.safar.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.safar.ui.format.FreshnessText
import app.safar.ui.format.ValueSource
import app.safar.ui.theme.Atlas
import java.util.Locale

/** Mono uppercase label above titles and values (`.kicker`). */
@Composable
fun Kicker(text: String, modifier: Modifier = Modifier, color: Color = Atlas.colors.ink3) {
    BasicText(text.uppercase(Locale.ROOT), modifier, style = Atlas.type.kicker.copy(color = color), maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Section heading: mono label, hairline to the edge, optional aside (`.sec`). */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, aside: (@Composable () -> Unit)? = null) {
    val c = Atlas.colors
    Row(
        modifier.fillMaxWidth().padding(top = Atlas.dimens.sectionGap, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BasicText(
            title.uppercase(Locale.ROOT),
            Modifier.semantics { heading() },
            style = Atlas.type.section.copy(color = c.ink2),
            maxLines = 1,
        )
        Hairline(Modifier.weight(1f), color = c.rule2)
        aside?.invoke()
    }
}

/**
 * Old caption "↳ bike · 3 s ago". Now Developer mode only: a free-text source and a ready-made age cannot tell
 * whether the value is stale, so normal mode shows nothing. Use [Freshness] for values, or plain copy for notes.
 */
@Deprecated("Use Freshness(source, atMillis) for values; plain copy (Note) for anything a rider must read.")
@Composable
fun SourceLine(source: String, modifier: Modifier = Modifier, age: String? = null) {
    DevOnly { DeveloperLine(if (age == null) "↳ $source" else "↳ $source · $age", modifier) }
}

/**
 * Old live caption. Delegates to [Freshness]: the full line in Developer mode, a calm note only when stale in
 * normal mode. The source is guessed from the text; a "last known" source counts as a dropped link.
 */
@Deprecated("Use Freshness(ValueSource, atMillis, connected = ...).")
@Composable
fun SourceLine(source: String, atMillis: Long?, modifier: Modifier = Modifier) {
    val connected = "last known" !in source
    if (LocalDeveloperMode.current) {
        DeveloperFreshness(modifier) { now, zone -> FreshnessText.developer(source, atMillis, now, zone = zone) }
    } else {
        val kind = legacySource(source)
        Freshness(kind, atMillis, modifier, connected = connected)
    }
}

/** Best guess of a [ValueSource] from an old free-text source label. */
internal fun legacySource(label: String): ValueSource = when {
    "Google Maps" in label -> ValueSource.GOOGLE_MAPS
    "phone" in label || "GPS" in label -> ValueSource.PHONE_GPS
    "bike" in label -> ValueSource.BIKE
    else -> ValueSource.CALCULATED
}

/** Serif introduction paragraph (`.lede`). */
@Composable
fun Lede(text: String, modifier: Modifier = Modifier) {
    BasicText(text, modifier, style = Atlas.type.lede.copy(color = Atlas.colors.ink2))
}

/** Small explanatory text (`.note`). */
@Composable
fun Note(text: String, modifier: Modifier = Modifier, color: Color = Atlas.colors.ink2) {
    BasicText(text, modifier, style = Atlas.type.note.copy(color = color))
}

/**
 * Tab page masthead (`.mast`): kicker row with an optional right-hand note, then a big serif
 * title whose [titleEm] tail is set light and italic, e.g. "Where to" + "?".
 */
@Composable
fun Masthead(kicker: String, title: String, modifier: Modifier = Modifier, kickerAside: String? = null, titleEm: String? = null) {
    Column(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 18.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Kicker(kicker, Modifier.weight(1f))
            if (kickerAside != null) Kicker(kickerAside)
        }
        Spacer(Modifier.height(10.dp))
        DisplayTitle(title, titleEm, Atlas.type.displayLarge, Modifier.semantics { heading() })
    }
}

@Preview
@Composable
private fun TextPreview() = PreviewSurface {
    Masthead("Route · with Google Maps", "Where to", titleEm = "?")
    SectionHeader("Your places") { Kicker("3 saved") }
    Freshness(ValueSource.BIKE, atMillis = null)
    Lede("Safar sends turns, callers and the time to your bike’s display.")
}
