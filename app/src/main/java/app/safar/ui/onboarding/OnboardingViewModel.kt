package app.safar.ui.onboarding

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.safar.core.AppContainer
import app.safar.core.RideTracker
import app.safar.data.SettingsStore
import app.safar.protocol.sanitizeName
import app.safar.ui.copy.Phrases
import app.safar.ui.shell.ShellDeps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.Locale

/** Pages: what Safar does, permissions, rider name. */
const val ONBOARDING_PAGES = 3

/** How the cluster will show [raw]: capitals, printable ASCII, 19 characters at most (protocol `sanitizeName`). */
fun clusterName(raw: String): String = sanitizeName(raw.uppercase(Locale.ROOT))

/** Text input is off above 10 km/h (docs 12.5 rule 3); this is the reason shown under the field. */
const val MOVING_LOCK_REASON = "Typing is off while you’re riding."

data class OnboardingUi(
    val step: Int,
    val name: String,
    val locked: Boolean,
) {
    val cleaned: String get() = clusterName(name)
    val nextLabel: String get() = listOf("Begin", "Continue", Phrases.LOOK_FOR_BIKE)[step.coerceIn(0, ONBOARDING_PAGES - 1)]
}

/**
 * Onboarding pages and the rider name draft. [writeScope] outlives this screen, so finishing (which leaves
 * the screen at once) still saves the name and `onboarded`.
 */
class OnboardingViewModel(
    private val settings: SettingsStore,
    ride: RideTracker,
    private val writeScope: CoroutineScope,
) : ViewModel() {
    private val step = MutableStateFlow(0)
    private val draft = MutableStateFlow<String?>(null)

    val ui: StateFlow<OnboardingUi> = combine(step, draft, settings.settings, ride.state) { s, d, set, r ->
        OnboardingUi(s, d ?: set.riderName, r.moving)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, OnboardingUi(0, "", false))

    fun setName(value: String) {
        if (!ui.value.locked) draft.value = value
    }

    /** Back inside onboarding: false on the first page (system back then leaves the app). */
    fun prev(): Boolean {
        if (step.value == 0) return false
        step.value -= 1
        return true
    }

    /** Next page, or on the last one: save and call [onFinished] (the screen goes to PAIR). */
    fun next(onFinished: () -> Unit) {
        if (step.value < ONBOARDING_PAGES - 1) {
            step.value += 1
            return
        }
        val name = ui.value.name.trim()
        writeScope.launch {
            try {
                settings.update { s -> s.copy(riderName = if (clusterName(name).isNotEmpty()) name else s.riderName, onboarded = true) }
            } catch (e: IOException) {
                // Not fatal: onboarding shows again on the next launch.
                Log.e(TAG, "Saving the rider name and onboarding state failed", e)
            }
        }
        onFinished()
    }

    companion object {
        private const val TAG = "SafarOnboarding"

        fun fromContainer() = OnboardingViewModel(AppContainer.settings, ShellDeps.ride, AppContainer.appScope)
    }
}
