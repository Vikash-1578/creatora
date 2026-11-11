package com.creatora.app.ui.settings

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.creatora.app.BuildConfig
import com.creatora.app.R
import com.creatora.app.ui.components.ListGroup
import com.creatora.app.ui.components.ListRowDivider
import com.creatora.app.ui.components.NavigationListRow
import com.creatora.app.ui.components.SubScreenScaffold
import com.creatora.app.ui.theme.CreatoraTheme

/** Where each Settings row leads. */
class SettingsDestinations(
    val account: () -> Unit,
    val notifications: () -> Unit,
    val ai: () -> Unit,
    val billing: () -> Unit,
    val appearance: () -> Unit,
    val privacy: () -> Unit,
    val help: () -> Unit,
    /** The old user-key screen, only offered in developer builds. */
    val devAiKeys: () -> Unit,
)

/** The top level of Settings, like the phone's own: a list where every row opens its own page. */
@Composable
fun SettingsScreen(onBack: () -> Unit, destinations: SettingsDestinations, modifier: Modifier = Modifier) {
    SubScreenScaffold(title = stringResource(R.string.settings_title), onBack = onBack, modifier = modifier) {
        ListGroup {
            NavigationListRow(stringResource(R.string.settings_account), destinations.account)
            ListRowDivider()
            NavigationListRow(stringResource(R.string.settings_notifications), destinations.notifications)
            ListRowDivider()
            NavigationListRow(stringResource(R.string.settings_ai), destinations.ai)
            ListRowDivider()
            NavigationListRow(stringResource(R.string.settings_billing), destinations.billing)
        }
        ListGroup {
            NavigationListRow(stringResource(R.string.settings_appearance_language), destinations.appearance)
            ListRowDivider()
            NavigationListRow(stringResource(R.string.settings_privacy), destinations.privacy)
            ListRowDivider()
            NavigationListRow(stringResource(R.string.settings_help_about), destinations.help)
        }
        if (BuildConfig.DEV_AI_KEYS) {
            ListGroup { NavigationListRow(stringResource(R.string.ai_providers_title), destinations.devAiKeys) }
        }
    }
}

private val PreviewDestinations = SettingsDestinations({}, {}, {}, {}, {}, {}, {}, {})

@Preview(name = "Light", showBackground = true)
@Composable
private fun SettingsLightPreview() {
    CreatoraTheme(darkTheme = false) { SettingsScreen({}, PreviewDestinations) }
}

@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsDarkPreview() {
    CreatoraTheme(darkTheme = true) { SettingsScreen({}, PreviewDestinations) }
}
