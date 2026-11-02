package app.safar.ui.parked.preview

import app.safar.data.GpsFix
import app.safar.data.ParkedSpot
import app.safar.ui.parked.ParkedUiState
import app.safar.ui.parked.parkedUiState

/** Synthetic parked spot for @Preview only. */
object ParkedPreviewData {
    private const val NOW = 1_793_620_800_000L

    val spot = ParkedSpot(18.5000, 73.8000, 8f, NOW - 2 * 3_600_000L, "Main Rd, near the market")

    val near: ParkedUiState = parkedUiState(spot, GpsFix(18.4990, 73.7990, NOW, 5f), heading = 30f, locationAllowed = true)

    val noLocation: ParkedUiState = parkedUiState(spot, null, heading = null, locationAllowed = false)
}
