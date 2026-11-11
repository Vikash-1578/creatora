package com.creatora.app.ui.settings

import android.Manifest
import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.creatora.app.R
import com.creatora.app.domain.account.NotificationCategory
import com.creatora.app.domain.account.NotificationPrefs
import com.creatora.app.ui.components.SecondaryButton
import com.creatora.app.ui.components.SubScreenScaffold
import com.creatora.app.ui.notifications.NotificationToggles
import com.creatora.app.ui.theme.CreatoraTheme

@Composable
fun NotificationSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: NotificationSettingsViewModel = hiltViewModel()) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    NotificationSettingsContent(
        prefs, viewModel::toggle, viewModel::setMilestone,
        onAllow = { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) askPermission.launch(Manifest.permission.POST_NOTIFICATIONS) },
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun NotificationSettingsContent(
    prefs: NotificationPrefs,
    onToggle: (NotificationCategory, Boolean) -> Unit,
    onMilestone: (Long) -> Unit,
    onAllow: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SubScreenScaffold(title = stringResource(R.string.settings_notifications), onBack = onBack, modifier = modifier) {
        SecondaryButton(stringResource(R.string.notify_allow), onAllow)
        NotificationToggles(prefs, onToggle, onMilestone)
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun NotificationSettingsLightPreview() {
    CreatoraTheme(darkTheme = false) { NotificationSettingsContent(NotificationPrefs(), { _, _ -> }, {}, {}, {}) }
}

@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NotificationSettingsDarkPreview() {
    CreatoraTheme(darkTheme = true) { NotificationSettingsContent(NotificationPrefs(), { _, _ -> }, {}, {}, {}) }
}
