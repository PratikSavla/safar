package app.safar.ui.vm

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.transform

/**
 * Emits the first value at once, then at most one value per [periodMillis]: always the latest, so nothing shown
 * is ever older than one period. For counters that change faster than a screen needs (the debug log size).
 */
fun <T> Flow<T>.throttleLatest(periodMillis: Long): Flow<T> = conflate().transform {
    emit(it)
    delay(periodMillis)
}
