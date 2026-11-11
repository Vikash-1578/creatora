package com.creatora.app.ui.home

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.creatora.app.R
import com.creatora.app.domain.model.Project
import com.creatora.app.domain.model.VideoFormat
import com.creatora.app.domain.model.WizardStep
import com.creatora.app.ui.components.HeaderIconButton
import com.creatora.app.ui.components.PrimaryButton
import com.creatora.app.ui.components.ProjectThumbnail
import com.creatora.app.ui.components.ScreenHeader
import com.creatora.app.ui.components.SecondaryButton
import com.creatora.app.ui.components.SectionTitle
import com.creatora.app.ui.components.TabScreen
import com.creatora.app.ui.create.titleRes
import com.creatora.app.ui.theme.CreatoraTheme

/** Home tab: where you left off, quick ways to start, recent projects, the Coach and your channel. */
@Composable
fun HomeScreen(
    onProfileClick: () -> Unit,
    onContinue: (projectId: Long) -> Unit,
    onOpenCoach: () -> Unit,
    onNewVideo: () -> Unit,
    onOpenLibrary: () -> Unit,
    onConnectChannel: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val project by viewModel.continueProject.collectAsStateWithLifecycle()
    val recent by viewModel.recentProjects.collectAsStateWithLifecycle()
    HomeContent(project, recent, onProfileClick, onContinue, onOpenCoach, onNewVideo, onOpenLibrary, onConnectChannel, modifier)
}

@Composable
fun HomeContent(
    continueProject: Project?,
    recent: List<Project>,
    onProfileClick: () -> Unit,
    onContinue: (Long) -> Unit,
    onOpenCoach: () -> Unit,
    onNewVideo: () -> Unit,
    onOpenLibrary: () -> Unit,
    onConnectChannel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = CreatoraTheme.spacing
    TabScreen(modifier) {
        ScreenHeader(
            title = stringResource(R.string.tab_home),
            subtitle = stringResource(R.string.home_subtitle),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                HeaderIconButton(Icons.Outlined.AutoAwesome, stringResource(R.string.cd_open_coach), onOpenCoach)
                HeaderIconButton(Icons.Outlined.Person, stringResource(R.string.cd_profile), onProfileClick)
            }
        }
        Column(
            modifier = Modifier.padding(horizontal = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.xxl),
        ) {
            if (continueProject != null) ContinueCard(continueProject, onContinue)
            Section(R.string.home_quick_actions) {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                    QuickAction(Icons.Outlined.Add, R.string.home_action_new_video, onNewVideo, Modifier.weight(1f))
                    QuickAction(Icons.Outlined.AutoAwesome, R.string.home_action_ideas, onOpenCoach, Modifier.weight(1f))
                    QuickAction(Icons.Outlined.VideoLibrary, R.string.home_action_library, onOpenLibrary, Modifier.weight(1f))
                }
            }
            Section(R.string.home_recent) {
                if (recent.isEmpty()) {
                    InfoCard(R.string.home_recent_empty, R.string.home_recent_empty_action, onNewVideo)
                } else {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                        recent.forEach { RecentCard(it, onContinue) }
                    }
                }
            }
            Section(R.string.home_coach_title) {
                InfoCard(R.string.home_coach_body, R.string.home_coach_action, onOpenCoach)
            }
            Section(R.string.home_channel_title) {
                InfoCard(R.string.home_channel_body, R.string.home_channel_action, onConnectChannel)
            }
        }
    }
}

@Composable
private fun Section(titleRes: Int, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
        SectionTitle(stringResource(titleRes))
        content()
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = CreatoraTheme.shapes.radius16
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CreatoraTheme.colors.surface)
            .border(CreatoraTheme.dimens.borderWidth, CreatoraTheme.colors.border, shape)
            .padding(CreatoraTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm),
    ) { content() }
}

/** A short explanation with one action, for things that have no data yet. */
@Composable
private fun InfoCard(bodyRes: Int, actionRes: Int, onAction: () -> Unit) {
    Card {
        Text(stringResource(bodyRes), style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.textMuted)
        SecondaryButton(stringResource(actionRes), onAction)
    }
}

@Composable
private fun QuickAction(icon: ImageVector, labelRes: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = CreatoraTheme.colors
    val shape = CreatoraTheme.shapes.radius16
    Column(
        modifier = modifier
            .heightIn(min = CreatoraTheme.dimens.minTouchTarget)
            .clip(shape)
            .background(colors.surface)
            .border(CreatoraTheme.dimens.borderWidth, colors.border, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = CreatoraTheme.spacing.md, horizontal = CreatoraTheme.spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.xs),
    ) {
        Icon(icon, contentDescription = null, tint = colors.accentText)
        Text(stringResource(labelRes), style = CreatoraTheme.typography.caption, color = colors.text, maxLines = 2)
    }
}

@Composable
private fun RecentCard(project: Project, onOpen: (Long) -> Unit) {
    val colors = CreatoraTheme.colors
    Column(
        modifier = Modifier
            .width(CreatoraTheme.dimens.recentCardWidth)
            .clickable(role = Role.Button) { onOpen(project.id) },
        verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.xs),
    ) {
        Column(
            Modifier
                .aspectRatio(RECENT_RATIO)
                .clip(CreatoraTheme.shapes.radius16)
                .border(CreatoraTheme.dimens.borderWidth, colors.border, CreatoraTheme.shapes.radius16),
        ) { ProjectThumbnail(project.format) }
        Text(project.title, style = CreatoraTheme.typography.bodyBold, color = colors.text, maxLines = 1)
    }
}

private const val RECENT_RATIO = 16f / 10f

@Composable
private fun ContinueCard(project: Project, onContinue: (Long) -> Unit) {
    val colors = CreatoraTheme.colors
    val step = WizardStep.entries[project.step.coerceIn(0, WizardStep.entries.lastIndex)]
    Card {
        Text(stringResource(R.string.home_continue_title), style = CreatoraTheme.typography.caption, color = colors.textMuted)
        Text(project.title, style = CreatoraTheme.typography.cardTitle, color = colors.text, maxLines = 1)
        Text(
            stringResource(R.string.home_continue_step, step.ordinal + 1, WizardStep.entries.size, stringResource(step.titleRes())),
            style = CreatoraTheme.typography.body,
            color = colors.textMuted,
        )
        PrimaryButton(stringResource(R.string.home_continue_action), { onContinue(project.id) })
    }
}

private val PreviewProject = Project(id = 1, title = "Preview project", format = VideoFormat.LONG_VIDEO, step = 2)

@Preview(name = "Light", showBackground = true)
@Composable
private fun HomeLightPreview() {
    CreatoraTheme(darkTheme = false) { HomeContent(PreviewProject, listOf(PreviewProject), {}, {}, {}, {}, {}, {}) }
}

@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeDarkPreview() {
    CreatoraTheme(darkTheme = true) { HomeContent(null, emptyList(), {}, {}, {}, {}, {}, {}) }
}
