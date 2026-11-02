package app.safar

import android.app.Application
import app.safar.data.DataModule
import app.safar.phone.PhoneModule

class SafarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DataModule.install(this)
        // phone: keep PhoneModule after the other modules (it reads their singletons lazily).
        PhoneModule.install(this)
    }
}
