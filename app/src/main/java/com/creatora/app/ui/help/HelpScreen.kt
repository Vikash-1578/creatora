package com.creatora.app.ui.help

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.creatora.app.R
import com.creatora.app.ui.components.SubScreenScaffold
import com.creatora.app.ui.theme.CreatoraTheme

private val Topics = listOf(
    R.string.help_keys_title to R.string.help_keys_body,
    R.string.help_create_title to R.string.help_create_body,
    R.string.help_files_title to R.string.help_files_body,
    R.string.help_privacy_title to R.string.help_privacy_body,
)

@Composable
fun HelpScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    SubScreenScaffold(title = stringResource(R.string.help_title), onBack = onBack, modifier = modifier) { HelpTopics() }
}

/** The help cards, also shown inside Settings > Help and about. */
@Composable
fun HelpTopics() {
    run {
        Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md)) {
            Topics.forEach { (title, body) ->
                val shape = CreatoraTheme.shapes.radius16
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(CreatoraTheme.colors.surface)
                        .border(CreatoraTheme.dimens.borderWidth, CreatoraTheme.colors.border, shape)
                        .padding(CreatoraTheme.spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.xs),
                ) {
                    Text(stringResource(title), style = CreatoraTheme.typography.cardTitle, color = CreatoraTheme.colors.text)
                    Text(stringResource(body), style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.textMuted)
                }
            }
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun HelpLightPreview() {
    CreatoraTheme(darkTheme = false) { HelpScreen(onBack = {}) }
}

@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HelpDarkPreview() {
    CreatoraTheme(darkTheme = true) { HelpScreen(onBack = {}) }
}
