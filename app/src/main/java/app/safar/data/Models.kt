package app.safar.data

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
)

data class TrackPoint(
    val tripId: Long,
    val atMillis: Long,
    val lat: Double,
    val lng: Double,
    val speedKmh: Float,
)

enum class ServiceIcon { OIL, CHAIN, AIR, BRAKE, TYRE, GENERIC }

data class ServiceItem(
    val id: Long = 0,
    val label: String,
    val everyKm: Int,
    val lastDoneOdoKm: Int,
    val lastDoneMillis: Long?,
    val icon: ServiceIcon,
)

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
