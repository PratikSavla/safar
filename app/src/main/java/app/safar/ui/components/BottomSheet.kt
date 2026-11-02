package app.safar.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.safar.ui.theme.Atlas

private val SheetEase = CubicBezierEasing(0.22f, 0.8f, 0.24f, 1f)

/**
 * Paper sheet with a torn (zig-zag) top edge (`.sheet.paper`), over a warm scrim.
 * Place it last inside a full-screen Box (e.g. `AtlasPage(overlay = { ... })`).
 * System back, a tap on the scrim, or TalkBack's "close" dismiss it.
 */
@Composable
fun AtlasBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(enabled = visible, onBack = onDismiss)
    BoxWithConstraints(modifier.fillMaxSize()) {
        val maxSheet = maxHeight * 0.86f
        AnimatedVisibility(visible, enter = fadeIn(tween(300)), exit = fadeOut(tween(300))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Atlas.colors.scrim)
                    .clickable(remember { MutableInteractionSource() }, indication = null, onClickLabel = "Close sheet", onClick = onDismiss),
            )
        }
        AnimatedVisibility(
            visible,
            Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(420, easing = SheetEase)) { it },
            exit = slideOutVertically(tween(320, easing = SheetEase)) { it },
        ) {
            SheetBody(maxSheet.value, title, onDismiss, content)
        }
    }
}

@Composable
private fun SheetBody(maxHeightDp: Float, title: String?, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = Atlas.colors
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeightDp.dp)
            .background(c.card, TornTopShape())
            .pointerInput(Unit) { detectTapGestures { } } // taps on paper never reach the scrim
            .semantics {
                dialog()
                if (title != null) paneTitle = title
                dismiss("Close sheet") { onDismiss(); true } // not onClick: the paper is not a button
            }
            .imePadding() // paper runs behind the keyboard, the fields scroll above it
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = Atlas.dimens.pad, end = Atlas.dimens.pad, top = 30.dp, bottom = 28.dp),
    ) {
        if (title != null) DisplayTitle(title, null, Atlas.type.displaySmall, Modifier.padding(top = 6.dp, bottom = 10.dp))
        content()
    }
}

/** Zig-zag top edge, 14 dp teeth (the prototype's conic-gradient mask). */
class TornTopShape(private val tooth: Float = 14f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = with(density) { tooth.dp.toPx() }
        val h = w / 2
        val path = Path().apply {
            moveTo(0f, h)
            var x = 0f
            while (x < size.width) {
                lineTo(x + w / 2, 0f)
                lineTo(x + w, h)
                x += w
            }
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean = other is TornTopShape && other.tooth == tooth

    override fun hashCode(): Int = tooth.hashCode()
}
