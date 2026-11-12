package com.creatora.app.ui.home

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.creatora.app.R
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.account.Goal
import com.creatora.app.domain.account.VideoStats
import com.creatora.app.domain.home.AccountSelection
import com.creatora.app.domain.model.Project
import com.creatora.app.domain.model.VideoFormat
import com.creatora.app.domain.model.WizardStep
import com.creatora.app.domain.repository.UpcomingPost
import com.creatora.app.ui.accounts.labelRes
import com.creatora.app.ui.components.PrimaryButton
import com.creatora.app.ui.components.TabScreen
import com.creatora.app.ui.create.titleRes
import com.creatora.app.ui.theme.CreatoraTheme

/** Everything Home shows, gathered from its ViewModels. */
data class HomeModel(
    val home: HomeUiState = HomeUiState(),
    val cards: ChannelCardsState = ChannelCardsState(phase = CardsPhase.NO_ACCOUNT),
    val focus: FocusState = FocusState.NoAccount,
    val ideas: IdeasState = IdeasState.NoAccount,
    val goals: GoalsState = GoalsState(loading = false),
    val trending: TrendingState = TrendingState.Loading,
)

class HomeActions(
    val bell: () -> Unit = {},
    val coach: () -> Unit = {},
    val profile: () -> Unit = {},
    val connect: () -> Unit = {},
    val select: (AccountSelection) -> Unit = {},
    val hideAmounts: (Boolean) -> Unit = {},
    val retry: () -> Unit = {},
    val openUpload: (UploadRowLike) -> Unit = {},
    val fix: () -> Unit = {},
    val later: () -> Unit = {},
    val ideaCreate: (com.creatora.app.domain.home.CreatorIdea) -> Unit = {},
    val ideaSave: (com.creatora.app.domain.home.CreatorIdea) -> Unit = {},
    val ideaDismiss: (com.creatora.app.domain.home.CreatorIdea) -> Unit = {},
    val continueProject: (Long) -> Unit = {},
    val addGoal: (com.creatora.app.domain.account.GoalMetric, Long, Int) -> Unit = { _, _, _ -> },
    val editGoal: (Goal, Long, Int) -> Unit = { _, _, _ -> },
    val deleteGoal: (Goal) -> Unit = {},
    val planGoal: (GoalItem, Boolean) -> Unit = { _, _ -> },
    val celebrated: () -> Unit = {},
    val streakUnit: (com.creatora.app.domain.home.StreakUnit) -> Unit = {},
    val openVideo: (VideoStats) -> Unit = {},
    val useAsIdea: (VideoStats) -> Unit = {},
    val refreshTrending: () -> Unit = {},
    val newVideo: () -> Unit = {},
    val ideasQuick: () -> Unit = {},
    val thumbnail: () -> Unit = {},
)

typealias UploadRowLike = com.creatora.app.domain.home.UploadRow

