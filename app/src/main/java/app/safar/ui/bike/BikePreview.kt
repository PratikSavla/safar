package app.safar.ui.bike

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.safar.core.BikeCatalog
import app.safar.core.BikeState
import app.safar.core.DeviceInfo
import app.safar.core.LinkState
import app.safar.core.SavedBike
import app.safar.data.ThemeMode
import app.safar.ui.theme.SafarTheme

// Synthetic preview data only.
private val saved = SavedBike("SBM01A1B2C3D", "00:00:00:00:00:01", BikeCatalog.forBleName("SBM01A1B2C3D"))
private val info = DeviceInfo("SUZUKI", "SBM01", "A1B2C3D0042", "2.4.1", "HW-03", "SW-1.18", null)

@Preview(heightDp = 1900)
@Composable
private fun BikeConnectedPreview() = SafarTheme(ThemeMode.LIGHT) {
    BikeContent(BikeScreenState(true, bikeInfo(BikeState(link = LinkState.Connected, saved = saved, mtu = 65, deviceInfo = info), null)), BikeActions())
}

@Preview(heightDp = 1900, fontScale = 1.4f)
@Composable
private fun BikeOfflinePreview() = SafarTheme(ThemeMode.DARK) {
    BikeContent(BikeScreenState(true, bikeInfo(BikeState(saved = saved), null)), BikeActions())
}

@Preview(heightDp = 800)
@Composable
private fun NoBikePreview() = SafarTheme(ThemeMode.LIGHT) {
    BikeContent(BikeScreenState(true, null), BikeActions())
}
