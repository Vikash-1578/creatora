package com.creatora.app.ui.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.creatora.app.R
import com.creatora.app.ui.components.InfoListRow
import com.creatora.app.ui.components.ListGroup
import com.creatora.app.ui.components.NavigationListRow
import com.creatora.app.ui.components.SectionTitle
import com.creatora.app.ui.components.SubScreenScaffold
import com.creatora.app.ui.help.HelpTopics
import com.creatora.app.ui.theme.CreatoraTheme

private val PrivacyTopics = listOf(
    R.string.privacy_stored_title to R.string.privacy_stored_body,
    R.string.privacy_ai_title to R.string.privacy_ai_body,
    R.string.privacy_youtube_title to R.string.privacy_youtube_body,
    R.string.privacy_control_title to R.string.privacy_control_body,
)

/** Plain words about what is stored and sent. [onOpenAccount] opens the page where data can be deleted. */
@Composable
fun PrivacyScreen(onBack: () -> Unit, onOpenAccount: () -> Unit, modifier: Modifier = Modifier) {
    SubScreenScaffold(title = stringResource(R.string.settings_privacy), onBack = onBack, modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.lg)) {
            PrivacyTopics.forEach { (title, body) ->
                Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.xs)) {
                    Text(stringResource(title), style = CreatoraTheme.typography.cardTitle, color = CreatoraTheme.colors.text)
                    Text(stringResource(body), style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.textMuted)
                }
            }
        }
        ListGroup { NavigationListRow(stringResource(R.string.privacy_delete_row), onOpenAccount) }
    }
}

@Composable
fun HelpAboutScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    SubScreenScaffold(title = stringResource(R.string.settings_help_about), onBack = onBack, modifier = modifier) {
        HelpTopics()
        Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
            SectionTitle(stringResource(R.string.settings_about))
            ListGroup { InfoListRow(stringResource(R.string.settings_version), version.ifEmpty { stringResource(R.string.profile_version_unknown) }) }
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun PrivacyLightPreview() {
    CreatoraTheme(darkTheme = false) { PrivacyScreen({}, {}) }
}

@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HelpAboutDarkPreview() {
    CreatoraTheme(darkTheme = true) { HelpAboutScreen({}) }
}
