package app.safar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.safar.data.ThemeMode
import app.safar.ui.catalog.CatalogScreen
import app.safar.ui.theme.SafarTheme

/** Temporary entry point: shows the Atlas component catalog until the real screens land. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var themeMode by rememberSaveable { mutableStateOf(ThemeMode.SYSTEM) }
            SafarTheme(themeMode) {
                CatalogScreen(themeMode, onThemeModeChange = { themeMode = it })
            }
        }
    }
}
