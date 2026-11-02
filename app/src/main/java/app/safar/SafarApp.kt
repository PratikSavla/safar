package app.safar

import android.app.Application
import app.safar.data.DataModule

class SafarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DataModule.install(this)
    }
}
