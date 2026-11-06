package app.safar.data.db

import app.safar.data.BikeDocument
import app.safar.data.DocKind
import app.safar.data.FuelUp
import app.safar.data.OdoSource
import app.safar.data.ParkedSpot
import app.safar.data.Place
import app.safar.data.PlaceKind
import app.safar.data.PlaceSource
import app.safar.data.ServiceIcon
import app.safar.data.ServiceItem
import app.safar.data.SpanUnit
import app.safar.data.TimeSpan
import app.safar.data.TrackPoint
import app.safar.data.Trip
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MappersTest {
    @Test
    fun roundTripsAPaperAndReadsAnUnknownKindAsOther() {
        val doc = BikeDocument(7, "PUC", DocKind.PUC, "a.pdf", "application/pdf", 1_024, 5_000, 20_400, "Centre 12", 2)
        assertEquals(doc, doc.toEntity().toModel())
        assertEquals(DocKind.OTHER, doc.toEntity().copy(kind = "PERMIT").toModel().kind)
    }

    @Test
    fun roundTripsEveryModel() {
        val fuel = FuelUp(3, 1_000, 12164, 6.7, 712.0, true, OdoSource.EDITED, 1)
        assertEquals(fuel, fuel.toEntity().toModel())
        val trip = Trip(4, "Morning ride", 1_000, 2_000, 42.5, 98f, 41f, 12164, 12207, 5, 4)
        assertEquals(trip, trip.toEntity().toModel())
        val point = TrackPoint(4, 1_500, 10.5, 70.25, 40f)
        assertEquals(point, point.toEntity().toModel())
        val item = ServiceItem(5, "Engine oil", 3000, 9800, null, ServiceIcon.OIL)
        assertEquals(item, item.toEntity().toModel())
        val scheduled = item.copy(everyKm = null, everyTime = TimeSpan(2, SpanUnit.WEEKS), firstByDate = LocalDate.of(2027, 1, 15), icon = ServiceIcon.SPARK)
        assertEquals(scheduled, scheduled.toEntity().toModel())
        assertEquals(0, scheduled.toEntity().everyKm, "no distance interval is stored as 0")
        val first = item.copy(firstAtKm = 10_800, icon = ServiceIcon.LIGHT)
        assertEquals(first, first.toEntity().toModel())
        val place = Place(6, "Home", "Main Rd", "Main Rd, NH 48", PlaceKind.HOME, PlaceSource.TYPED, 2, 10.5, 70.25)
        assertEquals(place, place.toEntity().toModel())
        val spot = ParkedSpot(10.5, 70.25, 8f, 3_000, "Main Rd")
        assertEquals(spot, spot.toEntity().toModel())
    }

    @Test
    fun storesEnumsByName() {
        assertEquals("BIKE", FuelUp(0, 1, 2, 3.0, 4.0, false, OdoSource.BIKE).toEntity().odoSource)
        assertEquals("CHAIN", ServiceItem(0, "Chain", 500, 0, null, ServiceIcon.CHAIN).toEntity().icon)
    }

    @Test
    fun unknownEnumNamesFallBack() {
        val row = PlaceEntity(1, "X", "", "X", kind = "GARAGE", source = "NFC", sortOrder = 0, lat = null, lng = null)
        assertEquals(PlaceKind.STAR, row.toModel().kind)
        assertEquals(PlaceSource.TYPED, row.toModel().source)
        assertEquals(ServiceIcon.GENERIC, ServiceItemEntity(1, "X", 1, 0, null, "LASER").toModel().icon)
        val oddUnit = ServiceItemEntity(1, "X", 0, 0, null, "OIL", everyTimeCount = 3, everyTimeUnit = "FORTNIGHTS").toModel()
        assertEquals(TimeSpan(3, SpanUnit.MONTHS), oddUnit.everyTime)
        assertEquals(OdoSource.MANUAL, FuelUpEntity(1, 1, 1, 1.0, 1.0, true, "GPS", null).toModel().odoSource)
    }

    private fun place(id: Long, order: Int) = PlaceEntity(id, "P$id", "", "P$id", "STAR", "TYPED", order, null, null)

    @Test
    fun moveSwapsNeighbours() {
        val list = listOf(place(1, 0), place(2, 1), place(3, 2))
        val down = reorderPlaces(list, 1, +1)
        assertEquals(mapOf(2L to 0, 1L to 1), down.associate { it.id to it.sortOrder })
        val up = reorderPlaces(list, 3, -1)
        assertEquals(mapOf(3L to 1, 2L to 2), up.associate { it.id to it.sortOrder })
    }

    @Test
    fun moveRenumbersEqualOrders() {
        val list = listOf(place(1, 0), place(2, 0), place(3, 5))
        val changed = reorderPlaces(list, 2, -1).associate { it.id to it.sortOrder }
        assertEquals(mapOf(1L to 1, 3L to 2), changed)
    }

    @Test
    fun moveAtEdgesOrUnknownIdDoesNothing() {
        val list = listOf(place(1, 0), place(2, 1))
        assertTrue(reorderPlaces(list, 1, -1).isEmpty())
        assertTrue(reorderPlaces(list, 2, +1).isEmpty())
        assertTrue(reorderPlaces(list, 9, +1).isEmpty())
    }
}
