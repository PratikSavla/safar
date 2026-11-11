package app.safar.data.backup

import app.safar.data.SavedBikeRecord
import app.safar.data.Settings
import app.safar.data.ThemeMode
import app.safar.data.db.DatabaseRows
import app.safar.data.db.DocumentEntity
import app.safar.data.db.FuelUpEntity
import app.safar.data.db.ParkedSpotEntity
import app.safar.data.db.PlaceEntity
import app.safar.data.db.ServiceItemEntity
import app.safar.data.db.TrackPointEntity
import app.safar.data.db.TripEntity
import app.safar.data.reminderStage
import java.time.LocalDate

// Room rows and settings <-> the export file's classes. Pure: unit-tested.

/** Everything Safar stores, read in one go for an export. */
data class UserSnapshot(val rows: DatabaseRows, val settings: Settings, val savedBike: SavedBikeRecord?)

fun UserSnapshot.toBackupData(): BackupData {
    val tracks = rows.trackPoints.groupBy { it.tripId }
    return BackupData(
        trips = rows.trips.map { it.toDto(tracks[it.id].orEmpty()) },
        fuelUps = rows.fuelUps.map { it.toDto() },
        serviceItems = rows.serviceItems.map { it.toDto() },
        places = rows.places.map { it.toDto() },
        parkedSpot = rows.parkedSpot?.let { ParkedSpotDto(it.lat, it.lng, it.accuracyM, it.atMillis, it.label) },
        documents = rows.documents.map { it.toDto() },
        settings = settings.toDto(),
        savedBike = savedBike?.let { SavedBikeDto(it.name, it.address) },
    )
}

fun BackupData.counts(): BackupCounts = BackupCounts(
    trips = trips.size,
    trackPoints = trips.sumOf { it.track.size },
    fuelUps = fuelUps.size,
    serviceItems = serviceItems.size,
    places = places.size,
    parkedSpot = parkedSpot != null,
    documents = documents.size,
)

/**
 * Rows to insert. Track points get new ids; every other row keeps its id. Reminders already due on
 * [todayEpochDay] count as sent: the rider sees the warning in the app rather than a burst of notifications.
 */
fun BackupData.toRows(todayEpochDay: Long): DatabaseRows = DatabaseRows(
    trips = trips.map { it.toEntity() },
    trackPoints = trips.flatMap { t -> t.track.map { TrackPointEntity(tripId = t.id, atMillis = it.atMillis, lat = it.lat, lng = it.lng, speedKmh = it.speedKmh) } },
    fuelUps = fuelUps.map { FuelUpEntity(it.id, it.atMillis, it.odometerKm, it.litres, it.totalPaid, it.full, it.odoSource, it.barsBefore) },
    serviceItems = serviceItems.map {
        ServiceItemEntity(
            it.id, it.label, it.everyKm, it.lastDoneOdoKm, it.lastDoneMillis, it.icon,
            it.everyTimeCount, it.everyTimeUnit, it.firstAtKm, it.firstByEpochDay,
        )
    },
    places = places.map { PlaceEntity(it.id, it.label, it.detail, it.query, it.kind, it.source, it.sortOrder, it.lat, it.lng) },
    parkedSpot = parkedSpot?.let { ParkedSpotEntity(lat = it.lat, lng = it.lng, accuracyM = it.accuracyM, atMillis = it.atMillis, label = it.label) },
    documents = documents.map {
        val stage = reminderStage(todayEpochDay, it.expiryEpochDay)
        DocumentEntity(it.id, it.title, it.kind, it.fileName, it.mimeType, it.sizeBytes, it.addedMillis, it.expiryEpochDay, it.note, stage)
    },
)

/** [this] applied over [base]: missing values keep [base]'s. Onboarding and dismissed cards stay [base]'s. */
fun SettingsDto.applyTo(base: Settings): Settings = base.copy(
    riderName = riderName ?: base.riderName,
    speedAlertOn = speedAlertOn ?: base.speedAlertOn,
    speedAlertKmh = speedAlertKmh ?: base.speedAlertKmh,
    alertCalls = alertCalls ?: base.alertCalls,
    alertWhatsAppCalls = alertWhatsAppCalls ?: base.alertWhatsAppCalls,
    alertWhatsAppMessages = alertWhatsAppMessages ?: base.alertWhatsAppMessages,
    debugLog = debugLog ?: base.debugLog,
    hideNames = hideNames ?: base.hideNames,
    theme = ThemeMode.entries.firstOrNull { it.name == theme } ?: base.theme,
    keepScreenOn = keepScreenOn ?: base.keepScreenOn,
    autoRideMode = autoRideMode ?: base.autoRideMode,
    serviceReminders = serviceReminders ?: base.serviceReminders,
    lowFuelAlert = lowFuelAlert ?: base.lowFuelAlert,
    documentReminders = documentReminders ?: base.documentReminders,
    tankLitres = tankLitres,
    currencySymbol = currencySymbol ?: base.currencySymbol,
    developerMode = developerMode ?: base.developerMode,
)

fun Settings.toDto() = SettingsDto(
    riderName = riderName, speedAlertOn = speedAlertOn, speedAlertKmh = speedAlertKmh, alertCalls = alertCalls,
    alertWhatsAppCalls = alertWhatsAppCalls, alertWhatsAppMessages = alertWhatsAppMessages, debugLog = debugLog,
    hideNames = hideNames, theme = theme.name, keepScreenOn = keepScreenOn, autoRideMode = autoRideMode,
    serviceReminders = serviceReminders, lowFuelAlert = lowFuelAlert, documentReminders = documentReminders,
    tankLitres = tankLitres, currencySymbol = currencySymbol, developerMode = developerMode,
)

private fun TripEntity.toDto(track: List<TrackPointEntity>) = TripDto(
    id, title, startMillis, endMillis, distanceKm, topSpeedKmh, avgSpeedKmh, odoStartKm, odoEndKm, fuelBarsStart, fuelBarsEnd,
    note = note, stops = emptyList(),
    track = track.map { TrackPointDto(it.atMillis, it.lat, it.lng, it.speedKmh) },
)

private fun TripDto.toEntity() = TripEntity(
    id, title, startMillis, endMillis, distanceKm, topSpeedKmh, avgSpeedKmh, odoStartKm, odoEndKm, fuelBarsStart, fuelBarsEnd,
    note = note,
)

private fun FuelUpEntity.toDto() = FuelUpDto(id, atMillis, odometerKm, litres, totalPaid, full, odoSource, barsBefore)

private fun ServiceItemEntity.toDto() = ServiceItemDto(
    id, label, everyKm, lastDoneOdoKm, lastDoneMillis, icon, everyTimeCount, everyTimeUnit, firstAtKm, firstByEpochDay,
    firstByDate = firstByEpochDay?.let { LocalDate.ofEpochDay(it).toString() },
)

private fun PlaceEntity.toDto() = PlaceDto(id, label, detail, query, kind, source, sortOrder, lat, lng)

private fun DocumentEntity.toDto() = DocumentDto(
    id, title, kind, fileName, mimeType, sizeBytes, addedMillis, expiryEpochDay,
    expiryDate = expiryEpochDay?.let { LocalDate.ofEpochDay(it).toString() }, note = note,
)
