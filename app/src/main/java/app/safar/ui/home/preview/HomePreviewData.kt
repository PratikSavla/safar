package app.safar.ui.home.preview

import app.safar.core.AlertCounter
import app.safar.core.AlertSource
import app.safar.core.AlertsState
import app.safar.core.BikeCatalog
import app.safar.core.BikeState
import app.safar.core.ClusterReading
import app.safar.core.LinkState
import app.safar.core.NavState
import app.safar.core.RideState
import app.safar.core.SavedBike
import app.safar.data.FuelUp
import app.safar.data.OdoSource
import app.safar.data.Settings
import app.safar.ui.home.ConnActions
import app.safar.ui.home.HomeLive
import app.safar.ui.home.HomeStored
import app.safar.ui.home.HomeUi
import app.safar.ui.home.homeUi
import app.safar.ui.home.speedUi

/** Synthetic Today states for @Preview (rider Alex, bike SBM01A1B2C3D). */
object HomePreviewData {
    private const val NOW = 1_780_000_000_000L
    const val BIKE_NAME = "SBM01A1B2C3D"
    val saved = SavedBike(BIKE_NAME, "00:11:22:33:44:55", BikeCatalog.forBleName(BIKE_NAME))

    fun reading(at: Long = NOW - 3_000) = ClusterReading(
        speedKmh = 0, odometerKm = 12_345, tripAKm = 142.6, tripBKm = 1_204.3, fuelBars = 4, gear = 'N',
        raw23 = 'N'.code, raw25 = 84, receivedAtMillis = at,
    )

    val fuelUps = listOf(
        FuelUp(1, NOW - 20 * DAY, 11_700, 11.2, 1_150.0, full = true, odoSource = OdoSource.BIKE),
        FuelUp(2, NOW - 9 * DAY, 12_050, 9.4, 970.0, full = true, odoSource = OdoSource.BIKE),
    )

    private val settings = Settings(onboarded = true, riderName = "Alex")

    val noActions = ConnActions({}, {}, {}, {})

    fun connected(): HomeUi = build(
        BikeState(link = LinkState.Connected, saved = saved, cluster = reading(), lastCluster = reading(), framesReceived = 12),
        AlertsState(missed = AlertCounter(2, "Rider", AlertSource.PHONE), messages = AlertCounter(1, "Alex", AlertSource.WHATSAPP)),
    )

    fun reconnecting(): HomeUi = build(
        BikeState(link = LinkState.Reconnecting(2, NOW + 4_000), saved = saved, lastCluster = reading(NOW - 90_000)),
        AlertsState(),
    )

    val speed = speedUi(RideState(speedKmh = 0f, lastFixAtMillis = NOW - 2_000))

    private fun build(bike: BikeState, alerts: AlertsState): HomeUi = homeUi(
        HomeLive(bike, NavState(), alerts, settings),
        HomeStored(fuelUps, emptyList(), null, emptyList(), 3),
        logCount = 40,
        nowMillis = NOW,
    )

    private const val DAY = 86_400_000L
}
