package app.safar.ui.format

import app.safar.protocol.Timing
import java.time.Instant
import java.time.ZoneId

/** A bike value is stale after six missed cluster frames (one every 5 s): 30 s. */
const val BIKE_STALE_AFTER_MS: Long = Timing.CLUSTER_PERIOD_MS * 6

/** Phone GPS reports about once a second while moving; 10 s without a fix is stale. */
const val GPS_STALE_AFTER_MS: Long = 10_000L

/** For values that only change when something happens (Maps turns, calculations, typed values). */
const val NEVER_STALE: Long = Long.MAX_VALUE

/**
 * Where a value comes from. [label] is the Developer mode word ("bike", "phone GPS"); [waiting] is what a rider
 * sees before the first reading while the source is connected, or null to show nothing.
 */
enum class ValueSource(val label: String, val staleAfterMillis: Long, val waiting: String?) {
    /** Cluster frames: odometer, trips, fuel bars, gear. */
    BIKE("bike", BIKE_STALE_AFTER_MS, "Waiting for the bike"),

    /** The phone's own location: speed, parked spot, track. */
    PHONE_GPS("phone GPS", GPS_STALE_AFTER_MS, "Waiting for GPS"),

    /** The Google Maps turn notification. Maps posts only on change, so a quiet minute is not stale. */
    GOOGLE_MAPS("Google Maps", NEVER_STALE, "Waiting for Google Maps"),

    /** Worked out by Safar from other values (range, averages). */
    CALCULATED("calculated", NEVER_STALE, null),

    /** Typed or set by the rider. */
    RIDER("you", NEVER_STALE, null),
}

/**
 * Freshness wording from the voice guide (docs/14-voice.md). In normal mode a value says nothing while it is
 * fresh and a calm note once it is stale; the exact source and age are Developer mode only. Ages never go
 * below a minute in normal mode. Pure: tests pass `now` and a zone.
 */
object FreshnessText {
    private const val MINUTE_MS = 60_000L
    private const val HOUR_MS = 60 * MINUTE_MS
    private const val WEEK_DAYS = 7

    /** True when a reading taken at [atMillis] is older than [staleAfterMillis] at [nowMillis]. */
    fun isStale(atMillis: Long, nowMillis: Long, staleAfterMillis: Long): Boolean =
        staleAfterMillis != NEVER_STALE && nowMillis - atMillis > staleAfterMillis

    /**
     * The normal-mode note, or null when there is nothing to say (fresh, or no reading while disconnected).
     * [connected] is false when the source has stopped sending (the bike link is down): a bike value then
     * comes from the last ride.
     */
    fun note(
        source: ValueSource,
        atMillis: Long?,
        nowMillis: Long,
        connected: Boolean = true,
        staleAfterMillis: Long = source.staleAfterMillis,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String? = when {
        atMillis == null -> if (connected) source.waiting else null
        !connected && source == ValueSource.BIKE -> lastRide(atMillis, nowMillis, zone)
        isStale(atMillis, nowMillis, staleAfterMillis) -> lastSeen(atMillis, nowMillis, zone)
        else -> null
    }

    /** "Last seen 12 min ago", "Last seen today, 14:05", "Last seen yesterday, 18:40", "Last seen 12 Mar". */
    fun lastSeen(atMillis: Long, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        "Last seen ${calmAge(atMillis, nowMillis, zone)}"

    /** "From your last ride" (today), "From your last ride · yesterday", "· 3 days ago", "· 12 Mar". */
    fun lastRide(atMillis: Long, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val days = daysBetween(atMillis, nowMillis, zone)
        return when {
            days <= 0 -> "From your last ride"
            days == 1L -> "From your last ride · yesterday"
            days < WEEK_DAYS -> "From your last ride · $days days ago"
            else -> "From your last ride · ${Formatters.shortDate(localDate(atMillis, zone))}"
        }
    }

    /**
     * An age with no seconds: "under a minute ago", "12 min ago", "today, 14:05", "yesterday, 18:40",
     * "3 days ago", then "12 Mar". Future timestamps (clock skew) read as "under a minute ago".
     */
    fun calmAge(atMillis: Long, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault(), is24h: Boolean = true): String {
        val ms = (nowMillis - atMillis).coerceAtLeast(0)
        val days = daysBetween(atMillis, nowMillis, zone)
        return when {
            ms < MINUTE_MS -> "under a minute ago"
            ms < HOUR_MS -> "${ms / MINUTE_MS} min ago"
            days <= 0 -> "today, ${Formatters.clock(atMillis, zone, is24h)}"
            days == 1L -> "yesterday, ${Formatters.clock(atMillis, zone, is24h)}"
            days < WEEK_DAYS -> "$days days ago"
            else -> Formatters.shortDate(localDate(atMillis, zone))
        }
    }

    /**
     * The Developer mode line: "↳ bike · 3 s ago", "↳ bike · Device Information · last known · 2 min ago",
     * "↳ phone GPS · waiting". [detail] adds a technical note after the source.
     */
    fun developer(
        source: ValueSource,
        atMillis: Long?,
        nowMillis: Long,
        connected: Boolean = true,
        detail: String? = null,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String = developer(source.label, atMillis, nowMillis, connected, detail, zone)

    /** [developer] with a free-text source label, for the deprecated string-based source lines. */
    fun developer(
        label: String,
        atMillis: Long?,
        nowMillis: Long,
        connected: Boolean = true,
        detail: String? = null,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val parts = listOfNotNull(
            label,
            detail,
            "last known".takeIf { !connected && atMillis != null },
            if (atMillis == null) "waiting" else Formatters.age(atMillis, nowMillis, zone),
        )
        return "↳ " + parts.joinToString(" · ")
    }

    private fun localDate(atMillis: Long, zone: ZoneId) = Instant.ofEpochMilli(atMillis).atZone(zone).toLocalDate()

    private fun daysBetween(atMillis: Long, nowMillis: Long, zone: ZoneId): Long =
        localDate(nowMillis, zone).toEpochDay() - localDate(atMillis, zone).toEpochDay()
}
