package app.safar.sim

import app.safar.ble.BikeManager
import app.safar.ble.SessionInputs
import app.safar.core.DebugLog
import app.safar.core.LinkState
import app.safar.core.NavigationBridge
import app.safar.data.SavedBikeRecord
import app.safar.data.Settings
import app.safar.phone.AlertCenterImpl
import app.safar.phone.PhoneEventLog
import app.safar.sim.fakes.FakeNavigationBridge
import app.safar.sim.fakes.FakePhoneStatus
import app.safar.sim.fakes.FakeSavedBikeStore
import app.safar.sim.fakes.FakeServiceRepository
import app.safar.sim.fakes.FakeSettingsStore
import app.safar.sim.fakes.RecordingLog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import java.time.LocalDateTime
import java.time.ZoneId

/** Synthetic cluster names: a V-Strom SX (sum checksum) and an Access 125 scooter (inverted checksum). */
const val MOTO_NAME = "SBM01A1B2C3D"
const val SCOOTER_NAME = "SAS01A1B2C3D"
const val BIKE_ADDRESS = "0A:1B:2C:3D:4E:5F"

/** Wall clock at virtual time 0: 2 Nov 2026, 14:05:00 local time. */
val CLOCK_BASE: Long = LocalDateTime.of(2026, 11, 2, 14, 5, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

/**
 * The app's BLE layer ([BikeManager] and everything below it) talking to a [FakeBikePeripheral] on the test
 * scheduler, with fakes for the stores, phone status and navigation, and the real [AlertCenterImpl]. All
 * times are virtual: [now] is [CLOCK_BASE] plus the scheduler time, the bike records scheduler times.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BikeRig(
    val test: TestScope,
    bikeName: String = MOTO_NAME,
    savedBike: SavedBikeRecord? = null,
    initialSettings: Settings = Settings(riderName = "Alex"),
    makeLog: (() -> Long) -> DebugLog = { RecordingLog(it) },
) {
    val now: () -> Long = { CLOCK_BASE + test.testScheduler.currentTime }
    val log: DebugLog = makeLog(now)
    val recording: RecordingLog get() = log as RecordingLog
    val settings = FakeSettingsStore(initialSettings)
    val savedStore = FakeSavedBikeStore(savedBike)
    val phone = FakePhoneStatus()
    val navigation = FakeNavigationBridge()

    /** Read when a session starts; set it before connecting to use another bridge. */
    var navSource: NavigationBridge = navigation
    val service = FakeServiceRepository()
    val bike = FakeBikePeripheral(bikeName, BIKE_ADDRESS, test.backgroundScope) { test.testScheduler.currentTime }
    val radio = SimRadio(listOf(bike), log)
    val frames = TxFrames(bike.mode)

    val manager: BikeManager = BikeManager(
        radio = radio,
        log = log,
        savedStore = savedStore,
        serviceItems = { service },
        inputs = ::inputs,
        parent = test.backgroundScope,
        now = now,
        serial = StandardTestDispatcher(test.testScheduler),
    )
    val alerts: AlertCenterImpl = AlertCenterImpl({ manager }, { settings.settings.value }, PhoneEventLog(log::event), now)

    val link: LinkState get() = manager.state.value.link
    val time: Long get() = test.testScheduler.currentTime

    private fun inputs(): SessionInputs = SessionInputs(settings.settings, phone.state, alerts.state, navSource.state)

    fun advance(ms: Long) {
        test.advanceTimeBy(ms)
        test.runCurrent()
    }

    /** Starts the manager; with a saved bike it connects at once. */
    fun start() {
        manager.start()
        test.runCurrent()
    }

    /** First pairing: scan, tap the bike, wait for the link. Returns the virtual time the session started. */
    fun pairFromScan(): Long {
        start()
        manager.startScan()
        test.runCurrent()
        manager.connect(bike.address)
        return awaitConnected()
    }

    /** Steps 1 ms at a time until the link is up; returns that exact virtual time (the session start). */
    fun awaitConnected(limitMs: Long = 120_000): Long {
        val until = time + limitMs
        while (link != LinkState.Connected) {
            check(time < until) { "no connection within $limitMs ms, link is $link" }
            advance(1)
        }
        return time
    }

    /** Frames of [type] the bike received, oldest first. */
    fun writes(type: Char): List<WrittenFrame> = bike.writes.filter { it.type == type }

    /** Frames of [type] written at or after [from] (virtual ms). */
    fun writes(type: Char, from: Long): List<WrittenFrame> = writes(type).filter { it.atMillis >= from }
}
