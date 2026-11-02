package app.safar.ui.art

import androidx.compose.ui.graphics.vector.ImageVector

private fun icon(name: String, block: LineArtScope.() -> Unit) = lazy { lineArt("atlas.$name", block = block) }

/**
 * Atlas icon set: hand-inked line glyphs on a 24 grid, 1.7 stroke, round caps (ux/variant-b/js/icons.js).
 * Names match the prototype. Tint with [AtlasIcon] or an Image colour filter.
 */
object AtlasIcons {
    val today by icon("today") {
        circle(12f, 12f, 8.5f); path("M12 6.5v1.6M12 15.9v1.6M6.5 12h1.6M15.9 12h1.6")
        path("m10 14 1.2-3.2L14 10l-1.2 3.2z", filled = true)
    }
    val route by icon("route") {
        circle(6f, 18f, 2f); path("M8 18h5a3 3 0 0 0 0-6h-2a3 3 0 0 1 0-6h3")
        path("M18 3.5c-1.7 0-3 1.3-3 2.9 0 2.2 3 5.1 3 5.1s3-2.9 3-5.1c0-1.6-1.3-2.9-3-2.9z"); circle(18f, 6.4f, 0.9f, filled = true)
    }
    val journal by icon("journal") {
        path("M5 4.5h11a2 2 0 0 1 2 2v13H7a2 2 0 0 1-2-2z"); path("M5 17.5a2 2 0 0 1 2-2h11")
        path("M9 8h5M9 11h3"); path("M15 4.5v5l1.5-1 1.5 1v-5")
    }
    val alerts by icon("alerts") { path("M4 6.5h16v10H9l-5 3.5z"); path("M8 10h8M8 13h5") }
    val settings by icon("settings") {
        path("M5 4v4M5 12v8M12 4v10M12 18v2M19 4v2M19 10v10")
        circle(5f, 10f, 2f); circle(12f, 16f, 2f); circle(19f, 8f, 2f)
    }
    val back by icon("back") { path("M19 12H5.5M11 6l-6 6 6 6") }
    val chev by icon("chev") { path("m9.5 6 6 6-6 6") }
    val arrow by icon("arrow") { path("M5 12h13.5M13 6l6 6-6 6") }
    val ride by icon("ride") {
        path("M4.5 15a7.5 7.5 0 0 1 15 0"); path("M12 15l3.6-4.2"); circle(12f, 15f, 1.4f, filled = true)
        path("M6.5 15h-.8M18.3 15h-.8M8 9.8l-.6-.6M16 9.8l.6-.6M12 7.5v-.9")
    }
    val fuel by icon("fuel") {
        path("M5 20V6a2 2 0 0 1 2-2h5a2 2 0 0 1 2 2v14M3.5 20h12"); path("M7.5 8h4")
        path("M14 11h1.5a1.5 1.5 0 0 1 1.5 1.5V16a1.3 1.3 0 0 0 2.6 0V9l-2.4-2.6")
    }
    val service by icon("service") { path(WRENCH) }
    val parked by icon("parked") { path(PIN_OUTLINE); path("M10.2 13V7h2.3a1.8 1.8 0 0 1 0 3.6h-2.3") }
    val bike by icon("bike") {
        circle(5.5f, 16f, 3.5f); circle(18.5f, 16f, 3.5f)
        path("M5.5 16 9 10h6l3.5 6M9 10l2.5 6h2l1.5-6M14.5 7.5h2.5l1.5 2.5")
    }
    val log by icon("log") { rect(3.5f, 5f, 17f, 14f, 1.5f); path("m7 10 2.5 2L7 14M11.5 14.5H16") }
    val call by icon("call") { path(HANDSET) }
    val wa by icon("wa") {
        path("M4.5 19.5 5.6 16A8 8 0 1 1 8.4 18.6z")
        path(
            "M9.3 8.6c.2-.4.6-.4.8 0l.6 1.4-.6.8c.5 1 1.3 1.8 2.3 2.3l.8-.6 1.4.6c.4.2.4.6 0 .8-.6.6-1.7.8-3-.1a7 7 0 0 1-2.4-2.4c-.7-1.2-.5-2.3 0-2.8z",
            filled = true,
            stroked = false,
        )
    }
    val msg by icon("msg") { rect(3.5f, 5.5f, 17f, 13f, 1.5f); path("m4 6.5 8 6 8-6") }
    val missed by icon("missed") { path(HANDSET); path("m15 4 5 5M20 4l-5 5") }
    val check by icon("check") { path("m5 12.5 4.5 4.5L19 7") }
    val cross by icon("cross") { path("M6 6l12 12M18 6 6 18") }
    val share by icon("share") {
        circle(6f, 12f, 2.3f); circle(17.5f, 6f, 2.3f); circle(17.5f, 18f, 2.3f)
        path("m8 11 7.4-3.9M8 13l7.4 3.9")
    }
    val bt by icon("bt") { path("m7 7.5 10 9-5 4.5v-18l5 4.5-10 9") }
    val gps by icon("gps") {
        circle(12f, 12f, 6f); circle(12f, 12f, 1.8f, filled = true); path("M12 2.5V6M12 18v3.5M2.5 12H6M18 12h3.5")
    }
    val star by icon("star") { path("m12 4 2.4 5 5.3.6-4 3.6 1.1 5.3L12 15.8 7.2 18.5l1.1-5.3-4-3.6 5.3-.6z") }
    val home by icon("home") { path("M4 11.5 12 5l8 6.5M6.5 9.5V19h11V9.5"); path("M10 19v-5h4v5") }
    val work by icon("work") { rect(3.5f, 7.5f, 17f, 11.5f, 1.5f); path("M9 7.5V5.5h6v2M3.5 12.5h17") }
    val flag by icon("flag") { path("M6 21V4M6 4.5h11l-2.5 4 2.5 4H6") }
    val clock by icon("clock") { circle(12f, 12f, 8.5f); path("M12 7.5V12l3 2") }
    val plus by icon("plus") { path("M12 5v14M5 12h14") }
    val minus by icon("minus") { path("M5 12h14") }
    val drop by icon("drop") { path("M12 3.5s6 6.4 6 10.5a6 6 0 0 1-12 0c0-4.1 6-10.5 6-10.5z") }
    val chain by icon("chain") { rect(2.5f, 9f, 9f, 6f, 3f); rect(12.5f, 9f, 9f, 6f, 3f); path("M9.5 12h5") }
    val wind by icon("wind") { path("M3 9h11a3 3 0 1 0-3-3M3 13h15a3 3 0 1 1-3 3M3 17h7") }
    val disc by icon("disc") { circle(12f, 12f, 8.5f); circle(12f, 12f, 2.5f); path("M12 5v1M19 12h-1M12 19v-1M5 12h1") }
    val gauge by icon("gauge") { path("M4 16a8 8 0 1 1 16 0"); path("m12 16 4-5"); path("M4 19.5h16") }
    val search by icon("search") { circle(11f, 11f, 6f); path("m15.5 15.5 4.5 4.5") }
    val info by icon("info") { circle(12f, 12f, 8.5f); path("M12 11v5.5M12 7.6v.2") }
    val edit by icon("edit") { path("M15 5.5 18.5 9 9 18.5H5.5V15z"); path("m13 7.5 3.5 3.5") }
    val lock by icon("lock") { rect(5.5f, 10.5f, 13f, 9.5f, 1.5f); path("M8.5 10.5V8a3.5 3.5 0 0 1 7 0v2.5") }
    val play by icon("play") { path("M8 5.5v13l10-6.5z") }
    val stop by icon("stop") { rect(6.5f, 6.5f, 11f, 11f, 1f) }
    val export by icon("export") { path("M12 15V4M7.5 8.5 12 4l4.5 4.5M5 14v5.5h14V14") }
    val bell by icon("bell") { path("M6.5 16.5V11a5.5 5.5 0 0 1 11 0v5.5l1.5 1.5H5z"); path("M10 20.5h4") }
    val maps by icon("maps") { path("M4 6.5 9 4.5l6 2 5-2v13l-5 2-6-2-5 2z"); path("M9 4.5v13M15 6.5v13") }
    val sun by icon("sun") {
        circle(12f, 12f, 4f); path("M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6 7 7M17 17l1.4 1.4M5.6 18.4 7 17M17 7l1.4-1.4")
    }
    val moon by icon("moon") { path("M19 14.5A7.5 7.5 0 0 1 9.5 5a7.5 7.5 0 1 0 9.5 9.5z") }
    val phone by icon("phone") { rect(6.5f, 3f, 11f, 18f, 2f); path("M10.5 18h3") }
    val pin by icon("pin") { path(PIN_OUTLINE); circle(12f, 10f, 2.3f) }
    val wrench by icon("wrench") { path(WRENCH) }

