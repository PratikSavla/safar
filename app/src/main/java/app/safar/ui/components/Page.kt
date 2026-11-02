package app.safar.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.safar.ui.art.AtlasIcons
import app.safar.ui.theme.Atlas

/**
 * Atlas page frame: paper, contour layer, edge-to-edge scrolling content padded clear of the
 * system bars, a scroll viewport that ends above the keyboard (the window is edge-to-edge, so
 * adjustResize no longer shrinks it), a paper scrim under the status bar, and an optional bottom bar
 * (the ticket-stub tabs). [overlay] draws above everything (sheets, toasts).
 */
@Composable
fun AtlasPage(
    modifier: Modifier = Modifier,
    topo: TopoTransform = TopoTransform(),
    scrollState: ScrollState = rememberScrollState(),
    bottomBar: (@Composable () -> Unit)? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Atlas.colors
    val pad = Atlas.dimens.pad
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(modifier.fillMaxSize().background(c.paper)) {
        TopoBackground(transform = topo)
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .imePadding()
                .verticalScroll(scrollState)
                .padding(top = statusTop + 8.dp, start = pad, end = pad),
        ) {
            content()
            BottomClearance(tabs = bottomBar != null)
        }
        StatusScrim(statusTop)
        if (bottomBar != null) Box(Modifier.align(Alignment.BottomCenter)) { bottomBar() }
        overlay()
    }
}

/** Paper fading out under the status bar, so content scrolls away beneath it. */
@Composable
internal fun StatusScrim(statusTop: Dp) {
    val c = Atlas.colors
    Spacer(
        Modifier.fillMaxWidth().height(statusTop + 4.dp)
            .background(Brush.verticalGradient(0.55f to c.paper, 1f to c.paper.copy(alpha = 0f))),
    )
}

/**
 * Space after the last item: clears the tabs (if any) and the gesture bar. The keyboard is
 * cleared by the page's imePadding, which consumes the nav-bar inset while the keyboard is open.
 */
@Composable
internal fun BottomClearance(tabs: Boolean) {
    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    Spacer(Modifier.height(if (tabs) 104.dp else 40.dp))
}

/**
 * Inner page header (`.top`): back arrow (48 dp), kicker and serif title with an optional italic tail.
 * System back should call the same [onBack].
 */
@Composable
fun BackTopBar(kicker: String, title: String, onBack: () -> Unit, modifier: Modifier = Modifier, titleEm: String? = null) {
    Row(modifier.fillMaxWidth().padding(top = 6.dp, bottom = 22.dp), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        Box(
            Modifier
                .offset(x = (-12).dp)
                .size(Atlas.dimens.touchTarget)
                .clickable(onClickLabel = "Go back", role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            AtlasIcon(AtlasIcons.back, contentDescription = "Back", size = 24.dp)
        }
        Column(Modifier.weight(1f).padding(top = 4.dp)) {
            Kicker(kicker)
            DisplayTitle(title, titleEm, Atlas.type.displayMedium, Modifier.padding(top = 4.dp).semantics { heading() })
        }
    }
}

/** Fixed spacer helper for page layouts. */
@Composable
fun Gap(height: Dp) = Spacer(Modifier.height(height))
