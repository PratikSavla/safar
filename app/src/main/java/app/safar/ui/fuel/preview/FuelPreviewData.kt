package app.safar.ui.fuel.preview

import app.safar.core.BikeCatalog
import app.safar.core.BikeState
import app.safar.core.ClusterReading
import app.safar.core.LinkState
import app.safar.core.RideState
import app.safar.core.SavedBike
import app.safar.data.FuelUp
import app.safar.data.OdoSource
import app.safar.data.OdoSuggestion
import app.safar.data.OdoSuggestionSource
import app.safar.data.Settings
import app.safar.ui.fuel.FuelForm
import app.safar.ui.fuel.FuelUiState
import app.safar.ui.fuel.FuelUpUiState
import app.safar.ui.fuel.fuelUiState
import app.safar.ui.fuel.fuelUpUiState
import java.time.ZoneOffset

/** Synthetic fuel data for @Preview only (the same receipts as the data-layer test fixtures). */
object FuelPreviewData {
    private const val NOW = 1_793_620_800_000L
    private const val DAY = 86_400_000L
    private val zone = ZoneOffset.ofHoursMinutes(5, 30)

    val model = BikeCatalog.forBleName("SBM01A1B2C3D")

    val fuelUps = listOf(
        FuelUp(1, NOW - 30 * DAY, 10880, 11.2, 1176.0, true, OdoSource.BIKE, 0),
        FuelUp(2, NOW - 22 * DAY, 11262, 11.4, 1197.0, true, OdoSource.BIKE, 1),
        FuelUp(3, NOW - 12 * DAY, 11610, 10.6, 1118.0, true, OdoSource.MANUAL, null),
        FuelUp(4, NOW - 8 * DAY, 11894, 4.0, 424.0, false, OdoSource.BIKE, 3),
        FuelUp(5, NOW - 4 * DAY, 11952, 6.4, 678.0, true, OdoSource.EDITED, 2),
        FuelUp(6, NOW - 2 * DAY, 12164, 6.7, 712.0, true, OdoSource.BIKE, 1),
    )

    val cluster = ClusterReading(0, 12_301, 84.6, 462.3, 2, 'N', 0x4E, 29, NOW - 3_000)

    val bike = BikeState(link = LinkState.Connected, saved = SavedBike("SBM01A1B2C3D", "00:11:22:33:44:55", model), cluster = cluster)

    val page: FuelUiState = fuelUiState(fuelUps, Settings(), bike, NOW, zone)

    val empty: FuelUiState = fuelUiState(emptyList(), Settings(), BikeState(), NOW, zone)

    private val sugg = OdoSuggestion(12_301, OdoSuggestionSource.BIKE, NOW - 3_000)

    val form: FuelUpUiState = fuelUpUiState(
        null, false, sugg, FuelForm("6.7", "712", "12301", full = true, editOdo = false), fuelUps, 12.0, "₹", RideState(),
    )

    val formErrors: FuelUpUiState = fuelUpUiState(
        null, false, OdoSuggestion(null, OdoSuggestionSource.NONE, null), FuelForm("14", "", "11000", editOdo = true), fuelUps, 12.0, "₹", RideState(),
    )

    val formLocked: FuelUpUiState = fuelUpUiState(
        null, false, sugg, FuelForm(), fuelUps, 12.0, "₹", RideState(speedKmh = 42f, moving = true),
    )
}
