package com.creatora.app.ui.home

import android.content.res.Configuration
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.creatora.app.R
import com.creatora.app.domain.grow.Delta
import com.creatora.app.domain.grow.MyChannel
import com.creatora.app.domain.model.Idea
import com.creatora.app.domain.model.Platform
import com.creatora.app.domain.model.Project
import com.creatora.app.domain.model.ScheduledPost
import com.creatora.app.domain.model.VideoFormat
import com.creatora.app.domain.model.WizardStep
import com.creatora.app.ui.components.ChoiceChip
import com.creatora.app.ui.components.HeaderIconButton
import com.creatora.app.ui.components.PrimaryButton
import com.creatora.app.ui.components.ScreenHeader
import com.creatora.app.ui.components.SecondaryButton
import com.creatora.app.ui.components.SectionTitle
import com.creatora.app.ui.components.TabScreen
import com.creatora.app.ui.create.titleRes
import com.creatora.app.ui.grow.StatCard
import com.creatora.app.ui.publish.labelRes
import com.creatora.app.ui.theme.CreatoraTheme

/** Home tab: a composer to start a project or ask the Coach, then cards built from the user's own data. */
@Composable
fun HomeScreen(
    onProfileClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onContinue: (projectId: Long) -> Unit,
    onOpenCoach: (question: String?) -> Unit,
    onOpenPublish: (projectId: Long) -> Unit,
    onOpenGrow: () -> Unit,
    onOpenIdeas: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(
        state = state,
        onProfileClick = onProfileClick,
        onNotificationsClick = onNotificationsClick,
        onStart = { text, format -> viewModel.startProject(text, format, onContinue) },
        onAskCoach = onOpenCoach,
        onContinue = onContinue,
        onUseIdea = { idea -> viewModel.useIdea(idea, onContinue) },
        onOpenPublish = onOpenPublish,
        onOpenGrow = onOpenGrow,
        onOpenIdeas = onOpenIdeas,
        modifier = modifier,
    )
}

@Composable
fun HomeContent(
    state: HomeUiState,
    onProfileClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onStart: (text: String, format: VideoFormat) -> Unit,
    onAskCoach: (question: String?) -> Unit,
    onContinue: (Long) -> Unit,
    onUseIdea: (Idea) -> Unit,
    onOpenPublish: (Long) -> Unit,
    onOpenGrow: () -> Unit,
    onOpenIdeas: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = CreatoraTheme.spacing
    TabScreen(modifier) {
        ScreenHeader(
            title = stringResource(R.string.tab_home),
            subtitle = stringResource(R.string.home_subtitle),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                HeaderIconButton(Icons.Outlined.Notifications, stringResource(R.string.cd_notifications), onNotificationsClick)
                HeaderIconButton(Icons.Outlined.Person, stringResource(R.string.cd_profile), onProfileClick)
            }
        }
        Column(
            modifier = Modifier.padding(start = spacing.lg, end = spacing.lg, bottom = spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            Composer(onStart, onAskCoach)
            state.continueProject?.let { ContinueCard(it, onContinue) }
            state.nextPost?.let { NextPostCard(it, onOpenPublish) }
            IdeasSection(state.ideas, onUseIdea, onOpenIdeas)
            ChannelSection(state, onOpenGrow)
        }
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val shape = CreatoraTheme.shapes.radius16
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CreatoraTheme.colors.surface)
            .border(CreatoraTheme.dimens.borderWidth, CreatoraTheme.colors.border, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(CreatoraTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm),
    ) { content() }
}

@Composable
private fun Composer(onStart: (String, VideoFormat) -> Unit, onAskCoach: (String?) -> Unit) {
    val spacing = CreatoraTheme.spacing
    var text by rememberSaveable { mutableStateOf("") }
    var format by rememberSaveable { mutableStateOf(VideoFormat.LONG_VIDEO) }
    val start = { if (text.isNotBlank()) { onStart(text, format); text = "" } }
    Card {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.home_composer_hint), style = CreatoraTheme.typography.body) },
                textStyle = CreatoraTheme.typography.body,
                shape = CreatoraTheme.shapes.radius12,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { start() }),
            )
            IconButton(
                onClick = start,
                enabled = text.isNotBlank(),
                modifier = Modifier
                    .size(CreatoraTheme.dimens.minTouchTarget)
                    .clip(CircleShape)
                    .background(if (text.isNotBlank()) CreatoraTheme.colors.accent else CreatoraTheme.colors.surfaceAlt),
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.Send,
                    contentDescription = stringResource(R.string.cd_start_project),
                    tint = if (text.isNotBlank()) CreatoraTheme.colors.onAccent else CreatoraTheme.colors.textMuted,
                )
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            VideoFormat.entries.forEach { entry ->
                ChoiceChip(stringResource(entry.titleRes()), selected = entry == format, onClick = { format = entry })
            }
        }
        SecondaryButton(
            text = stringResource(R.string.home_ask_coach),
            onClick = { onAskCoach(text.trim().ifEmpty { null }) },
        )
    }
}

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

