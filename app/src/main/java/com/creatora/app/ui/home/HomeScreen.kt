package com.creatora.app.ui.home

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.creatora.app.R
import com.creatora.app.domain.model.Project
import com.creatora.app.domain.model.VideoFormat
import com.creatora.app.domain.model.WizardStep
import com.creatora.app.ui.components.HeaderIconButton
import com.creatora.app.ui.components.PrimaryButton
import com.creatora.app.ui.components.ScreenHeader
import com.creatora.app.ui.components.TabScreen
import com.creatora.app.ui.create.titleRes
import com.creatora.app.ui.theme.CreatoraTheme

/** Home tab. Shows a Continue card for the latest unfinished Create project; the rest comes in M12. */
@Composable
fun HomeScreen(
    onProfileClick: () -> Unit,
    onContinue: (projectId: Long) -> Unit,
    modifier: Modifier = Modifier,
    onNotificationsClick: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val project by viewModel.continueProject.collectAsStateWithLifecycle()
    HomeContent(project, onProfileClick, onNotificationsClick, onContinue, modifier)
}

@Composable
fun HomeContent(
    continueProject: Project?,
    onProfileClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onContinue: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    TabScreen(modifier = modifier) {
        ScreenHeader(
            title = stringResource(R.string.tab_home),
            subtitle = stringResource(R.string.home_subtitle),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
                HeaderIconButton(
                    icon = Icons.Outlined.Notifications,
                    contentDescription = stringResource(R.string.cd_notifications),
                    onClick = onNotificationsClick,
                )
                HeaderIconButton(
                    icon = Icons.Outlined.Person,
                    contentDescription = stringResource(R.string.cd_profile),
                    onClick = onProfileClick,
                )
            }
        }
        if (continueProject != null) ContinueCard(continueProject, onContinue)
    }
}

@Composable
private fun ContinueCard(project: Project, onContinue: (Long) -> Unit) {
    val colors = CreatoraTheme.colors
    val shape = CreatoraTheme.shapes.radius16
    val step = WizardStep.entries[project.step.coerceIn(0, WizardStep.entries.lastIndex)]
    Column(
        modifier = Modifier
            .padding(horizontal = CreatoraTheme.spacing.lg)
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(CreatoraTheme.dimens.borderWidth, colors.border, shape)
            .padding(CreatoraTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm),
    ) {
        Text(stringResource(R.string.home_continue_title), style = CreatoraTheme.typography.caption, color = colors.textMuted)
        Text(project.title, style = CreatoraTheme.typography.cardTitle, color = colors.text, maxLines = 1)
        Text(
            stringResource(R.string.home_continue_step, step.ordinal + 1, WizardStep.entries.size, stringResource(step.stepTitle())),
            style = CreatoraTheme.typography.body,
            color = colors.textMuted,
        )
        PrimaryButton(stringResource(R.string.home_continue_action), { onContinue(project.id) })
    }
}

private fun WizardStep.stepTitle() = titleRes()

private val PreviewProject = Project(id = 1, title = "Preview project", format = VideoFormat.LONG_VIDEO, step = 2)

@Preview(name = "Light", showBackground = true)
@Composable
private fun HomeLightPreview() {
    CreatoraTheme(darkTheme = false) { HomeContent(PreviewProject, {}, {}, {}) }
}

@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeDarkPreview() {
    CreatoraTheme(darkTheme = true) { HomeContent(null, {}, {}, {}) }
}
