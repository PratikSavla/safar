package app.safar.phone

import app.safar.core.AlertSource
import app.safar.core.ClusterOutbox
import app.safar.data.Settings

/** Records each outbox call as "type:name:count-or-status:source". */
class RecordingOutbox : ClusterOutbox {
    val frames = mutableListOf<String>()

    override fun sendCall(name: String, source: AlertSource, answered: Boolean) {
        frames += "2:$name:${if (answered) "answered" else "ringing"}:${source.code}"
    }

    override fun sendMissedCalls(name: String, count: Int, source: AlertSource) {
        frames += "4:$name:$count:${source.code}"
    }

    override fun sendMessages(name: String, count: Int, source: AlertSource) {
        frames += "5:$name:$count:${source.code}"
    }
}

/** Records log events by name. */
class RecordingLog : PhoneEventLog {
    val events = mutableListOf<String>()
    override fun event(name: String, detail: Map<String, String>, error: Boolean) {
        events += name
    }
}

class FakeClock(var now: Long = 1_000_000L) : () -> Long {
    override fun invoke(): Long = now
}

/** An [AlertCenterImpl] wired to fakes. */
class AlertRig(settings: Settings = Settings()) {
    var settings: Settings = settings
    val outbox = RecordingOutbox()
    val log = RecordingLog()
    val clock = FakeClock()
    val center = AlertCenterImpl({ outbox }, { this.settings }, log, clock)
}
