package app.safar.ui.icontest

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.safar.data.ThemeMode
import app.safar.ui.theme.SafarTheme

@Preview(heightDp = 2200)
@Composable
private fun IconTestPreview() = SafarTheme(ThemeMode.LIGHT) {
    IconTestContent(IconTestState(current = 21, connected = true, lastSentCode = 21, lastSentAtMillis = 0L), IconTestActions())
}

@Preview(heightDp = 2600, fontScale = 1.4f)
@Composable
private fun IconTestOfflinePreview() = SafarTheme(ThemeMode.DARK) {
    IconTestContent(IconTestState(), IconTestActions())
}
