package app.safar.ui.bike

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.safar.core.BikeController
import app.safar.core.BikeStyle
import app.safar.data.BikePhotos
import app.safar.data.DocumentRejected
import app.safar.data.SavedBikeStore
import app.safar.data.SettingsStore
import app.safar.ui.documents.PickedSource
import app.safar.ui.vm.Notices
import app.safar.ui.vm.launchLogged
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

private const val TAG = "SafarBike"

/** [loaded] is false until the first value arrives, so the page does not flash "No bike". */
data class BikeScreenState(
    val loaded: Boolean = false,
    val info: BikeInfo? = null,
    val picture: BikePictureUi = BikePictureUi(),
    /** A photo is being copied in; the picture buttons wait. */
    val busy: Boolean = false,
)

class BikeViewModel(
    private val bike: BikeController,
    savedBike: SavedBikeStore,
    private val settings: SettingsStore,
    private val photos: BikePhotos,
    private val io: CoroutineDispatcher,
) : ViewModel() {
    private val busy = MutableStateFlow(false)
    val notices = Notices()

    val state: StateFlow<BikeScreenState> = combine(bike.state, savedBike.saved, settings.settings, busy) { b, saved, s, w ->
        val info = bikeInfo(b, saved)
        BikeScreenState(true, info, bikePicture(info?.model, s), w)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BikeScreenState())

    /** Disconnects and deletes the saved bike. Returns false when the BLE layer refused. */
    fun forget(): Boolean = try {
        bike.forgetBike()
        true
    } catch (e: RuntimeException) {
        Log.e(TAG, "Could not forget the bike", e)
        false
    }

    /** Shows the drawing for [style]; null goes back to the paired model's own. */
    fun chooseStyle(style: BikeStyle?) {
        launchLogged(TAG, "choice of bike drawing", { notices.post("Couldn’t save that. Try again.") }) {
            settings.update { it.copy(bikeStyle = style) }
        }
    }

    /** Copies the picked photo in, shows it, and deletes the one it replaces. */
    fun usePhoto(source: PickedSource) {
        if (!busy.compareAndSet(expect = false, update = true)) return
        launchLogged(TAG, "bike photo", { busy.value = false; notices.post("Couldn’t add the photo. Try again.") }) {
            try {
                val name = withContext(io) {
                    val input = source.open() ?: throw DocumentRejected("Couldn’t open that photo.")
                    input.use { photos.save(it, source.mimeType) }
                }
                settings.update { it.copy(bikePhoto = name) }
                withContext(io) { photos.keepOnly(name) }
                notices.post("Photo added")
            } catch (e: DocumentRejected) {
                Log.w(TAG, "Bike photo refused: ${e.reason}")
                notices.post(e.reason)
            } finally {
                busy.value = false
            }
        }
    }

    /** Back to the drawing; the photo file is deleted. */
    fun removePhoto() {
        launchLogged(TAG, "removal of the bike photo", { notices.post("Couldn’t remove the photo. Try again.") }) {
            settings.update { it.copy(bikePhoto = null) }
            withContext(io) { photos.keepOnly(null) }
            notices.post("Photo removed")
        }
    }
}
