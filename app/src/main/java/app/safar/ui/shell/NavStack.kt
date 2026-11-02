package app.safar.ui.shell

import app.safar.ui.nav.Route

/** One page on the back stack. [id] keys its saved UI state and its ViewModels. */
data class StackEntry(val id: Long, val route: Route)

/** Which way the last move went: picks the page-in or page-back animation. */
enum class NavDirection { FORWARD, BACK }

/**
 * The back stack as a value (app.js `stack`). Pure, so the rules are unit-tested:
 * - going to HOME clears the stack (HOME is the root; back does nothing there),
 * - going to the page already on top does nothing,
 * - back at a root other than HOME and ONBOARDING (Pair after onboarding, Ride, a shared place) lands on HOME.
 */
data class NavStack(
    val entries: List<StackEntry> = emptyList(),
    val nextId: Long = 1,
    val direction: NavDirection = NavDirection.FORWARD,
) {
    val top: StackEntry? get() = entries.lastOrNull()

    /** True when [back] would change something; system back is left to Android otherwise. */
    val canGoBack: Boolean
        get() = entries.size > 1 || (top != null && top?.route !in ROOTS)

    fun go(route: Route): NavStack = when {
        route == Route.Home -> resetTo(route)
        top?.route == route -> this
        else -> copy(entries = entries + StackEntry(nextId, route), nextId = nextId + 1, direction = NavDirection.FORWARD)
    }

    fun back(): NavStack = when {
        entries.size > 1 -> copy(entries = entries.dropLast(1), direction = NavDirection.BACK)
        canGoBack -> copy(entries = listOf(StackEntry(nextId, Route.Home)), nextId = nextId + 1, direction = NavDirection.BACK)
        else -> this
    }

    fun resetTo(route: Route): NavStack {
        if (entries.size == 1 && top?.route == route) return this
        return copy(entries = listOf(StackEntry(nextId, route)), nextId = nextId + 1, direction = NavDirection.FORWARD)
    }

    /** Entry ids in this stack that are gone in [next]: their saved state and ViewModels can be dropped. */
    fun removedIn(next: NavStack): List<Long> {
        val kept = next.entries.mapTo(HashSet()) { it.id }
        return entries.map { it.id }.filterNot { it in kept }
    }

    fun encode(): ArrayList<String> = entries.mapTo(ArrayList()) { RouteCodec.encode(it.route) }

    companion object {
        private val ROOTS = setOf(Route.Home, Route.Onboarding)

        /** Rebuilds a saved stack with fresh ids; unreadable entries are skipped. Empty when nothing is usable. */
        fun decode(saved: List<String>?): NavStack {
            val routes = saved.orEmpty().mapNotNull(RouteCodec::decode)
            val entries = routes.mapIndexed { i, r -> StackEntry(i + 1L, r) }
            return NavStack(entries, nextId = entries.size + 1L)
        }
    }
}
