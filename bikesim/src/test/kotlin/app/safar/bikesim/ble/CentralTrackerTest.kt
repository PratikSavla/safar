package app.safar.bikesim.ble

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class CentralTrackerTest {
    private val safar = "02:00:00:00:00:01"
    private val watch = "02:00:00:00:00:02"
    private val tracker = CentralTracker()

    @Test
    fun `first link becomes the central and enabling notify turns it on`() {
        assertEquals(CentralTracker.Change(safar, true, false, false), tracker.connected(safar))
        assertEquals(CentralTracker.Change(safar, false, true, true), tracker.cccd(safar, true))
        assertEquals(listOf(safar), tracker.subscribers())
    }

    @Test
    fun `a second link keeps the central and its notify state`() {
        tracker.connected(safar)
        tracker.cccd(safar, true)
        assertEquals(CentralTracker.Change(safar, false, true, false), tracker.connected(watch))
        assertEquals(CentralTracker.Change(safar, false, true, false), tracker.disconnected(watch))
        assertEquals(listOf(safar), tracker.subscribers())
        assertFalse(tracker.isSubscribed(watch))
    }

    @Test
    fun `central disconnect turns notify off and frees the slot`() {
        tracker.connected(safar)
        tracker.cccd(safar, true)
        tracker.connected(watch)
        assertEquals(CentralTracker.Change(null, true, false, true), tracker.disconnected(safar))
        assertNull(tracker.central)
    }

    @Test
    fun `a device that enables notify takes over from a silent central`() {
        tracker.connected(watch)
        tracker.connected(safar)
        assertEquals(watch, tracker.central)
        assertEquals(CentralTracker.Change(safar, true, true, true), tracker.cccd(safar, true))
        assertEquals(CentralTracker.Change(safar, false, true, false), tracker.disconnected(watch))
    }

    @Test
    fun `when the central leaves another subscriber takes over without a notify change`() {
        tracker.connected(watch)
        tracker.cccd(watch, true)
        tracker.connected(safar)
        tracker.cccd(safar, true)
        assertEquals(listOf(watch, safar), tracker.subscribers())
        assertEquals(CentralTracker.Change(watch, true, true, false), tracker.disconnected(safar))
    }

    @Test
    fun `disabling notify keeps the central linked and reports notify off`() {
        tracker.connected(safar)
        tracker.cccd(safar, true)
        assertEquals(CentralTracker.Change(safar, false, false, true), tracker.cccd(safar, false))
        assertTrue(tracker.subscribers().isEmpty())
    }

    @Test
    fun `clear forgets every link`() {
        tracker.connected(safar)
        tracker.cccd(safar, true)
        tracker.clear()
        assertNull(tracker.central)
        assertTrue(tracker.subscribers().isEmpty())
    }
}
