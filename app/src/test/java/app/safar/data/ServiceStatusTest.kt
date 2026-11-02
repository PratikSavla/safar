package app.safar.data

import org.junit.Test
import kotlin.test.assertEquals

class ServiceStatusTest {
    private fun item(every: Int, last: Int) =
        ServiceItem(id = 1, label = "Engine oil", everyKm = every, lastDoneOdoKm = last, lastDoneMillis = null, icon = ServiceIcon.OIL)

    @Test
    fun okWhenFarFromDue() {
        val s = serviceStatus(item(3000, 9800), 11000)
        assertEquals(1200, s.kmSinceLast)
        assertEquals(1800, s.kmToNext)
        assertEquals(12800, s.dueAtKm)
        assertEquals(0.4, s.progress, 1e-9)
        assertEquals(ServiceState.OK, s.state)
    }

    @Test
    fun dueSoonWithinTenPercent() {
        assertEquals(ServiceState.OK, serviceStatus(item(3000, 9800), 12499).state)
        assertEquals(ServiceState.DUE_SOON, serviceStatus(item(3000, 9800), 12500).state)
        assertEquals(ServiceState.DUE_SOON, serviceStatus(item(300, 12080), 12350).state)
        assertEquals(ServiceState.DUE_SOON, serviceStatus(item(3000, 9800), 12800).state)
    }

    @Test
    fun overdueOncePastInterval() {
        val s = serviceStatus(item(3000, 9800), 13261)
        assertEquals(-461, s.kmToNext)
        assertEquals(ServiceState.OVERDUE, s.state)
        assertEquals(3461.0 / 3000, s.progress, 1e-9)
    }

    @Test
    fun odometerBelowLastServiceClampsToZero() {
        val s = serviceStatus(item(500, 11890), 11800)
        assertEquals(0, s.kmSinceLast)
        assertEquals(500, s.kmToNext)
        assertEquals(0.0, s.progress)
    }

    @Test
    fun sortsMostUrgentFirst() {
        val items = listOf(item(6000, 6100).copy(id = 3), item(500, 11890).copy(id = 2), item(3000, 9800).copy(id = 1))
        // Air filter overdue by 161 km, chain due in 129 km, oil due in 539 km.
        assertEquals(listOf(3L, 2L, 1L), serviceStatuses(items, 12261).map { it.item.id })
        assertEquals(listOf(-161, 129, 539), serviceStatuses(items, 12261).map { it.kmToNext })
    }

    @Test
    fun defaultsStartAtCurrentOdometer() {
        val items = DefaultServiceItems.create(odometerKm = 12261, atMillis = 1_000)
        assertEquals(listOf(3000, 500, 6000, 5000, 300), items.map { it.everyKm })
        assertEquals(
            listOf(ServiceIcon.OIL, ServiceIcon.CHAIN, ServiceIcon.AIR, ServiceIcon.BRAKE, ServiceIcon.TYRE),
            items.map { it.icon },
        )
        items.forEach {
            assertEquals(12261, it.lastDoneOdoKm)
            assertEquals(1_000L, it.lastDoneMillis)
            assertEquals(0L, it.id)
            assertEquals(ServiceState.OK, serviceStatus(it, 12261).state)
        }
    }
}
