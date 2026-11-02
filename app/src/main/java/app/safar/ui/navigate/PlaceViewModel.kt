package app.safar.ui.navigate

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.safar.core.RideTracker
import app.safar.data.PlaceKind
import app.safar.data.PlaceRepository
import app.safar.ui.format.InputLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "SafarPlace"

data class PlaceState(
    val editing: Boolean = false,
    val loading: Boolean = false,
    val draft: PlaceDraft? = null,
    val confirmDelete: Boolean = false,
    /** Set while the bike moves: typing is locked (UX rule 3). */
    val lockReason: String? = null,
)

/** One-off outcomes: a toast, or a toast and close the page. */
sealed interface PlaceEvent {
    val message: String

    data class Toast(override val message: String) : PlaceEvent
    data class Done(override val message: String) : PlaceEvent
}

/** Save a place (new, or shared from Maps) or edit one ([placeId]). */
class PlaceViewModel(
    private val placeId: Long?,
    sharedText: String?,
    private val places: PlaceRepository,
    ride: RideTracker,
) : ViewModel() {
    private val form = MutableStateFlow(PlaceState(editing = placeId != null, loading = placeId != null))
    private val events = Channel<PlaceEvent>(Channel.BUFFERED)
    val messages: Flow<PlaceEvent> = events.receiveAsFlow()

    val state: StateFlow<PlaceState> = combine(form, ride.state) { f, r -> f.copy(lockReason = InputLock.reason(r, "Stop to type.")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), form.value)

    init {
        if (placeId != null) {
            load(placeId)
        } else if (sharedText != null) {
            val draft = draftFromShare(sharedText)
            if (draft == null) events.trySend(PlaceEvent.Toast("Nothing usable in the shared text"))
            form.update { it.copy(draft = draft) }
        }
    }

    private fun load(id: Long) = viewModelScope.launch {
        val place = try {
            places.places.first().firstOrNull { it.id == id }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Could not read place $id", e)
            null
        }
        if (place == null) {
            events.trySend(PlaceEvent.Done("That place is gone"))
        } else {
            form.update { it.copy(loading = false, draft = draftFromPlace(place)) }
        }
    }

    fun chooseTyped() {
        if (state.value.lockReason != null) return
        form.update { it.copy(draft = typedDraft()) }
    }

    fun useLocation(lat: Double, lng: Double, accuracyM: Float) = form.update { it.copy(draft = draftFromLocation(lat, lng, accuracyM)) }

    fun locationUnavailable(reason: String) {
        events.trySend(PlaceEvent.Toast(reason))
    }

    /** Back to the three ways in (new places only). */
    fun changeSource() {
        if (placeId == null) form.update { it.copy(draft = null) }
    }

    fun setLabel(text: String) = editDraft { it.copy(label = text.take(PLACE_LABEL_MAX)) }

    fun setQuery(text: String) = editDraft { it.copy(query = text, detail = text) }

    fun setKind(kind: PlaceKind) = form.update { s -> s.copy(draft = s.draft?.copy(kind = kind)) }

    private fun editDraft(change: (PlaceDraft) -> PlaceDraft) {
        if (state.value.lockReason != null) return
        form.update { s -> s.copy(draft = s.draft?.let(change)) }
    }

    fun save() {
        val draft = form.value.draft ?: return
        validateDraft(draft)?.let { events.trySend(PlaceEvent.Toast(it)); return }
        viewModelScope.launch {
            try {
                if (placeId != null) {
                    val order = places.places.first().firstOrNull { it.id == placeId }?.sortOrder ?: 0
                    places.update(draft.toPlace(placeId, order))
                    events.send(PlaceEvent.Done("Place updated"))
                } else {
                    places.add(draft.toPlace(0, 0))
                    events.send(PlaceEvent.Done("${draft.label.trim()} saved · in Route"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not save place ${placeId ?: "new"}", e)
                events.send(PlaceEvent.Toast("Could not save the place"))
            }
        }
    }

    /** First tap asks, the second deletes. */
    fun delete() {
        val id = placeId ?: return
        if (!form.value.confirmDelete) {
            form.update { it.copy(confirmDelete = true) }
            return
        }
        viewModelScope.launch {
            try {
                places.delete(id)
                events.send(PlaceEvent.Done("Place deleted"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not delete place $id", e)
                events.send(PlaceEvent.Toast("Could not delete the place"))
            }
        }
    }
}
