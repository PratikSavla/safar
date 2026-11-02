package app.safar

import android.app.Application
import app.safar.ble.BleModule
import app.safar.data.DataModule

class SafarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DataModule.install(this)
        // BLE / service / log part: debug log, bike link and outbox. Needs DataModule; reads phone layers lazily.
        BleModule.install(this)
    }
}
