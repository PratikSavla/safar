package app.safar.data

import java.time.LocalDate

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Rider settings (DataStore). Every alert and feature is switchable (UX rule 6). */
data class Settings(
    val onboarded: Boolean = false,
    /** Shown on the cluster: ASCII, max 19 characters after cleaning. Empty until the rider sets it. */
    val riderName: String = "",
    val speedAlertOn: Boolean = false,
    val speedAlertKmh: Int = 100,
    val alertCalls: Boolean = true,
    val alertWhatsAppCalls: Boolean = true,
    val alertWhatsAppMessages: Boolean = true,
    val debugLog: Boolean = false,
    val hideNames: Boolean = true,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val keepScreenOn: Boolean = true,
    val autoRideMode: Boolean = true,
    val serviceReminders: Boolean = true,
    val lowFuelAlert: Boolean = true,
    /** Notifications before a saved paper (PUC, insurance) expires. */
    val documentReminders: Boolean = true,
    /** Rider override of the tank size; null = model spec. */
    val tankLitres: Double? = null,
    val currencySymbol: String = "₹",
    /** Shows the technical tools and details (sources, exact ages, frames). Off by default; unlocked from Settings. */
    val developerMode: Boolean = false,
    /** Permissions the rider waved away on Today ("Not now"), by name. The card returns when another one goes off. */
    val dismissedPermissions: Set<String> = emptySet(),
)

/** Where a fuel-up odometer came from (ux/FUEL.md). */
enum class OdoSource { BIKE, EDITED, MANUAL }

data class FuelUp(
    val id: Long = 0,
    val atMillis: Long,
    val odometerKm: Int,
    val litres: Double,
    val totalPaid: Double,
    val full: Boolean,
    val odoSource: OdoSource,
    /** Fuel bars on the cluster at the time, if connected. */
    val barsBefore: Int? = null,
)

/** A ride, from connect (or first movement) to disconnect. Odometer values come from the bike when known. */
data class Trip(
    val id: Long = 0,
    val title: String,
    val startMillis: Long,
    val endMillis: Long?,
    val distanceKm: Double,
    val topSpeedKmh: Float,
    val avgSpeedKmh: Float,
    val odoStartKm: Int?,
    val odoEndKm: Int?,
    val fuelBarsStart: Int?,
    val fuelBarsEnd: Int?,
    val note: String? = null,
)

/** A labelled waypoint inside a merged trip, shown as a stop marker on the map. */
data class TripStop(val tripId: Long, val index: Int, val lat: Double, val lng: Double, val atMillis: Long, val label: String? = null)

data class TrackPoint(
    val tripId: Long,
    val atMillis: Long,
    val lat: Double,
    val lng: Double,
    val speedKmh: Float,
)

/** Stored by name: append new icons, never rename or reorder the old ones. */
enum class ServiceIcon {
    OIL, CHAIN, AIR, BRAKE, TYRE, GENERIC,
    BIKE, FUEL, CLOCK, BELL, STAR, FLAG,
    BATTERY, COOLANT, SPARK, LIGHT,
}

/** Unit of a service time interval. Months are calendar months (31 Jan + 1 month = 28 or 29 Feb). */
enum class SpanUnit { DAYS, WEEKS, MONTHS }

/** "Every 6 months", "every 2 weeks". [count] is at least 1. */
data class TimeSpan(val count: Int, val unit: SpanUnit)

/**
 * A logbook item, due by distance ([everyKm]), by time ([everyTime]) or by both, whichever comes first. At least
 * one is set. Until the item is first marked done, a pending first service ([firstAtKm] on the odometer,
 * [firstByDate] on the calendar) replaces the recurring interval of its kind.
 */
data class ServiceItem(
    val id: Long = 0,
    val label: String,
    /** Null when the item is not counted by distance. */
    val everyKm: Int?,
    val lastDoneOdoKm: Int,
    val lastDoneMillis: Long?,
    val icon: ServiceIcon,
    /** Null when the item is not counted by time. */
    val everyTime: TimeSpan? = null,
    val firstAtKm: Int? = null,
    val firstByDate: LocalDate? = null,
) {
    /** True until the first service is marked done. */
    val firstPending: Boolean get() = firstAtKm != null || firstByDate != null
}

enum class PlaceKind { HOME, WORK, FUEL, STAR }

/** How a place was saved: shared from Google Maps, phone GPS, typed, parked spot, or end of a trip. */
enum class PlaceSource { MAPS, HERE, TYPED, PARKED, TRIP }

/** A saved destination. [query] is what Safar hands to Google Maps: address text or "lat,lng". */
data class Place(
    val id: Long = 0,
    val label: String,
    val detail: String,
    val query: String,
    val kind: PlaceKind,
    val source: PlaceSource,
    val sortOrder: Int,
    val lat: Double? = null,
    val lng: Double? = null,
)

/** Phone location when the bike disconnected (docs 12.4 E8). */
data class ParkedSpot(
    val lat: Double,
    val lng: Double,
    val accuracyM: Float,
    val atMillis: Long,
    val label: String? = null,
)

/** What a saved bike paper is. Stored by name. */
enum class DocKind { RC, INSURANCE, PUC, LICENCE, SERVICE_BILL, OTHER }

/**
 * A bike paper kept on this phone: a PDF or an image in `filesDir/documents/[fileName]`. The file is never part
 * of Android backup. [expiryEpochDay] is a local date (days since 1970-01-01); [remindedStage] is the last
 * expiry reminder sent for that date (see `reminderStage`), 0 = none yet.
 */
data class BikeDocument(
    val id: Long = 0,
    val title: String,
    val kind: DocKind,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val addedMillis: Long,
    val expiryEpochDay: Long? = null,
    val note: String? = null,
    val remindedStage: Int = 0,
)