    /** Every icon by its prototype name, for catalogs and data-driven lists. */
    val all: Map<String, ImageVector>
        get() = mapOf(
            "today" to today, "route" to route, "journal" to journal, "alerts" to alerts, "settings" to settings,
            "back" to back, "chev" to chev, "arrow" to arrow, "ride" to ride, "fuel" to fuel, "service" to service,
            "parked" to parked, "bike" to bike, "log" to log, "call" to call, "wa" to wa, "msg" to msg,
            "missed" to missed, "check" to check, "cross" to cross, "share" to share, "bt" to bt, "gps" to gps,
            "star" to star, "home" to home, "work" to work, "flag" to flag, "clock" to clock, "plus" to plus,
            "minus" to minus, "drop" to drop, "chain" to chain, "wind" to wind, "disc" to disc, "gauge" to gauge,
            "search" to search, "info" to info, "edit" to edit, "lock" to lock, "play" to play, "stop" to stop,
            "export" to export, "bell" to bell, "maps" to maps, "sun" to sun, "moon" to moon, "phone" to phone,
            "pin" to pin, "wrench" to wrench,
        )

    /** Icon by prototype name; unknown names fall back to [info] like icons.js. */
    fun byName(name: String): ImageVector = all[name] ?: info
}

private const val WRENCH = "M14.5 4.5a4 4 0 0 0-4.8 5.3L4 15.5 6.5 18l5.7-5.7a4 4 0 0 0 5.3-4.8l-2.3 2.3-2.3-.5-.5-2.3z"
private const val PIN_OUTLINE = "M12 21s6.5-5.6 6.5-11a6.5 6.5 0 0 0-13 0c0 5.4 6.5 11 6.5 11z"
private const val HANDSET =
    "M6.5 4h3l1.5 4-2 1.3a10 10 0 0 0 5.7 5.7L16 13l4 1.5v3a2 2 0 0 1-2.2 2A15.5 15.5 0 0 1 4.5 6.2 2 2 0 0 1 6.5 4z"
