package app.safar

import android.app.Application
import app.safar.data.DataModule
import app.safar.ui.widget.WidgetUpdater

class SafarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DataModule.install(this)
        // widget
        WidgetUpdater.start(this)
    }
}
