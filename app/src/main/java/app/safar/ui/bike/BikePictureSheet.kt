package app.safar.ui.bike

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.safar.core.BikeStyle
import app.safar.ui.art.AtlasIcons
import app.safar.ui.art.BikeSilhouette
import app.safar.ui.art.label
import app.safar.ui.components.AtlasBottomSheet
import app.safar.ui.components.Kicker
import app.safar.ui.components.Note
import app.safar.ui.components.PrimaryButton
import app.safar.ui.components.TextAction
import app.safar.ui.theme.Atlas
import app.safar.ui.theme.AtlasShapes

/** What the picture sheet can do. */
class PictureActions(
    val onStyle: (BikeStyle?) -> Unit = {},
    val onChoosePhoto: () -> Unit = {},
    val onRemovePhoto: () -> Unit = {},
)

/**
 * Change the bike's picture: a photo from the phone, or one of the drawings. The paired model's drawing is
 * marked; choosing it again follows the model.
 */
@Composable
internal fun BikePictureSheet(visible: Boolean, picture: BikePictureUi, busy: Boolean, actions: PictureActions, onDismiss: () -> Unit) {
    AtlasBottomSheet(visible, onDismiss, title = "Your bike’s picture") {
        Kicker("Photo")
        val photoLabel = if (picture.photo == null) "Choose a photo" else "Choose another photo"
        PrimaryButton(if (busy) "Adding…" else photoLabel, actions.onChoosePhoto, Modifier.fillMaxWidth().padding(top = 8.dp), icon = AtlasIcons.plus, enabled = !busy)
        if (picture.photo != null) {
            TextAction("Remove the photo", actions.onRemovePhoto, Modifier.padding(top = 6.dp), icon = AtlasIcons.cross, enabled = !busy)
        }
        Note("The photo stays on this phone. It is not part of Export everything.", Modifier.padding(top = 6.dp), color = Atlas.colors.ink3)
        Kicker(if (picture.photo == null) "Drawing" else "Drawing, when there is no photo", Modifier.padding(top = 18.dp, bottom = 8.dp))
        StyleGrid(picture) { actions.onStyle(styleChoice(picture, it)) }
    }
}

@Composable
private fun StyleGrid(picture: BikePictureUi, onTap: (BikeStyle) -> Unit) {
    val c = Atlas.colors
    FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BikeStyle.entries.forEach { style ->
            val on = style == picture.style
            Column(
                Modifier
                    .width(104.dp)
                    .border(if (on) 2.dp else Atlas.dimens.outline, if (on) c.ink else c.rule2, AtlasShapes.small)
                    .selectable(on, role = Role.RadioButton, onClick = { onTap(style) })
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BikeSilhouette(style, width = 84.dp, color = if (on) c.ink else c.ink2)
                BasicText(style.label(), Modifier.padding(top = 4.dp), style = Atlas.type.uiSmall.copy(color = if (on) c.ink else c.ink2))
                if (style == picture.modelStyle) {
                    BasicText("your model", style = Atlas.type.src.copy(color = c.moss))
                }
            }
        }
    }
}
