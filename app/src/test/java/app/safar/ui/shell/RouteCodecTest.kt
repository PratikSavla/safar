package app.safar.ui.shell

import app.safar.ui.nav.Route
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RouteCodecTest {
    private val all = listOf(
        Route.Onboarding, Route.Pair, Route.Home, Route.Ride, Route.Navigate, Route.Alerts, Route.Trips, Route.Fuel,
        Route.Service, Route.Parked, Route.Bike, Route.Log, Route.IconTest, Route.Settings, Route.Permissions,
        Route.Place(), Route.Place(placeId = 4), Route.Place(sharedText = ""), Route.Place(sharedText = "Main Rd|NH 48|0"),
        Route.Place(placeId = 2, sharedText = "1Main Rd"), Route.Trip(12), Route.FuelUp(), Route.FuelUp(9),
        Route.Documents, Route.Document(), Route.Document(3),
    )

    @Test
    fun everyRouteRoundTrips() {
        all.forEach { r -> assertEquals(r, RouteCodec.decode(RouteCodec.encode(r)), "route $r") }
    }

    @Test
    fun encodedFormIsReadable() {
        assertEquals("home", RouteCodec.encode(Route.Home))
        assertEquals("trip|12", RouteCodec.encode(Route.Trip(12)))
        assertEquals("place||0", RouteCodec.encode(Route.Place()))
        assertEquals("place|3|1NH 48", RouteCodec.encode(Route.Place(3, "NH 48")))
    }

    @Test
    fun unreadableTextDecodesToNull() {
        assertNull(RouteCodec.decode("garage"))
        assertNull(RouteCodec.decode("trip|abc"))
        assertNull(RouteCodec.decode("trip"))
        assertNull(RouteCodec.decode("place|1"))
    }
}
