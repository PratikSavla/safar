package app.safar.ui.service.preview

import app.safar.core.BikeCatalog
import app.safar.core.BikeState
import app.safar.core.ClusterReading
import app.safar.core.LinkState
import app.safar.core.RideState
import app.safar.core.SavedBike
import app.safar.data.ServiceIcon
import app.safar.data.ServiceItem
import app.safar.ui.service.ServiceUiState
import app.safar.ui.service.serviceUiState

/** Synthetic logbook for @Preview only: one item overdue, one due soon, the rest on track. */
object ServicePreviewData {
    private const val NOW = 1_793_620_800_000L

    val items = listOf(
        ServiceItem(1, "Engine oil", 3000, 9_200, NOW, ServiceIcon.OIL),
        ServiceItem(2, "Chain clean & lube", 500, 11_820, NOW, ServiceIcon.CHAIN),
        ServiceItem(3, "Air filter", 6000, 9_200, NOW, ServiceIcon.AIR),
        ServiceItem(4, "Brake pads check", 5000, 10_000, NOW, ServiceIcon.BRAKE),
        ServiceItem(5, "Tyre pressure", 300, 12_150, NOW, ServiceIcon.TYRE),
    )

    private val bike = BikeState(
        link = LinkState.Connected,
        saved = SavedBike("SBM01A1B2C3D", "00:11:22:33:44:55", BikeCatalog.forBleName("SBM01A1B2C3D")),
        cluster = ClusterReading(0, 12_301, 84.6, 462.3, 4, 'N', 0x4E, 29, NOW - 3_000),
    )

    val state: ServiceUiState = serviceUiState(items, bike, RideState(), reminders = true)
}
