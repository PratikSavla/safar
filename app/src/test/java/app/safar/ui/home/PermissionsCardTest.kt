package app.safar.ui.home

import app.safar.ui.onboarding.PermId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionsCardTest {
    @Test
    fun nothingOffShowsNoCard() {
        assertNull(permissionsCardUi(emptySet(), emptySet()))
    }

    @Test
    fun oneOffNamesItAndWhatItIsFor() {
        val ui = permissionsCardUi(setOf(PermId.LOCATION), emptySet())!!
        assertEquals("One permission is off", ui.title)
        assertEquals(listOf("Location · trip routes, your speed and where you parked"), ui.lines)
        assertTrue(ui.canDismiss)
    }

    @Test
    fun severalOffAreListedInOnboardingOrder() {
        val ui = permissionsCardUi(setOf(PermId.PHONE, PermId.NOTIFICATIONS, PermId.LOCATION), emptySet())!!
        assertEquals("3 permissions are off", ui.title)
        assertEquals(listOf("Notifications", "Location", "Phone and contacts"), ui.lines.map { it.substringBefore(" · ") })
    }

    @Test
    fun nearbyDevicesOffCannotBeWavedAway() {
        val ui = permissionsCardUi(setOf(PermId.NEARBY, PermId.PHONE), setOf("NEARBY", "PHONE"))!!
        assertEquals("Safar can’t reach your bike", ui.title)
        assertFalse(ui.canDismiss)
    }

    @Test
    fun notNowHidesTheCardUntilSomethingElseGoesOff() {
        assertNull(permissionsCardUi(setOf(PermId.PHONE), setOf("PHONE")))
        val again = permissionsCardUi(setOf(PermId.PHONE, PermId.LOCATION), setOf("PHONE"))!!
        assertEquals("2 permissions are off", again.title)
    }

    @Test
    fun noJargonInTheLines() {
        val all = permissionsCardUi(PermId.entries.toSet() - PermId.NEARBY, emptySet())!!.lines.joinToString(" ")
        listOf("BLE", "Bluetooth", "listener", "cluster", "GPS").forEach { assertFalse(it, all.contains(it)) }
    }
}
