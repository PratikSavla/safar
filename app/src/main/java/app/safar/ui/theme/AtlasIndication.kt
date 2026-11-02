package app.safar.ui.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.launch

/**
 * Atlas press feedback: a flat ink wash while pressed. No ripple, no animation.
 * Provided as LocalIndication by [SafarTheme], so plain `clickable` uses it.
 */
class AtlasIndication(private val wash: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        WashNode(interactionSource, wash)

    override fun equals(other: Any?): Boolean = other is AtlasIndication && other.wash == wash

    override fun hashCode(): Int = wash.hashCode()
}

private class WashNode(
    private val source: InteractionSource,
    private val wash: Color,
) : Modifier.Node(), DrawModifierNode {
    private var pressed = 0

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> pressed++
                    is PressInteraction.Release, is PressInteraction.Cancel -> pressed = (pressed - 1).coerceAtLeast(0)
                }
                invalidateDraw()
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (pressed > 0) drawRect(wash)
    }
}
