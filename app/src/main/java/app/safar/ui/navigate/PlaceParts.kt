package app.safar.ui.navigate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.safar.data.PlaceSource
import app.safar.ui.art.AtlasIcons
import app.safar.ui.components.AtlasCard
import app.safar.ui.components.AtlasTextField
import app.safar.ui.components.CardStyle
import app.safar.ui.components.DevOnly
import app.safar.ui.components.Gap
import app.safar.ui.components.IconBox
import app.safar.ui.components.Kicker
import app.safar.ui.components.Lede
import app.safar.ui.components.LockLine
import app.safar.ui.components.Note
import app.safar.ui.components.PrimaryButton
import app.safar.ui.components.SecondaryButton
import app.safar.ui.components.SegmentedChoice
import app.safar.ui.components.Stamp
import app.safar.ui.components.StampTone
import app.safar.ui.components.TextAction
import app.safar.ui.theme.Atlas

/** The three ways a place comes in (`.src-opt`). Typing is off while moving. */
@Composable
internal fun SourceChooser(lockReason: String?, actions: PlaceActions) {
    Lede("Google Maps does the searching. Bring a place to Safar in one of three ways.")
    Gap(18.dp)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SourceOption(AtlasIcons.maps, "Share from Google Maps", "in Maps: open a place → Share → Safar", actions.onMaps) {
            Stamp("best", tone = StampTone.MOSS)
        }
        SourceOption(AtlasIcons.pin, "Where I am now", "your phone’s last known location", actions.onHere)
        SourceOption(
            AtlasIcons.edit,
            "Type an address",
            if (lockReason != null) "stop the bike to type" else "Google Maps finds it when you start",
            actions.onTyped,
            enabled = lockReason == null,
        )
    }
    Note("Everything stays on this phone. Nothing is looked up online until you start a route in Maps.", Modifier.padding(top = 14.dp), color = Atlas.colors.ink3)
}

@Composable
private fun SourceOption(icon: ImageVector, title: String, sub: String, onClick: () -> Unit, enabled: Boolean = true, trailing: (@Composable () -> Unit)? = null) {
    val c = Atlas.colors
    AtlasCard(
        Modifier.alpha(if (enabled) 1f else 0.45f),
        style = CardStyle.TICKET,
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
        onClick = if (enabled) onClick else null,
        onClickLabel = title,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconBox(icon)
            Column(Modifier.weight(1f)) {
                BasicText(title, style = Atlas.type.serifTitle.copy(color = c.ink))
                BasicText(sub, style = Atlas.type.src.copy(color = c.ink3), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            trailing?.invoke()
        }
    }
}

/** Source card or address field, name, mark and Save (`.placepg`); the Maps query in Developer mode. */
@Composable
internal fun PlaceForm(draft: PlaceDraft, state: PlaceState, actions: PlaceActions) {
    val lock = state.lockReason
    if (draft.source == PlaceSource.TYPED) {
        AtlasTextField(draft.query, actions.onQuery, "Address or place name", lockedReason = lock, placeholder = "e.g. Main Rd, Sample Town")
    } else {
        SourceCard(draft, canChange = !state.editing, onChange = actions.onChangeSource)
    }
    AtlasTextField(draft.label, actions.onLabel, "Name in your list", lockedReason = lock, placeholder = "e.g. Home", maxLength = PLACE_LABEL_MAX)
    Kicker("Mark", Modifier.padding(top = 18.dp, bottom = 8.dp))
    SegmentedChoice(PlaceKindLabels, draft.kind, actions.onKind)
    DevOnly {
        val query = if (draft.query.isBlank()) "…" else draft.query
        BasicText("↳ Maps gets ${readableNavUri(query)}", Modifier.padding(top = 16.dp), style = Atlas.type.src.copy(color = Atlas.colors.ink3))
    }
    Gap(14.dp)
    PrimaryButton(
        if (state.editing) "Save changes" else "Save to destinations",
        actions.onSave,
        Modifier.fillMaxWidth().heightIn(min = 58.dp),
        icon = AtlasIcons.check,
    )
    if (state.editing) {
        Gap(10.dp)
        SecondaryButton(
            if (state.confirmDelete) "Tap again to delete" else "Delete",
            actions.onDelete,
            Modifier.fillMaxWidth(),
            icon = AtlasIcons.cross,
        )
    }
    if (lock != null && draft.source != PlaceSource.TYPED) LockLine(lock)
}

@Composable
private fun SourceCard(draft: PlaceDraft, canChange: Boolean, onChange: () -> Unit) {
    val (name, tone) = draft.source.stamp()
    AtlasCard(style = CardStyle.LINE, contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
        Stamp(
            name,
            tone = when (tone) {
                SourceTone.MOSS -> StampTone.MOSS
                SourceTone.SLATE -> StampTone.SLATE
                SourceTone.INK -> StampTone.INK
            },
        )
        BasicText(
            draft.detail.ifBlank { draft.query },
            Modifier.padding(top = 8.dp),
            style = Atlas.type.serifTitle.copy(color = Atlas.colors.ink),
        )
        val link = draft.link
        if (link != null) {
            DevOnly {
                BasicText(link, Modifier.padding(top = 4.dp), style = Atlas.type.src.copy(color = Atlas.colors.ink3), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (canChange) TextAction("Change", onChange, Modifier.padding(top = 4.dp))
    }
}