@Composable
private fun NextPostCard(next: NextPost, onOpenPublish: (Long) -> Unit) {
    val colors = CreatoraTheme.colors
    val context = LocalContext.current
    val whenText = DateUtils.formatDateTime(
        context, next.post.scheduledAt, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL,
    )
    Card(onClick = { onOpenPublish(next.post.projectId) }) {
        Text(stringResource(R.string.home_next_post_title), style = CreatoraTheme.typography.caption, color = colors.textMuted)
        Text(next.title.ifBlank { stringResource(R.string.home_untitled) }, style = CreatoraTheme.typography.cardTitle, color = colors.text, maxLines = 1)
        Text(
            stringResource(R.string.home_next_post_when, stringResource(next.post.platform.labelRes()), whenText),
            style = CreatoraTheme.typography.body,
            color = colors.textMuted,
        )
    }
}

@Composable
private fun IdeasSection(ideas: List<Idea>, onUseIdea: (Idea) -> Unit, onOpenIdeas: () -> Unit) {
    val spacing = CreatoraTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        SectionTitle(stringResource(R.string.home_ideas_title))
        if (ideas.isEmpty()) {
            Card(onClick = onOpenIdeas) {
                Text(stringResource(R.string.home_ideas_empty), style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.textMuted)
                Text(stringResource(R.string.home_ideas_empty_action), style = CreatoraTheme.typography.bodySemiBold, color = CreatoraTheme.colors.accentText)
            }
        } else {
            ideas.forEach { idea -> IdeaCard(idea, onUseIdea) }
        }
    }
}

@Composable
private fun IdeaCard(idea: Idea, onUseIdea: (Idea) -> Unit) {
    val colors = CreatoraTheme.colors
    Card(Modifier.heightIn(min = CreatoraTheme.dimens.minTouchTarget), onClick = { onUseIdea(idea) }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md)) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = colors.accentText)
            Column(Modifier.weight(1f)) {
                Text(idea.title, style = CreatoraTheme.typography.cardTitle, color = colors.text, maxLines = 2)
                if (idea.notes.isNotBlank()) Text(idea.notes, style = CreatoraTheme.typography.caption, color = colors.textMuted, maxLines = 1)
            }
            Text(stringResource(R.string.home_idea_use), style = CreatoraTheme.typography.bodySemiBold, color = colors.accentText)
        }
    }
}

@Composable
private fun ChannelSection(state: HomeUiState, onOpenGrow: () -> Unit) {
    val spacing = CreatoraTheme.spacing
    val stats = state.channel
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        SectionTitle(stringResource(R.string.home_channel_title))
        if (stats == null) {
            Card(onClick = onOpenGrow) {
                Text(stringResource(R.string.home_channel_empty), style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.textMuted)
                Text(stringResource(R.string.home_channel_empty_action), style = CreatoraTheme.typography.bodySemiBold, color = CreatoraTheme.colors.accentText)
            }
        } else {
            Text(stats.channel.title, style = CreatoraTheme.typography.caption, color = CreatoraTheme.colors.textMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                StatCard(stringResource(R.string.grow_subscribers), stats.subscribers, HomeViewModel.SNAPSHOT_DAYS, Modifier.weight(1f))
                StatCard(stringResource(R.string.grow_views), stats.views, HomeViewModel.SNAPSHOT_DAYS, Modifier.weight(1f))
                StatCard(stringResource(R.string.grow_videos), stats.videos, HomeViewModel.SNAPSHOT_DAYS, Modifier.weight(1f))
            }
        }
    }
}

private val PreviewProject = Project(id = 1, title = "Preview project", format = VideoFormat.LONG_VIDEO, step = 2)
private val PreviewState = HomeUiState(
    continueProject = PreviewProject,
    ideas = listOf(Idea(id = 1, title = "A day in my studio", notes = "Behind the scenes")),
    nextPost = NextPost(ScheduledPost(1, 1, Platform.YOUTUBE, System.currentTimeMillis() + 3_600_000), "Preview project"),
    channel = ChannelStats(MyChannel("c", "My channel"), Delta(1_200, 40), Delta(54_000, 3_100), Delta(18, 1)),
)

@Preview(name = "Light", showBackground = true)
@Composable
private fun HomeLightPreview() {
    CreatoraTheme(darkTheme = false) { HomeContent(PreviewState, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}) }
}

@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeDarkPreview() {
    CreatoraTheme(darkTheme = true) { HomeContent(HomeUiState(), {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}) }
}
