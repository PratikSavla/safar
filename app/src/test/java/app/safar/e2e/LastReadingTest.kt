package app.safar.e2e

import app.safar.ble.READING_SAVE_EVERY_MS
import app.safar.core.ClusterReading
import app.safar.core.LinkState
import app.safar.data.SavedBikeRecord
import app.safar.protocol.Timing
import app.safar.sim.BIKE_ADDRESS
import app.safar.sim.BikeRig
import app.safar.sim.CLOCK_BASE
import app.safar.sim.ClusterSpec
import app.safar.sim.MOTO_NAME
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The last cluster reading is stored with the saved bike, so it survives an app restart or update. */
@OptIn(ExperimentalCoroutinesApi::class)
class LastReadingTest {
    private val riding = ClusterSpec(
        speedKmh = 48,
        odometerKm = 12_305,
        tripAKm = 128.7,
        tripBKm = 506.2,
        fuelBars = 4,
        byte23 = '3'.code,
        byte25 = 0x1E,
    )

    @Test
    fun readingsAreStoredAtMostEvery30sWhileConnected() = runTest {
        val rig = BikeRig(this)
        rig.pairFromScan()
        rig.advance(1_000)
        assertEquals(1, rig.savedStore.readings.size, "the first reading is stored at once")
        assertEquals(12_261, rig.savedStore.readings.single().odometerKm)

        rig.bike.cluster = riding
        rig.advance(READING_SAVE_EVERY_MS - 2_000)
        assertEquals(1, rig.savedStore.readings.size, "frames within 30 s are not stored")

        rig.advance(3_000)
        assertEquals(2, rig.savedStore.readings.size)
        assertEquals(12_305, rig.savedStore.readings.last().odometerKm)
        val frames = rig.manager.state.value.framesReceived
        assertTrue(frames >= READING_SAVE_EVERY_MS / Timing.CLUSTER_PERIOD_MS, "frames kept arriving: $frames")
    }

    @Test
    fun theNewestReadingIsStoredWhenTheRiderDisconnects() = runTest {
        val rig = BikeRig(this)
        rig.pairFromScan()
        rig.bike.cluster = riding
        rig.advance(5 * Timing.CLUSTER_PERIOD_MS)
        val newest = rig.manager.state.value.cluster

        rig.manager.disconnect()
        rig.advance(100)
        assertEquals(LinkState.Idle, rig.link)
        assertEquals(newest, rig.savedStore.lastReading.value)
        assertEquals(newest, rig.manager.state.value.lastCluster)
        assertNull(rig.manager.state.value.cluster)
    }

    @Test
    fun theNewestReadingIsStoredWhenTheBikeDropsTheLink() = runTest {
        val rig = BikeRig(this)
        rig.pairFromScan()
        rig.bike.cluster = riding
        rig.advance(5 * Timing.CLUSTER_PERIOD_MS)
        val newest = rig.manager.state.value.cluster

        rig.bike.powerOff()
        rig.advance(100)
        assertTrue(rig.link is LinkState.Reconnecting, "link is ${rig.link}")
        assertEquals(newest, rig.savedStore.lastReading.value)
        assertEquals(48, rig.savedStore.lastReading.value?.speedKmh)
        assertEquals(4, rig.savedStore.lastReading.value?.fuelBars)
    }

    @Test
    fun aStoredReadingShowsAsLastSeenAfterARestart() = runTest {
        val stored = ClusterReading(0, 12_305, 128.7, 506.2, 4, 'N', 'N'.code, 0x1D, CLOCK_BASE - 3_600_000)
        val rig = BikeRig(this, savedBike = SavedBikeRecord(MOTO_NAME, BIKE_ADDRESS))
        rig.bike.powerOff()
        rig.savedStore.keep(stored)

        rig.start()
        val state = rig.manager.state.value
        assertEquals(stored, state.lastCluster, "an hour-old reading from the earlier run")
        assertNull(state.cluster, "nothing live while the bike is off")
        assertTrue(rig.recording.events.any { it.name == "Last reading restored" })
    }

    @Test
    fun forgettingTheBikeDropsItsStoredReading() = runTest {
        val rig = BikeRig(this)
        rig.pairFromScan()
        rig.bike.cluster = riding
        rig.advance(5 * Timing.CLUSTER_PERIOD_MS)

        rig.manager.forgetBike()
        rig.advance(100)
        assertNull(rig.savedStore.lastReading.value)
        assertNull(rig.manager.state.value.lastCluster)
        assertEquals(1, rig.savedStore.readings.size, "the unsaved newest reading is dropped, not written")
    }
}
