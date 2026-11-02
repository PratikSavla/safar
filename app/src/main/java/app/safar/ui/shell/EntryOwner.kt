package app.safar.ui.shell

import android.app.Application
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SAVED_STATE_REGISTRY_OWNER_KEY
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.enableSavedStateHandles
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * The ViewModel owner of one stack entry, like a navigation back stack entry: its own [ViewModelStore]
 * (kept by [ShellViewModel] until the entry leaves the stack) and its own saved state registry, so a screen
 * ViewModel may use `createSavedStateHandle()` and keep a draft across process death.
 */
internal class EntryOwner(
    override val viewModelStore: ViewModelStore,
    private val application: Application?,
) : ViewModelStoreOwner, SavedStateRegistryOwner, HasDefaultViewModelProviderFactory {
    private val registry = LifecycleRegistry(this)
    private val controller = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry

    override val defaultViewModelProviderFactory: ViewModelProvider.Factory by lazy { SavedStateViewModelFactory(application, this) }

    override val defaultViewModelCreationExtras: CreationExtras
        get() = MutableCreationExtras().apply {
            application?.let { set(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY, it) }
            set(SAVED_STATE_REGISTRY_OWNER_KEY, this@EntryOwner)
            set(VIEW_MODEL_STORE_OWNER_KEY, this@EntryOwner)
        }

    /** Called once, while the lifecycle is still INITIALIZED. [state] is what [save] returned before. */
    fun restore(state: Bundle?) {
        controller.performAttach()
        controller.performRestore(state)
        enableSavedStateHandles()
    }

    fun save(): Bundle = Bundle().also(controller::performSave)

    /**
     * Follows the page: at least CREATED once restored (saved state handles attach on ON_CREATE), never
     * DESTROYED, because the entry outlives an activity recreation. Its store is cleared when it leaves the stack.
     */
    fun follow(parent: Lifecycle.State) {
        registry.currentState = if (parent.isAtLeast(Lifecycle.State.CREATED)) parent else Lifecycle.State.CREATED
    }
}

/** The [EntryOwner] of a page, its registry saved in the page's saveable state (the shell's state holder). */
@Composable
internal fun rememberEntryOwner(entryId: Long, store: () -> ViewModelStore): EntryOwner {
    val application = LocalContext.current.applicationContext as? Application
    val saver = Saver<EntryOwner, Bundle>(
        save = { it.save() },
        restore = { state -> EntryOwner(store(), application).also { it.restore(state) } },
    )
    val owner = rememberSaveable(entryId, saver = saver) { EntryOwner(store(), application).also { it.restore(null) } }
    val parent = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(owner, parent) {
        val observer = LifecycleEventObserver { _, _ -> owner.follow(parent.currentState) }
        owner.follow(parent.currentState)
        parent.addObserver(observer)
        onDispose {
            parent.removeObserver(observer)
            owner.follow(Lifecycle.State.CREATED)
        }
    }
    return owner
}
