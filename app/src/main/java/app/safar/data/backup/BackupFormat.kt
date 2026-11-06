package app.safar.data.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// The "Export everything" file: a zip holding manifest.json, data.json and documents/<file>. These classes are
// the file's format, kept apart from the Room rows so a database change never breaks an old export. Every
// field has a default and unknown keys are ignored, so older and newer files both read.

const val BACKUP_FORMAT = "safar-backup"

/** Bump when a change cannot be read by older builds; they then refuse the file with a clear message. */
const val BACKUP_FORMAT_VERSION = 1

const val MANIFEST_ENTRY = "manifest.json"
const val DATA_ENTRY = "data.json"
const val DOCUMENTS_PREFIX = "documents/"

internal val BackupJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    prettyPrint = true
    prettyPrintIndent = "  "
}

@Serializable
data class BackupManifest(
    val format: String = "",
    val formatVersion: Int = 0,
    val appVersion: String = "",
    val exportedAtMillis: Long = 0,
    val counts: BackupCounts = BackupCounts(),
)

@Serializable
data class BackupCounts(
    val trips: Int = 0,
    val trackPoints: Int = 0,
    val fuelUps: Int = 0,
    val serviceItems: Int = 0,
    val places: Int = 0,
    val parkedSpot: Boolean = false,
    val documents: Int = 0,
)

@Serializable
data class BackupData(
    val trips: List<TripDto> = emptyList(),
    val fuelUps: List<FuelUpDto> = emptyList(),
    val serviceItems: List<ServiceItemDto> = emptyList(),
    val places: List<PlaceDto> = emptyList(),
    val parkedSpot: ParkedSpotDto? = null,
    val documents: List<DocumentDto> = emptyList(),
    val settings: SettingsDto? = null,
    val savedBike: SavedBikeDto? = null,
)

@Serializable
data class TripDto(
    val id: Long = 0,
    val title: String = "",
    val startMillis: Long = 0,
    val endMillis: Long? = null,
    val distanceKm: Double = 0.0,
    val topSpeedKmh: Float = 0f,
    val avgSpeedKmh: Float = 0f,
    val odoStartKm: Int? = null,
    val odoEndKm: Int? = null,
    val fuelBarsStart: Int? = null,
    val fuelBarsEnd: Int? = null,
    val track: List<TrackPointDto> = emptyList(),
)

@Serializable
data class TrackPointDto(val atMillis: Long = 0, val lat: Double = 0.0, val lng: Double = 0.0, val speedKmh: Float = 0f)

@Serializable
data class FuelUpDto(
    val id: Long = 0,
    val atMillis: Long = 0,
    val odometerKm: Int = 0,
    val litres: Double = 0.0,
    val totalPaid: Double = 0.0,
    val full: Boolean = false,
    val odoSource: String = "MANUAL",
    val barsBefore: Int? = null,
)

/** A logbook item. [everyKm] 0 = not counted by distance; [everyTimeCount] null = not counted by time. */
@Serializable
data class ServiceItemDto(
    val id: Long = 0,
    val label: String = "",
    val everyKm: Int = 0,
    val lastDoneOdoKm: Int = 0,
    val lastDoneMillis: Long? = null,
    val icon: String = "GENERIC",
    val everyTimeCount: Int? = null,
    /** DAYS, WEEKS or MONTHS. */
    val everyTimeUnit: String? = null,
    val firstAtKm: Int? = null,
    /** Local date as days since 1970-01-01. */
    val firstByEpochDay: Long? = null,
    /** The same date as text (yyyy-MM-dd), for people reading the file. Ignored on import. */
    val firstByDate: String? = null,
)

@Serializable
data class PlaceDto(
    val id: Long = 0,
    val label: String = "",
    val detail: String = "",
    val query: String = "",
    val kind: String = "STAR",
    val source: String = "TYPED",
    val sortOrder: Int = 0,
    val lat: Double? = null,
    val lng: Double? = null,
)

@Serializable
data class ParkedSpotDto(val lat: Double = 0.0, val lng: Double = 0.0, val accuracyM: Float = 0f, val atMillis: Long = 0, val label: String? = null)

/** A paper. Its file is `documents/<fileName>` in the same zip. */
@Serializable
data class DocumentDto(
    val id: Long = 0,
    val title: String = "",
    val kind: String = "OTHER",
    val fileName: String = "",
    val mimeType: String = "",
    val sizeBytes: Long = 0,
    val addedMillis: Long = 0,
    /** Local date as days since 1970-01-01. */
    val expiryEpochDay: Long? = null,
    /** The same date as text (yyyy-MM-dd), for people reading the file. Ignored on import. */
    val expiryDate: String? = null,
    val note: String? = null,
)

/** Rider settings. Missing values take the app's defaults on import. */
@Serializable
data class SettingsDto(
    val riderName: String? = null,
    val speedAlertOn: Boolean? = null,
    val speedAlertKmh: Int? = null,
    val alertCalls: Boolean? = null,
    val alertWhatsAppCalls: Boolean? = null,
    val alertWhatsAppMessages: Boolean? = null,
    val debugLog: Boolean? = null,
    val hideNames: Boolean? = null,
    val theme: String? = null,
    val keepScreenOn: Boolean? = null,
    val autoRideMode: Boolean? = null,
    val serviceReminders: Boolean? = null,
    val lowFuelAlert: Boolean? = null,
    val documentReminders: Boolean? = null,
    val tankLitres: Double? = null,
    val currencySymbol: String? = null,
    val developerMode: Boolean? = null,
)

/** The paired bike at export time. Kept for the record; import leaves the phone's own pairing alone. */
@Serializable
data class SavedBikeDto(val name: String = "", val address: String = "")
