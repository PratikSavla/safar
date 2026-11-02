package app.safar.ui.vm

import app.safar.core.BikeState
import app.safar.core.LinkState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform

/**
 * The bike state with only what screens other than the link card show (link, saved bike, cluster readings):
 * frame counts, scan results, MTU and device info changing do not re-emit.
 */
fun Flow<BikeState>.readingsOnly(): Flow<BikeState> =
    map { BikeState(link = it.link, saved = it.saved, cluster = it.cluster, lastCluster = it.lastCluster) }.distinctUntilChanged()

/** True while the cluster is linked; re-emits only when that flips. */
fun Flow<BikeState>.linked(): Flow<Boolean> = map { it.link == LinkState.Connected }.distinctUntilChanged()

/**
 * Emits the first value at once, then at most one value per [periodMillis]: always the latest, so nothing shown
 * is ever older than one period. For counters that change faster than a screen needs (the debug log size).
 */
fun <T> Flow<T>.throttleLatest(periodMillis: Long): Flow<T> = conflate().transform {
    emit(it)
    delay(periodMillis)
}
