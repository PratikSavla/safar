package app.safar.ui.trips.preview

import app.safar.data.TrackPoint
import app.safar.ui.trips.LiveRideUi
import app.safar.ui.trips.TripDetailUi
import app.safar.ui.trips.TripFuelUi
import app.safar.ui.trips.TripRowUi
import app.safar.ui.trips.TripTotals
import app.safar.ui.trips.TripsUiState
import kotlin.math.cos
import kotlin.math.sin

/** Synthetic journal data for @Preview only. The track is a made-up loop, not a real road. */
object TripsPreviewData {
    private const val T0 = 1_793_000_000_000L

    val track: List<TrackPoint> = List(80) { i ->
        val t = i / 79.0 * 5.2
        TrackPoint(1, T0 + i * 20_000L, 18.50 + 0.03 * sin(t) + 0.004 * t, 73.80 + 0.045 * cos(t * 0.8) + 0.01 * t, 40f)
    }

    val list = TripsUiState(
        loading = false,
        bikeName = "V-Strom SX",
        week = TripTotals(186.4, 255, 4, 104f),
        month = TripTotals(712.9, 1010, 13, 112f),
        rows = listOf(
            TripRowUi(3, 3, "MON 02 NOV", "Evening ride", 42.3, 64, 88f),
            TripRowUi(2, 2, "SAT 31 OCT", "Morning ride on NH 48", 128.6, 142, 104f),
            TripRowUi(1, 1, "THU 29 OCT", "Main Rd errands", 8.1, 26, 52f),
        ),
        live = LiveRideUi(42f, 61f, T0),
    )

    val detail = TripDetailUi(
        id = 2, number = 2, day = "SAT 31 OCT", title = "Morning ride on NH 48", km = 128.6,
        distanceSource = "bike odometer", minutes = 142, avgKmh = 54f, topKmh = 104f,
        odoStart = 12_036, odoEnd = 12_164,
        fuel = TripFuelUi(5, 3, 6, "2 bars used", "≈ 64 km per bar"),
        trackLat = track.map { it.lat }, trackLng = track.map { it.lng }, fixes = track.size, recording = false,
    )
}
