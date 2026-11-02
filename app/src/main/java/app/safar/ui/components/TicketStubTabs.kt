package app.safar.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.safar.ui.art.AtlasIcons
import app.safar.ui.theme.Atlas
import app.safar.ui.theme.AtlasShapes
import java.util.Locale

/** One ticket stub: [badge] counts unread alerts, [dot] marks live navigation. */
data class StubTab(val key: String, val label: String, val icon: ImageVector, val badge: Int = 0, val dot: Boolean = false)

/** The five Atlas sections in order: Today, Route, Trips, Alerts, Settings. The keys keep their prototype names. */
fun safarTabs(alertCount: Int = 0, navigating: Boolean = false): List<StubTab> = listOf(
    StubTab("today", "Today", AtlasIcons.today),
    StubTab("route", "Route", AtlasIcons.route, dot = navigating),
    StubTab("journal", "Trips", AtlasIcons.journal),
    StubTab("alerts", "Alerts", AtlasIcons.alerts, badge = alertCount),
    StubTab("settings", "Settings", AtlasIcons.settings),
)

/**
 * Ticket stubs torn along the bottom page edge (chrome.css `.stubs`). The current stub is a
 * notched card with a rust dot and an italic label; it is marked, not raised.
 */
@Composable
fun TicketStubTabs(tabs: List<StubTab>, selectedKey: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = Atlas.colors
    Column(modifier.fillMaxWidth()) {
        Spacer(Modifier.fillMaxWidth().height(10.dp).background(Brush.verticalGradient(listOf(c.paper.copy(alpha = 0f), c.paper))))
        Box(Modifier.fillMaxWidth().background(c.paper2)) {
            Perforation()
            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 6.dp)
                    .selectableGroup(),
            ) {
                tabs.forEachIndexed { i, tab ->
                    val on = tab.key == selectedKey
                    val dashed = i > 0 && !on && tabs[i - 1].key != selectedKey
                    Stub(tab, on, dashed, Modifier.weight(1f)) { onSelect(tab.key) }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.Perforation() {
    val c = Atlas.colors
    Canvas(Modifier.matchParentSize()) {
        drawLine(c.rule, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
        val step = 14.dp.toPx()
        var x = step / 2
        while (x < size.width) {
            drawCircle(c.paper, 3.dp.toPx(), Offset(x, 0f))
            x += step
        }
    }
}

@Composable
private fun Stub(tab: StubTab, on: Boolean, dashedLeft: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Atlas.colors
    val spoken = buildString {
        append(tab.label)
        if (tab.badge > 0) append(", ${tab.badge} new")
        if (tab.dot) append(", navigating")
    }
    Box(
        modifier
            .height(56.dp)
            .selectable(on, role = Role.Tab, onClick = onClick)
            .clearAndSetSemantics { contentDescription = spoken; role = Role.Tab; selected = on },
    ) {
        if (dashedLeft) DashedEdge()
        if (on) StubCard()
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
            AtlasIcon(tab.icon, tint = if (on) c.ink else c.ink3)
            if (on) {
                BasicText(tab.label, style = Atlas.type.tabLabelActive.copy(color = c.ink), maxLines = 1)
            } else {
                BasicText(tab.label.uppercase(Locale.ROOT), style = Atlas.type.tabLabel.copy(color = c.ink3), maxLines = 1)
            }
        }
        Marks(tab, on)
    }
}

@Composable
private fun BoxScope.StubCard() {
    val c = Atlas.colors
    val shape = TicketShape(notch = 5.dp, corner = 4.dp, at = 0.62f)
    Box(
        Modifier.matchParentSize().padding(horizontal = 2.dp)
            // Shadows use the plain rounded rect: a blurred shadow of the notched path renders as a hard block.
            .paperShadow(
                AtlasShapes.small,
                PaperShadow(blur = 10.dp, color = c.shadow.copy(alpha = c.shadow.alpha * 0.5f), spread = (-4).dp, offsetY = 4.dp, edgeColor = c.shadowEdge),
            )
            .background(c.card, shape),
    )
    Box(Modifier.align(Alignment.TopEnd).offset(x = (-12).dp, y = 6.dp).size(7.dp).background(c.rust, CircleShape))
}

@Composable
private fun BoxScope.Marks(tab: StubTab, on: Boolean) {
    val c = Atlas.colors
    if (tab.badge > 0) {
        Box(
            Modifier.align(Alignment.TopCenter).offset(x = 12.dp, y = 4.dp).sizeIn(minWidth = 16.dp, minHeight = 16.dp)
                .background(c.rust, CircleShape).padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(if (tab.badge > 99) "99+" else tab.badge.toString(), style = Atlas.type.stamp.copy(fontSize = Atlas.type.tabLabel.fontSize, letterSpacing = 0.em, color = c.onRust))
        }
    }
    if (tab.dot && !on) {
        Box(Modifier.align(Alignment.TopCenter).offset(x = 12.dp, y = 8.dp).size(7.dp).background(c.moss, CircleShape))
    }
}

@Composable
private fun BoxScope.DashedEdge() {
    val color = Atlas.colors.rule2
    Canvas(Modifier.matchParentSize()) {
        drawLine(
            color, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
        )
    }
}
