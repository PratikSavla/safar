package app.safar.core

import app.safar.data.DocumentFiles
import app.safar.data.DocumentRepository
import app.safar.data.FuelRepository
import app.safar.data.ParkedRepository
import app.safar.data.PlaceRepository
import app.safar.data.SavedBikeStore
import app.safar.data.ServiceRepository
import app.safar.data.SettingsStore
import app.safar.data.TripRepository
import app.safar.data.backup.UserData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers

/**
 * Hand-wired singletons. [SafarApp.onCreate] assigns every property once, before any activity or service
 * starts. Each layer assigns its own implementations in its `install(...)` function.
 */
object AppContainer {
    /** Process-wide scope for long-running work (BLE session, log, GPS). */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var settings: SettingsStore
    lateinit var savedBike: SavedBikeStore
    lateinit var fuel: FuelRepository
    lateinit var trips: TripRepository
    lateinit var service: ServiceRepository
    lateinit var places: PlaceRepository
    lateinit var parked: ParkedRepository
    lateinit var documents: DocumentRepository
    lateinit var documentFiles: DocumentFiles
    /** Export, import and "Delete everything". */
    lateinit var userData: UserData

    lateinit var log: DebugLog
    lateinit var bike: BikeController
    lateinit var outbox: ClusterOutbox
    lateinit var navigation: NavigationBridge
    lateinit var alerts: AlertCenter
    lateinit var phone: PhoneStatusSource
    lateinit var ride: RideTracker
}
