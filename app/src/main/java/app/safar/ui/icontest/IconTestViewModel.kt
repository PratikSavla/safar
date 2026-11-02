package app.safar.ui.icontest

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.safar.core.BikeController
import app.safar.core.LinkState
import app.safar.protocol.MAX_ICON_CODE
import app.safar.protocol.MIN_ICON_CODE
import app.safar.protocol.maneuverByCode
import app.safar.ui.art.TurnCodes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

private const val TAG = "SafarIconTest"

/** One cell of the grid: cluster icon code 1..45 and its name from the protocol table. */
data class IconCell(val code: Int, val name: String)

val ICON_CELLS: List<IconCell> = (MIN_ICON_CODE..MAX_ICON_CODE).map { IconCell(it, iconName(it)) }

fun iconName(code: Int): String = maneuverByCode(code)?.name ?: TurnCodes.spec(code).name

/** Next or previous code, wrapping 45 -> 1 and 1 -> 45. */
fun stepCode(code: Int, by: Int): Int {
    val span = MAX_ICON_CODE - MIN_ICON_CODE + 1
    return Math.floorMod(code - MIN_ICON_CODE + by, span) + MIN_ICON_CODE
}

data class IconTestState(
    val current: Int = MIN_ICON_CODE,
    val connected: Boolean = false,
    /** Code and wall-clock time of the last frame handed to the BLE layer. */
    val lastSentCode: Int? = null,
    val lastSentAtMillis: Long? = null,
)

private data class Sent(val current: Int = MIN_ICON_CODE, val code: Int? = null, val atMillis: Long? = null)

class IconTestViewModel(private val bike: BikeController, private val clock: () -> Long = System::currentTimeMillis) : ViewModel() {
    private val sent = MutableStateFlow(Sent())

    val state: StateFlow<IconTestState> = combine(bike.state, sent) { b, s ->
        IconTestState(s.current, b.link == LinkState.Connected, s.code, s.atMillis)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IconTestState())

    /** Sends one navigation frame with [code]. The BLE layer drops it while not connected. */
    fun send(code: Int) {
        val c = code.coerceIn(MIN_ICON_CODE, MAX_ICON_CODE)
        try {
            bike.sendIconTest(c)
            sent.update { Sent(c, c, clock()) }
        } catch (e: RuntimeException) {
            Log.e(TAG, "Could not send icon test $c", e)
            sent.update { it.copy(current = c) }
        }
    }

    fun next() = send(stepCode(sent.value.current, 1))

    fun previous() = send(stepCode(sent.value.current, -1))
}
