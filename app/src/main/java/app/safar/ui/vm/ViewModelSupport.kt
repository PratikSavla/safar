package app.safar.ui.vm

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One-shot messages for a screen's toast. A ViewModel posts, the screen shows it and calls [consume].
 * The serial lets the same text show twice in a row.
 */
class Notices {
    private val _current = MutableStateFlow<Pair<Long, String>?>(null)
    val current: StateFlow<Pair<Long, String>?> = _current.asStateFlow()
    private var serial = 0L

    fun post(message: String) {
        _current.value = ++serial to message
    }

    fun consume() {
        _current.value = null
    }
}

/**
 * Runs a repository write in [viewModelScope]. A failure is logged with [what] as context and reported through
 * [onError] (a toast), never thrown into the scope, which would crash the app. Cancellation passes through.
 */
fun ViewModel.launchLogged(tag: String, what: String, onError: (Exception) -> Unit, block: suspend () -> Unit): Job =
    viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "$what failed", e)
            onError(e)
        }
    }
