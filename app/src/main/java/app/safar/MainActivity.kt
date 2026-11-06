package app.safar

import android.content.Context
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
import app.safar.data.acceptShare
import app.safar.ui.nav.Route
import app.safar.ui.shell.SafarShell
import app.safar.ui.shell.ShellDeps
import app.safar.ui.shell.ShellViewModel

/**
 * The single activity: hosts the app shell. Text shared from Google Maps (ACTION_SEND, text/plain) opens
 * "save a place"; a paper's expiry reminder opens the papers. The Atlas component catalog
 * (`ui.catalog.CatalogScreen`) stays in the code for design checks.
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
        if (savedInstanceState == null) handleIntent(intent)
        setContent { SafarShell(shell) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getStringExtra(EXTRA_OPEN) == OPEN_PAPERS && intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0) {
            shell.open(Route.Documents)
            return
        }
        val text = sharedText(intent) ?: return
        Log.i(TAG, "Received a shared place (${text.length} chars)")
        shell.openShared(text)
    }

    private fun sharedText(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return null
        // Reopened from Recents: Android hands back the share that started the task. It was handled then.
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        // Any app can share to Safar; a huge text would sit in the saved back stack and overflow the Binder.
        return acceptShare(text).also { if (it == null && text != null) Log.w(TAG, "Ignored a share of ${text.length} chars") }
    }

    companion object {
        private const val TAG = "SafarMain"
        private const val EXTRA_OPEN = "app.safar.extra.OPEN"
        private const val OPEN_PAPERS = "papers"

        /** Opens Safar on the papers page (from an expiry reminder). */
        fun openPapersIntent(context: Context): Intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN, OPEN_PAPERS)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
