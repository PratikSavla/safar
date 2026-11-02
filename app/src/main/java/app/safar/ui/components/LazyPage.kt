package app.safar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.safar.ui.theme.Atlas

/**
 * [AtlasPage] for a list that grows without bound (fuel history, the journal, the debug log): the same paper,
 * contours, insets, status scrim, bottom bar and [overlay], but the content is a LazyColumn, so only the rows
 * on screen are composed and drawn, and keyed rows keep their state when new ones arrive at the top.
 * Put the page's header pieces in `item { }` blocks and the rows in `items(...)`.
 */
@Composable
fun AtlasLazyPage(
    modifier: Modifier = Modifier,
    topo: TopoTransform = TopoTransform(),
    state: LazyListState = rememberLazyListState(),
    bottomBar: (@Composable () -> Unit)? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val pad = Atlas.dimens.pad
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(modifier.fillMaxSize().background(Atlas.colors.paper)) {
        TopoBackground(transform = topo)
        LazyColumn(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .imePadding(),
            state = state,
            contentPadding = PaddingValues(top = statusTop + 8.dp, start = pad, end = pad),
        ) {
            content()
            item(key = PAGE_END_KEY, contentType = PAGE_END_KEY) { BottomClearance(tabs = bottomBar != null) }
        }
        StatusScrim(statusTop)
        if (bottomBar != null) Box(Modifier.align(Alignment.BottomCenter)) { bottomBar() }
        overlay()
    }
}

private const val PAGE_END_KEY = "atlas-page-end"
