package app.safar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.safar.ui.theme.Atlas
import app.safar.ui.theme.AtlasColors
import app.safar.ui.theme.AtlasShapes

/** Card surfaces: raised paper with a long soft shadow, a ruled outline, or a notched ticket. */
enum class CardStyle { RAISED, LINE, TICKET }

/**
 * Atlas card (`.card`, `.card.line`, `.ticket`). Pass [onClick] to make the whole card one
 * 48 dp+ touch target with [onClickLabel] for TalkBack.
 */
@Composable
fun AtlasCard(
    modifier: Modifier = Modifier,
    style: CardStyle = CardStyle.RAISED,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Atlas.colors
    val shape: Shape = if (style == CardStyle.TICKET) TicketShape() else AtlasShapes.card
    val surface = when (style) {
        CardStyle.RAISED -> modifier.atlasShadow(shape, c).background(c.card, shape)
        CardStyle.LINE -> modifier.background(c.card.copy(alpha = 0.7f), shape).border(1.dp, c.rule, shape)
        CardStyle.TICKET -> modifier.background(c.card, shape)
    }
    val clickable = if (onClick == null) Modifier else Modifier.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
    Column(surface.clip(shape).then(clickable).fillMaxWidth().padding(contentPadding), content = content)
}

/** The two-layer paper shadow from base.css `--shadow`. */
fun Modifier.atlasShadow(shape: Shape, colors: AtlasColors): Modifier = this
    .dropShadow(shape, Shadow(radius = 26.dp, spread = (-16).dp, offset = DpOffset(0.dp, 12.dp), color = colors.shadow))
    .dropShadow(shape, Shadow(radius = 0.dp, offset = DpOffset(0.dp, 1.dp), color = colors.shadowEdge))

/** Rounded rectangle with a semicircle notch cut from both sides (`.ticket`, the stub tabs). */
class TicketShape(
    private val notch: Dp = 8.dp,
    private val corner: Dp = 6.dp,
    /** Notch centre as a fraction of the height. */
    private val at: Float = 0.5f,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { notch.toPx() }
        val cr = with(density) { corner.toPx() }
        val body = Path().apply { addRoundRect(RoundRect(Rect(0f, 0f, size.width, size.height), CornerRadius(cr))) }
        val cy = size.height * at
        val holes = Path().apply {
            addOval(Rect(-r, cy - r, r, cy + r))
            addOval(Rect(size.width - r, cy - r, size.width + r, cy + r))
        }
        return Outline.Generic(Path.combine(PathOperation.Difference, body, holes))
    }

    override fun equals(other: Any?): Boolean = other is TicketShape && other.notch == notch && other.corner == corner && other.at == at

    override fun hashCode(): Int = (notch.hashCode() * 31 + corner.hashCode()) * 31 + at.hashCode()
}

@Preview
@Composable
private fun CardPreview() = PreviewSurface {
    AtlasCard { Kicker("Raised") }
    AtlasCard(Modifier.padding(top = 12.dp), style = CardStyle.LINE) { Kicker("Line") }
    AtlasCard(Modifier.padding(top = 12.dp), style = CardStyle.TICKET, onClick = {}) { Kicker("Ticket") }
}
