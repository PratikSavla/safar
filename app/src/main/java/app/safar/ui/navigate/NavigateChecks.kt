package app.safar.ui.navigate

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.safar.ui.art.AtlasIcons
import app.safar.ui.components.AtlasCard
import app.safar.ui.components.AtlasIcon
import app.safar.ui.components.CardStyle
import app.safar.ui.components.DashedRule
import app.safar.ui.components.InkButton
import app.safar.ui.components.Stamp
import app.safar.ui.components.StampTone
import app.safar.ui.settings.AccessStatus
import app.safar.ui.theme.Atlas
import app.safar.ui.theme.AtlasShapes

/** Google Maps status card (`.checks`): Maps installed, permission to read its alerts, two-wheeler tip. */
@Composable
internal fun ChecksCard(access: AccessStatus, onGrant: () -> Unit, modifier: Modifier = Modifier) {
    AtlasCard(modifier, style = CardStyle.LINE, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
        CheckRow(
            ok = access.mapsInstalled,
            title = "Google Maps installed",
            sub = if (access.mapsInstalled) "Google Maps finds the route. Safar passes its turns to your bike." else "Install Google Maps to see turns on your bike.",
        )
        DashedRule(thickness = 1.dp, dash = 4.dp, gap = 3.dp)
        CheckRow(
            ok = access.notificationAccess,
            title = "Read Google Maps alerts",
            sub = if (access.notificationAccess) "Allowed · Safar can see the turns" else "Needed so Safar can see the turns",
        ) {
            if (!access.notificationAccess) InkButton("Allow", onGrant, small = true)
        }
        DashedRule(thickness = 1.dp, dash = 4.dp, gap = 3.dp)
        CheckRow(
            ok = null,
            title = "Two-wheeler mode",
            sub = "In Google Maps, pick the two-wheeler tab before you start. Routes and arrival times then suit a bike.",
        ) { Stamp("tip", tone = StampTone.SLATE) }
    }
}

/** [ok] null = a tip (slate square with a bike), true = moss tick, false = rust cross. */
@Composable
private fun CheckRow(ok: Boolean?, title: String, sub: String, trailing: (@Composable () -> Unit)? = null) {
    val c = Atlas.colors
    val state = when (ok) {
        true -> "Done"
        false -> "Not yet"
        null -> "Tip"
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (ok) {
            true -> Mark(AtlasIcons.check, c.moss, CircleShape, state)
            false -> Mark(AtlasIcons.cross, c.rust, CircleShape, state)
            null -> Mark(AtlasIcons.bike, c.slate, AtlasShapes.small, state)
        }
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
            BasicText(title, style = Atlas.type.uiStrong.copy(color = c.ink))
            BasicText(sub, Modifier.padding(top = 2.dp), style = Atlas.type.uiSmall.copy(color = c.ink3))
        }
        trailing?.invoke()
    }
}

@Composable
private fun Mark(icon: ImageVector, color: Color, shape: Shape, description: String) {
    Box(Modifier.size(30.dp).border(1.5.dp, color, shape), contentAlignment = Alignment.Center) {
        AtlasIcon(icon, contentDescription = description, tint = color, size = 16.dp)
    }
}
