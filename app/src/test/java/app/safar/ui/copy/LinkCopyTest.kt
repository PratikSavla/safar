package app.safar.ui.copy

import app.safar.core.BikeCatalog
import app.safar.core.BikeState
import app.safar.core.LinkState
import app.safar.core.SavedBike
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinkCopyTest {
    private val model = BikeCatalog.forBleName("SBM01A1B2C3D")
    private val saved = SavedBike("SBM01A1B2C3D", "00:00:00:00:00:01", model)
    private val connecting = LinkState.Connecting("SBM01A1B2C3D")
    private val reconnecting = LinkState.Reconnecting(attempt = 4, nextAttemptAtMillis = 40_000)

    @Test
    fun `headlines are the status words of the voice guide`() {
        assertEquals("Looking for your bike", LinkCopy.headline(LinkState.Searching, null, false))
        assertEquals("Connecting to your ${model.name}", LinkCopy.headline(connecting, model.name, true))
        assertEquals("Connecting to your bike", LinkCopy.headline(connecting, null, false))
        assertEquals("Connected", LinkCopy.headline(LinkState.Connected, model.name, true))
        assertEquals("Bike is off or out of range", LinkCopy.headline(reconnecting, model.name, true))
        assertEquals("Bluetooth is off", LinkCopy.headline(LinkState.BluetoothOff, model.name, true))
        assertEquals("Not set up yet", LinkCopy.headline(LinkState.Idle, null, false))
        assertEquals("Not connected", LinkCopy.headline(LinkState.Idle, model.name, true))
    }

    @Test
    fun `details say what happens next without radio words`() {
        assertEquals("We’ll reconnect as soon as it’s back.", LinkCopy.detail(reconnecting, model.name, true))
        assertEquals("Turn the ignition on and keep your phone close.", LinkCopy.detail(LinkState.Searching, null, false))
        assertEquals("Turn it on to connect to your ${model.name}.", LinkCopy.detail(LinkState.BluetoothOff, model.name, true))
        assertEquals("Turn it on to set up your bike.", LinkCopy.detail(LinkState.BluetoothOff, null, false))
        assertEquals("Ready to ride. Turns, calls and the time go to the display.", LinkCopy.detail(LinkState.Connected, model.name, true))
        val all = listOf(LinkState.Idle, LinkState.BluetoothOff, LinkState.Searching, connecting, LinkState.Connected, reconnecting)
        val words = Regex("(?i)cluster|gatt|uuid|mtu|frame|scan|attempt|back-off|PIN")
        all.forEach { link ->
            listOf(true, false).forEach { savedBike ->
                val text = LinkCopy.headline(link, model.name, savedBike) + " " + LinkCopy.detail(link, model.name, savedBike)
                assertFalse(words.containsMatchIn(text), "technical word in: $text")
            }
        }
    }

    @Test
    fun `bike state helpers use the saved model`() {
        val bike = BikeState(link = LinkState.Connecting("SBM01A1B2C3D"), saved = saved)
        assertEquals(model.name, LinkCopy.modelName(bike))
        assertEquals("Connecting to your ${model.name}", LinkCopy.headline(bike))
        assertEquals("This takes a few seconds.", LinkCopy.detail(bike))
        assertEquals(model.name, LinkCopy.modelName(BikeState(link = connecting)))
        assertNull(LinkCopy.modelName(BikeState()))
        assertEquals("Not set up yet", LinkCopy.headline(BikeState()))
    }

    @Test
    fun `stamps are one or two plain words`() {
        assertEquals("connected", LinkCopy.stamp(LinkState.Connected, true))
        assertEquals("waiting", LinkCopy.stamp(reconnecting, true))
        assertEquals("looking", LinkCopy.stamp(LinkState.Searching, false))
        assertEquals("new", LinkCopy.stamp(LinkState.Idle, false))
        assertEquals("offline", LinkCopy.stamp(LinkState.Idle, true))
    }

    @Test
    fun `developer detail keeps the technical facts`() {
        assertEquals("back-off · attempt 4 · next try in 12 s", LinkCopy.developerDetail(reconnecting, nowMillis = 28_000))
        assertEquals("MTU 65 · notify on · 42 frames", LinkCopy.developerDetail(LinkState.Connected, 0, mtu = 65, framesReceived = 42))
        assertTrue(LinkCopy.developerDetail(connecting, 0).orEmpty().startsWith("GATT"))
        assertNull(LinkCopy.developerDetail(LinkState.Idle, 0))
    }

    @Test
    fun `shared phrases match the voice guide`() {
        assertEquals("Coming soon", Phrases.COMING_SOON)
        assertEquals("Waiting for the bike", Phrases.WAITING_FOR_BIKE)
        assertEquals("Bike is off or out of range. We’ll reconnect as soon as it’s back.", Phrases.BIKE_AWAY)
        assertEquals("Allow Safar to read Google Maps and WhatsApp alerts", Phrases.ALLOW_ALERTS)
        assertEquals("You’re now a developer", Phrases.DEVELOPER_ON)
        assertEquals("Developer mode is off", Phrases.DEVELOPER_OFF)
    }
}
