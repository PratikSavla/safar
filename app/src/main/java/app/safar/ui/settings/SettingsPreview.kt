package app.safar.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.safar.core.BikeCatalog
import app.safar.data.Settings
import app.safar.data.ThemeMode
import app.safar.ui.theme.SafarTheme

// Synthetic preview data only.
private val model = BikeCatalog.forBleName("SBM01A1B2C3D")

@Preview(heightDp = 3400)
@Composable
private fun SettingsPreview() = SafarTheme(ThemeMode.LIGHT) {
    val s = Settings(riderName = "Alex", speedAlertOn = true, speedAlertKmh = 90, tankLitres = 11.5)
    SettingsContent(
        SettingsState(s, model, nameText = "Alex", tankText = "11.5", speedKmh = 90, alertRange = model.alertRange),
        AccessStatus(notificationAccess = true, mapsInstalled = true),
        SettingsActions(),
    )
}

@Preview(heightDp = 3400, fontScale = 1.4f)
@Composable
private fun SettingsLockedPreview() = SafarTheme(ThemeMode.DARK) {
    SettingsContent(
        SettingsState(Settings(riderName = "Rider"), null, nameText = "Rider", tankText = "", lockReason = "Locked while moving (42 km/h). Stop to edit."),
        AccessStatus(),
        SettingsActions(),
    )
}
