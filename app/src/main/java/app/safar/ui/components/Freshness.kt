package app.safar.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import app.safar.ui.format.FreshnessText
import app.safar.ui.format.ValueSource
import app.safar.ui.theme.Atlas
import java.time.ZoneId

/**
 * Where a value came from and when, to hand around with the value (UI state, [StatCell], [LedgerValue]).
 * [atMillis] null = no reading yet. [connected] false = the source stopped sending (bike link down).
 */
@Immutable
data class ValueOrigin(
    val source: ValueSource,
    val atMillis: Long?,
    val connected: Boolean = true,
    val staleAfterMillis: Long = source.staleAfterMillis,
    /** Developer mode only: a technical note after the source ("Device Information", "fuel-ups"). */
    val detail: String? = null,
)

/**
 * How fresh a value is. Normal mode: nothing while fresh, a calm note once stale ("Last seen 12 min ago",
 * "From your last ride · yesterday") or before the first reading ("Waiting for the bike"). Developer mode:
 * the full technical line, "↳ bike · 3 s ago". Reads the shared clock here only, so a tick never recomposes
 * the caller; in normal mode this recomposes only when the note's words change.
 */
@Composable
fun Freshness(
    source: ValueSource,
    atMillis: Long?,
    modifier: Modifier = Modifier,
    connected: Boolean = true,
    staleAfterMillis: Long = source.staleAfterMillis,
    detail: String? = null,
) {
    if (LocalDeveloperMode.current) {
        DeveloperFreshness(modifier) { now, zone -> FreshnessText.developer(source, atMillis, now, connected, detail, zone) }
    } else {
        CalmFreshness(source, atMillis, connected, staleAfterMillis, modifier)
    }
}

/** [Freshness] for a [ValueOrigin]. */
@Composable
fun Freshness(origin: ValueOrigin, modifier: Modifier = Modifier) {
    Freshness(origin.source, origin.atMillis, modifier, origin.connected, origin.staleAfterMillis, origin.detail)
}

/**
 * True once [origin] is stale or comes from a dropped link, for greying the value itself. The caller
 * recomposes only when the answer flips, not on every clock tick.
 */
@Composable
fun rememberStale(origin: ValueOrigin): Boolean {
    val now = rememberNow()
    val stale by remember(origin, now) {
        derivedStateOf {
            val at = origin.atMillis
            at != null && (!origin.connected || FreshnessText.isStale(at, now.value, origin.staleAfterMillis))
        }
    }
    return stale
}

@Composable
private fun CalmFreshness(source: ValueSource, atMillis: Long?, connected: Boolean, staleAfterMillis: Long, modifier: Modifier) {
    val zone = remember { ZoneId.systemDefault() }
    val now = rememberNow()
    // derivedStateOf: the clock ticks every second, but this text recomposes only when the words change.
    val note by remember(source, atMillis, connected, staleAfterMillis, now) {
        derivedStateOf { FreshnessText.note(source, atMillis, now.value, connected, staleAfterMillis, zone) }
    }
    note?.let { FreshnessNote(it, modifier) }
}

/** A calm, plain-language note under a value: no arrow, no mono. */
@Composable
internal fun FreshnessNote(text: String, modifier: Modifier = Modifier) {
    BasicText(text, modifier, style = Atlas.type.uiSmall.copy(color = Atlas.colors.ink2), maxLines = 2, overflow = TextOverflow.Ellipsis)
}

/** The Developer mode line, live to the second. [text] builds it from the clock. */
@Composable
internal fun DeveloperFreshness(modifier: Modifier, text: (now: Long, zone: ZoneId) -> String) {
    val zone = remember { ZoneId.systemDefault() }
    val now by rememberNow()
    DeveloperLine(text(now, zone), modifier)
}

/** Mono technical caption ("↳ bike · 3 s ago"). Two lines, so a narrow column at a large font scale wraps. */
@Composable
internal fun DeveloperLine(text: String, modifier: Modifier = Modifier) {
    BasicText(text, modifier, style = Atlas.type.src.copy(color = Atlas.colors.ink3), maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Preview
@Composable
private fun FreshnessPreview() = PreviewSurface {
    FreshnessNote("Last seen 12 min ago")
    FreshnessNote("From your last ride · yesterday")
    FreshnessNote("Waiting for the bike")
    DeveloperLine("↳ bike · 3 s ago")
}