/** Home tab: your accounts, real numbers, today's focus and ideas, goals and what is trending. */
@Composable
fun HomeScreen(
    onProfileClick: () -> Unit,
    onNotifications: () -> Unit,
    onOpenCoach: () -> Unit,
    onProjectOpened: (projectId: Long) -> Unit,
    onNewVideo: () -> Unit,
    onOpenThumbnail: (projectId: Long) -> Unit,
    onConnectChannel: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
    cardsViewModel: ChannelCardsViewModel = hiltViewModel(),
    focusViewModel: FocusViewModel = hiltViewModel(),
    ideasViewModel: IdeasViewModel = hiltViewModel(),
    goalsViewModel: GoalsViewModel = hiltViewModel(),
    trendingViewModel: TrendingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val model = HomeModel(
        home = viewModel.state.collectAsStateWithLifecycle().value,
        cards = cardsViewModel.state.collectAsStateWithLifecycle().value,
        focus = focusViewModel.state.collectAsStateWithLifecycle().value,
        ideas = ideasViewModel.state.collectAsStateWithLifecycle().value,
        goals = goalsViewModel.state.collectAsStateWithLifecycle().value,
        trending = trendingViewModel.state.collectAsStateWithLifecycle().value,
    )
    HomeContent(
        model = model,
        actions = HomeActions(
            bell = onNotifications, coach = onOpenCoach, profile = onProfileClick, connect = onConnectChannel,
            select = viewModel::select, hideAmounts = cardsViewModel::setHideAmounts, retry = cardsViewModel::refresh,
            openUpload = cardsViewModel::explain,
            fix = { focusViewModel.fixIt(onProjectOpened) }, later = focusViewModel::later,
            ideaCreate = { ideasViewModel.create(it, onProjectOpened) }, ideaSave = ideasViewModel::save, ideaDismiss = ideasViewModel::dismiss,
            continueProject = onProjectOpened,
            addGoal = { metric, target, days -> goalsViewModel.add(metric, target, days, forAll = model.home.selection == AccountSelection.All, startValue = null) },
            editGoal = goalsViewModel::edit, deleteGoal = goalsViewModel::delete, planGoal = goalsViewModel::plan,
            celebrated = goalsViewModel::dismissCelebration, streakUnit = cardsViewModel::setStreakUnit,
            openVideo = { video -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=${video.id}"))) } },
            useAsIdea = trendingViewModel::useAsIdea, refreshTrending = trendingViewModel::refresh,
            newVideo = onNewVideo, ideasQuick = onOpenCoach,
            thumbnail = { model.home.continueProject?.let { onOpenThumbnail(it.id) } },
        ),
        onWhyClosed = cardsViewModel::closeWhy,
        why = cardsViewModel.state.collectAsStateWithLifecycle().value.why,
        modifier = modifier,
    )
}

@Composable
fun HomeContent(model: HomeModel, actions: HomeActions, why: WhyState = WhyState.Closed, onWhyClosed: () -> Unit = {}, modifier: Modifier = Modifier) {
    val spacing = CreatoraTheme.spacing
    var goalDialog by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var comingSoon by rememberSaveable { mutableStateOf(false) }
    var noProject by rememberSaveable { mutableStateOf(false) }
    val hasAccounts = model.home.hasAccounts
    TabScreen(modifier) {
        HomeHeader(model.home, actions.bell, actions.coach, actions.profile)
        Column(
            modifier = Modifier.padding(horizontal = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.xxl),
        ) {
            if (!hasAccounts) ConnectCard(actions.connect) else AccountSwitcher(model.home.accounts, model.home.selection, actions.select)
            if (hasAccounts) SnapshotSection(model.cards, actions.hideAmounts, actions.retry)
            if (hasAccounts) FocusSection(model.focus, actions.fix, actions.later)
            if (hasAccounts) IdeasSection(model.ideas, actions.ideaCreate, actions.ideaSave, actions.ideaDismiss)
            model.home.continueProject?.let { ContinueCard(it, actions.continueProject) }
            if (hasAccounts) UploadsSection(model.cards, actions.openUpload)
            if (hasAccounts) UpcomingSection(model.home.upcoming, actions.newVideo)
            if (hasAccounts) GoalsSection(
                model.goals, canAdd = true,
                onAdd = { editing = null; goalDialog = true },
                onEdit = { editing = it.id; goalDialog = true },
                onDelete = actions.deleteGoal, onPlan = actions.planGoal,
            )
            if (hasAccounts) StreakSection(model.cards, actions.streakUnit)
            TrendingSection(model.trending, actions.openVideo, actions.useAsIdea, actions.refreshTrending)
            QuickActions(
                actions.newVideo, actions.ideasQuick, { comingSoon = true },
                {
                    if (model.home.continueProject != null) {
                        actions.thumbnail()
                    } else {
                        noProject = true
                    }
                },
            )
        }
    }
    if (goalDialog) {
        val existing = model.goals.goals.firstOrNull { it.goal.id == editing }?.goal
        GoalDialog(
            existing = existing,
            onSave = { metric, target, days -> goalDialog = false; if (existing == null) actions.addGoal(metric, target, days) else actions.editGoal(existing, target, days) },
            onDismiss = { goalDialog = false },
        )
    }
    CelebrationDialog(model.goals.celebrating, actions.celebrated)
    WhyDialog(why, onWhyClosed)
    if (comingSoon) InfoDialog(R.string.home_keyword_soon) { comingSoon = false }
    if (noProject) InfoDialog(R.string.home_thumbnail_needs_project) { noProject = false }
}

@Composable
private fun InfoDialog(textRes: Int, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        text = { BodyText(stringResource(textRes), muted = false) },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.action_close)) } },
        containerColor = CreatoraTheme.colors.surface,
    )
}

