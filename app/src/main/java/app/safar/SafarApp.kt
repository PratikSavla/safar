package app.safar

import android.app.Application
import app.safar.ble.BleModule
import app.safar.data.DataModule
import app.safar.phone.PhoneModule
import app.safar.ui.widget.WidgetUpdater

class SafarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DataModule.install(this)
        // BLE / service / log part: debug log, bike link and outbox. Needs DataModule; reads phone layers lazily.
        BleModule.install(this)
        // phone: keep PhoneModule after the other modules (it reads their singletons lazily).
        PhoneModule.install(this)
        // widget
        WidgetUpdater.start(this)
    }
}
