package app.safar.ui.ride.preview

import app.safar.core.ActiveCall
import app.safar.core.AlertSource
import app.safar.core.AlertsState
import app.safar.core.BikeState
import app.safar.core.LinkState
import app.safar.core.NavSnapshot
import app.safar.core.NavState
import app.safar.core.NavStatusCode
import app.safar.core.RideState
import app.safar.data.Settings
import app.safar.ui.home.preview.HomePreviewData
import app.safar.ui.ride.RideUi
import app.safar.ui.ride.rideUi

/** Synthetic ride states for @Preview: navigating along Main Rd, and a call from Alex. */
object RidePreviewData {
    private const val NOW = 1_780_000_000_000L
    private val bike = BikeState(link = LinkState.Connected, saved = HomePreviewData.saved, cluster = HomePreviewData.reading())
    private val riding = RideState(speedKmh = 42.4f, moving = true, lastFixAtMillis = NOW)
    private val settings = Settings(onboarded = true, riderName = "Alex")

    fun navigating(): RideUi = rideUi(
        bike, riding,
        NavState(
            active = true, destinationLabel = "Main Rd",
            snapshot = NavSnapshot(2, true, 850, NOW + 600_000, 11_200, NavStatusCode.NORMAL, "Turn right onto Main Rd", NOW),
        ),
        AlertsState(), settings,
    )

    fun calling(): RideUi = rideUi(
        bike.copy(link = LinkState.Reconnecting(1, NOW + 2_000)), riding, NavState(),
        AlertsState(call = ActiveCall("Alex", AlertSource.PHONE, answered = false, sinceMillis = NOW)), settings,
    )
}