/** Shown on top when no account is connected. */
@Composable
private fun ConnectCard(onConnect: () -> Unit) {
    HomeCard {
        Text(stringResource(R.string.home_connect_title), style = CreatoraTheme.typography.sectionTitle, color = CreatoraTheme.colors.text)
        BodyText(stringResource(R.string.home_connect_body))
        PrimaryButton(stringResource(R.string.home_channel_action), onConnect, Modifier.fillMaxWidth())
    }
}

@Composable
private fun ContinueCard(project: Project, onContinue: (Long) -> Unit) {
    val step = WizardStep.entries[project.step.coerceIn(0, WizardStep.entries.lastIndex)]
    HomeSection(R.string.home_continue_title) {
        HomeCard {
            Text(project.title, style = CreatoraTheme.typography.cardTitle, color = CreatoraTheme.colors.text, maxLines = 1)
            BodyText(stringResource(R.string.home_continue_step, step.ordinal + 1, WizardStep.entries.size, stringResource(step.titleRes())))
            PrimaryButton(stringResource(R.string.home_continue_action), { onContinue(project.id) })
        }
    }
}

@Composable
private fun UpcomingSection(posts: List<UpcomingPost>, onPlan: () -> Unit) {
    HomeSection(R.string.home_upcoming_title) {
        HomeCard {
            if (posts.isEmpty()) {
                BodyText(stringResource(R.string.home_upcoming_empty))
                PrimaryButton(stringResource(R.string.home_action_new_video), onPlan)
            }
            posts.forEach { post ->
                Column(Modifier.fillMaxWidth().heightIn(min = CreatoraTheme.dimens.minTouchTarget), verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.xs)) {
                    Text(post.title.ifBlank { stringResource(R.string.home_untitled) }, style = CreatoraTheme.typography.cardTitle, color = CreatoraTheme.colors.text, maxLines = 1)
                    Text(
                        stringResource(R.string.home_upcoming_when, stringResource(post.platform.labelRes()), DateUtils.formatDateTime(androidx.compose.ui.platform.LocalContext.current, post.scheduledAt, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL)),
                        style = CreatoraTheme.typography.caption, color = CreatoraTheme.colors.textMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActions(onNew: () -> Unit, onIdeas: () -> Unit, onKeyword: () -> Unit, onThumbnail: () -> Unit) {
    HomeSection(R.string.home_quick_actions) {
        Row(horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md)) {
            QuickAction(Icons.Outlined.Add, R.string.home_action_new_video, onNew, Modifier.weight(1f))
            QuickAction(Icons.Outlined.AutoAwesome, R.string.home_action_ideas, onIdeas, Modifier.weight(1f))
            QuickAction(Icons.Outlined.Search, R.string.home_action_keywords, onKeyword, Modifier.weight(1f))
            QuickAction(Icons.Outlined.Image, R.string.home_action_thumbnail, onThumbnail, Modifier.weight(1f))
        }
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
            .padding(vertical = CreatoraTheme.spacing.md, horizontal = CreatoraTheme.spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.xs),
    ) {
        Icon(icon, contentDescription = null, tint = colors.accentText)
        Text(stringResource(labelRes), style = CreatoraTheme.typography.caption, color = colors.text, maxLines = 2)
    }
}

private val PreviewProject = Project(id = 1, title = "Preview project", format = VideoFormat.LONG_VIDEO, step = 2)

@Preview(name = "Light", showBackground = true)
@Composable
private fun HomeLightPreview() {
    CreatoraTheme(darkTheme = false) {
        HomeContent(
            HomeModel(
                home = HomeUiState(firstName = "Asha", accounts = listOf(com.creatora.app.domain.account.ConnectedAccount(ConnectPlatform.YOUTUBE, "UC1", "My channel")), selection = AccountSelection.One("YOUTUBE_UC1"), continueProject = PreviewProject),
                cards = ChannelCardsState(phase = CardsPhase.LOADING),
                focus = FocusState.Loading, ideas = IdeasState.Loading,
            ),
            HomeActions(),
        )
    }
}

@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeDarkPreview() {
    CreatoraTheme(darkTheme = true) { HomeContent(HomeModel(home = HomeUiState(firstName = "Asha")), HomeActions()) }
}
