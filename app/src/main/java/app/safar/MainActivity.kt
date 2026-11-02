package app.safar

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.safar.core.AppContainer
import app.safar.ui.shell.SafarShell
import app.safar.ui.shell.ShellDeps
import app.safar.ui.shell.ShellViewModel

/**
 * The single activity: hosts the app shell. Text shared from Google Maps (ACTION_SEND, text/plain) opens
 * "save a place". The Atlas component catalog (`ui.catalog.CatalogScreen`) stays in the code for design checks.
 */
class MainActivity : ComponentActivity() {
    private val shell: ShellViewModel by viewModels {
        viewModelFactory {
            initializer {
                ShellViewModel(
                    saved = createSavedStateHandle(),
                    settings = AppContainer.settings,
                    bike = ShellDeps.bike,
                    alerts = ShellDeps.alerts,
                    navigation = ShellDeps.navigation,
                    ride = ShellDeps.ride,
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A recreated activity already handled its intent; the shared place is on the restored stack.
        if (savedInstanceState == null) handleShare(intent)
        setContent { SafarShell(shell) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        val text = sharedText(intent) ?: return
        Log.i(TAG, "Received a shared place (${text.length} chars)")
        shell.openShared(text)
    }

    private fun sharedText(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return null
        return intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
    }

    private companion object {
        const val TAG = "SafarMain"
    }
}
