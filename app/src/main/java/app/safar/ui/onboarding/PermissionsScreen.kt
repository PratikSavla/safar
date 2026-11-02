package app.safar.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.safar.ui.components.AtlasPage
import app.safar.ui.components.BackTopBar
import app.safar.ui.nav.Navigator

/**
 * The onboarding permission list on its own page, for Today's "permissions are off" card and Settings. A phone
 * restored from backup keeps the rider's data but none of these, so this is where the rider turns them back on.
 */
@Composable
fun PermissionsScreen(navigator: Navigator, modifier: Modifier = Modifier) {
    val asked = remember { mutableStateOf(emptySet<String>()) }
    AtlasPage(modifier) {
        BackTopBar("Permissions", "What Safar", navigator::back, titleEm = " may use.")
        PermissionsList(asked)
    }
}
