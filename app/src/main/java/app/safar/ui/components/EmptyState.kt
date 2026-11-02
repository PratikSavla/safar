package app.safar.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.safar.ui.theme.Atlas

/** Nothing here yet (`.empty`): dashed outline, italic serif line, a short hint and an optional action. */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = Atlas.colors
    Column(
        modifier.fillMaxWidth().dashedOutline(c.rule2).padding(horizontal = 18.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(title, style = Atlas.type.serifItalic.copy(fontSize = Atlas.type.serifItalic.fontSize * 1.15f, color = c.ink2, textAlign = TextAlign.Center))
        if (message != null) {
            BasicText(message, Modifier.padding(top = 4.dp), style = Atlas.type.note.copy(color = c.ink3, textAlign = TextAlign.Center))
        }
        if (actionLabel != null && onAction != null) TextAction(actionLabel, onAction, Modifier.padding(top = 8.dp))
    }
}

/** 1.5 dp dashed rounded outline. */
fun Modifier.dashedOutline(color: Color, radius: Float = 6f) = drawBehind {
    val w = 1.5.dp.toPx()
    drawRoundRect(
        color, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), CornerRadius(radius.dp.toPx()),
        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
    )
}

@Preview
@Composable
private fun EmptyStatePreview() = PreviewSurface {
    EmptyState("No rides yet", message = "Connect the bike and ride; trips land here by themselves.", actionLabel = "Pair a bike", onAction = {})
}
