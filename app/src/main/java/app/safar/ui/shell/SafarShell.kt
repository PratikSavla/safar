package app.safar.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import app.safar.ui.components.AtlasToastHost
import app.safar.ui.components.TicketStubTabs
import app.safar.ui.components.rememberToastState
import app.safar.ui.components.safarTabs
import app.safar.ui.nav.Route
import app.safar.ui.theme.Atlas
import app.safar.ui.theme.SafarTheme

private val PageEase = CubicBezierEasing(0.22f, 0.8f, 0.24f, 1f)

/**
 * The whole app: theme from Settings, the current page with the prototype's page-in / page-back motion,
 * the ticket stubs on tab pages, the call telegram, toasts, the auto ride stamp and system back.
 */
@Composable
fun SafarShell(shell: ShellViewModel) {
    val settings by shell.settingsState.collectAsStateWithLifecycle()
    val stack by shell.stack.collectAsStateWithLifecycle()
    val chrome by shell.chrome.collectAsStateWithLifecycle()
    val autoRide by shell.autoRide.collectAsStateWithLifecycle()
    SafarTheme(settings.theme) {
        val toast = rememberToastState()
        var flash by remember { mutableIntStateOf(0) }
        LaunchedEffect(shell) {
            shell.events.collect { e ->
                when (e) {
                    is ShellEvent.Toast -> toast.show(e.message)
                    ShellEvent.RideFlash -> flash += 1
                }
            }
        }
        KeepScreenOn(chrome.keepScreenOn)
        val ui = remember(shell, autoRide) { shellUi(shell, autoRide) }
        CompositionLocalProvider(LocalShellUi provides ui) {
            Box(Modifier.fillMaxSize().background(Atlas.colors.paper)) {
                BackHandler(enabled = stack.canGoBack, onBack = shell::back)
                val callSpace by animateDpAsState(if (chrome.call != null) TelegramSpace else 0.dp, tween(400, easing = PageEase), label = "callSpace")
                if (stack.top != null) PageHost(stack, shell, Modifier.padding(top = callSpace))
                TabsBar(stack.top?.route, chrome) { shell.go(it) }
                CallTelegram(chrome.call, onOpen = { shell.go(Route.Alerts) }, modifier = Modifier.align(Alignment.TopCenter))
                RideFlash(flash)
                AtlasToastHost(toast, Modifier.align(Alignment.BottomCenter), bottomPadding = if (showsTabs(stack.top?.route)) 120.dp else 56.dp)
            }
        }
    }
}

private fun shellUi(shell: ShellViewModel, auto: Boolean): ShellUi = object : ShellUi {
    override fun toast(message: String) = shell.toast(message)
    override val autoRide: Boolean = auto
    override fun leaveRide(stillMoving: Boolean) = shell.leaveRide(stillMoving)
}

/** The top entry, with its own saved UI state and ViewModels, animated like `.page.fwd` / `.page.back`. */
@Composable
private fun PageHost(stack: NavStack, shell: ShellViewModel, modifier: Modifier) {
    val holder = rememberSaveableStateHolder()
    val known = remember { HashSet<Long>() }
    val live = stack.entries.mapTo(HashSet()) { it.id }
    SideEffect {
        // Popped pages forget their scroll positions and typing; a page still animating out keeps it until it leaves.
        (known - live).forEach(holder::removeState)
        known.retainAll(live)
        known.addAll(live)
    }
    val density = LocalDensity.current
    val fwd = with(density) { 14.dp.roundToPx() }
    val bck = with(density) { (-10).dp.roundToPx() }
    AnimatedContent(
        targetState = stack.top ?: return,
        modifier = modifier.fillMaxSize(),
        contentKey = { it.id },
        transitionSpec = {
            val back = stack.direction == NavDirection.BACK
            val ms = if (back) 380 else 420
            (fadeIn(tween(ms, easing = PageEase)) + slideInVertically(tween(ms, easing = PageEase)) { if (back) bck else fwd })
                .togetherWith(fadeOut(tween(120)))
        },
        label = "page",
    ) { entry ->
        holder.SaveableStateProvider(entry.id) {
            val owner = rememberEntryOwner(entry.id) { shell.storeFor(entry.id) }
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                RouteHost(entry.route, shell)
            }
        }
    }
}

@Composable
private fun BoxScope.TabsBar(route: Route?, chrome: ShellChrome, onSelect: (Route) -> Unit) {
    val key = route?.let(::tabOf).orEmpty()
    AnimatedVisibility(
        showsTabs(route),
        Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically(tween(380, easing = PageEase)) { it },
        exit = slideOutVertically(tween(380, easing = PageEase)) { it },
    ) {
        TicketStubTabs(safarTabs(chrome.alertBadge, chrome.navigating), key, onSelect = { k -> TAB_ROUTES[k]?.let(onSelect) })
    }
}
