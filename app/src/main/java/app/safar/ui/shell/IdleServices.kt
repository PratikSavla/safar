package app.safar.ui.shell

import android.util.Log
import app.safar.core.AlertCenter
import app.safar.core.AlertsState
import app.safar.core.BikeController
import app.safar.core.BikeState
import app.safar.core.DebugLog
import app.safar.core.LogKind
import app.safar.core.LogRecord
import app.safar.core.NavState
import app.safar.core.NavigationBridge
import app.safar.core.PhoneSnapshot
import app.safar.core.PhoneStatusSource
import app.safar.core.RideState
import app.safar.core.RideTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.IOException

/**
 * Do-nothing implementations that keep the UI usable while a layer is missing (see [ShellDeps]).
 * Every action logs that it went nowhere, so a missing install is visible in logcat.
 */
internal object IdleServices {
    private const val TAG = "SafarShell"

    private fun ignored(what: String) {
        Log.w(TAG, "$what ignored: that layer is not installed")
    }

    val bike: BikeController = object : BikeController {
        override val state: StateFlow<BikeState> = MutableStateFlow(BikeState())
        override fun startScan() = ignored("startScan")
        override fun stopScan() = Unit
        override fun connect(address: String) = ignored("connect")
        override fun disconnect() = Unit
        override fun forgetBike() = ignored("forgetBike")
        override fun sendIconTest(iconCode: Int) = ignored("sendIconTest")
    }

    val navigation: NavigationBridge = object : NavigationBridge {
        override val state: StateFlow<NavState> = MutableStateFlow(NavState())
        override fun openInMaps(label: String, query: String) = ignored("openInMaps")
        override fun end() = Unit
    }

    val alerts: AlertCenter = object : AlertCenter {
        override val state: StateFlow<AlertsState> = MutableStateFlow(AlertsState())
        override fun clearMissedCalls() = Unit
        override fun clearMessages() = Unit
    }

    val phone: PhoneStatusSource = object : PhoneStatusSource {
        override val state: StateFlow<PhoneSnapshot> = MutableStateFlow(PhoneSnapshot())
    }

    val ride: RideTracker = object : RideTracker {
        override val state: StateFlow<RideState> = MutableStateFlow(RideState())
    }

    val log: DebugLog = object : DebugLog {
        override val records: StateFlow<List<LogRecord>> = MutableStateFlow(emptyList())
        override val sessionActive: StateFlow<Boolean> = MutableStateFlow(false)
        override fun frame(kind: LogKind, bytes: ByteArray, ok: Boolean, note: String?) = Unit
        override fun event(name: String, detail: Map<String, String>, error: Boolean) = Unit
        override fun startSession() = ignored("startSession")
        override fun stopSession() = Unit
        override fun clear() = Unit
        override suspend fun export(hideNames: Boolean): File {
            val e = IOException("Debug log layer is not installed")
            Log.e(TAG, "export failed", e)
            throw e
        }
    }
}
