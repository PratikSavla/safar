package app.safar.ui.vm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.safar.ui.components.AtlasToastState

/** Shows each [Notices] message as a toast, once. */
@Composable
fun NoticeToasts(notices: Notices, toast: AtlasToastState) {
    val notice by notices.current.collectAsStateWithLifecycle()
    LaunchedEffect(notice) {
        val n = notice ?: return@LaunchedEffect
        toast.show(n.second)
        notices.consume()
    }
}
