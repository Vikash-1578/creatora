package com.creatora.app.ui.onboarding

import android.Manifest
import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.creatora.app.R
import com.creatora.app.domain.account.AppLanguage
import com.creatora.app.domain.account.NotificationCategory
import com.creatora.app.domain.account.NotificationPrefs
import com.creatora.app.domain.account.Plan
import com.creatora.app.ui.accounts.AddAccountPanel
import com.creatora.app.ui.accounts.ChannelCard
import com.creatora.app.ui.accounts.LookupUi
import com.creatora.app.ui.accounts.message
import com.creatora.app.ui.components.PrimaryButton
import com.creatora.app.ui.components.SecondaryButton
import com.creatora.app.ui.notifications.NotificationToggles
import com.creatora.app.ui.theme.CreatoraTheme
import kotlinx.coroutines.launch

/** First run: language, welcome slides, Google sign-in, connect accounts, notifications, plan. */
@Composable
fun OnboardingScreen(modifier: Modifier = Modifier, viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    OnboardingContent(
        state = state,
        actions = OnboardingActions(
            chooseLanguage = viewModel::chooseLanguage,
            next = viewModel::next,
            signIn = viewModel::signIn,
            retryChannels = viewModel::loadChannels,
            confirmChannel = viewModel::confirmChannel,
            login = viewModel::login,
            lookUp = { input, platform -> viewModel.lookUp(input, platform) },
            dismissPasted = viewModel::dismissPasted,
            finishConnect = viewModel::finishConnect,
            answer = viewModel::answerWhatYouMake,
            toggle = viewModel::setNotification,
            milestone = viewModel::setMilestone,
            allowNotifications = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            continueFree = viewModel::continueFree,
            tryPro = viewModel::tryPro,
        ),
        modifier = modifier,
    )
}

class OnboardingActions(
    val chooseLanguage: (AppLanguage) -> Unit,
    val next: () -> Unit,
    val signIn: () -> Unit,
    val retryChannels: () -> Unit,
    val confirmChannel: (com.creatora.app.domain.account.ChannelPreview) -> Unit,
    val login: (com.creatora.app.domain.account.ConnectPlatform) -> Unit,
    val lookUp: (String, com.creatora.app.domain.account.ConnectPlatform) -> Unit,
    val dismissPasted: () -> Unit,
    val finishConnect: () -> Unit,
    val answer: (String) -> Unit,
    val toggle: (NotificationCategory, Boolean) -> Unit,
    val milestone: (Long) -> Unit,
    val allowNotifications: () -> Unit,
    val continueFree: () -> Unit,
    val tryPro: () -> Unit,
)

@Composable
fun OnboardingContent(state: OnboardingUiState, actions: OnboardingActions, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CreatoraTheme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        when (state.step) {
            OnboardingStep.LANGUAGE -> LanguageStep(state.language, actions.chooseLanguage, actions.next)
            OnboardingStep.WELCOME -> WelcomeStep(state, actions.signIn)
            OnboardingStep.CONNECT -> ConnectStep(state, actions)
            OnboardingStep.ASK -> AskStep(actions.answer)
            OnboardingStep.NOTIFICATIONS -> NotificationsStep(state.notificationPrefs, actions)
            OnboardingStep.PLAN -> PlanStep(state.proUnavailable, actions.continueFree, actions.tryPro)
        }
    }
}

@Composable
private fun StepColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CreatoraTheme.spacing.lg, vertical = CreatoraTheme.spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.lg),
    ) { content() }
}

@Composable
private fun Title(text: String, body: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
        Text(text, style = CreatoraTheme.typography.screenTitle, color = CreatoraTheme.colors.text)
        body?.let { Text(it, style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.textMuted) }
    }
}

private fun AppLanguage.labelRes() = when (this) {
    AppLanguage.ENGLISH -> R.string.language_english
    AppLanguage.HINDI -> R.string.language_hindi
    AppLanguage.HINGLISH -> R.string.language_hinglish
}

/** 1B: language first, preselected from the device. The text below is already in the chosen language. */
@Composable
private fun LanguageStep(selected: AppLanguage, onChoose: (AppLanguage) -> Unit, onContinue: () -> Unit) {
    val colors = CreatoraTheme.colors
    StepColumn {
        Title(stringResource(R.string.onboarding_language_title), stringResource(R.string.onboarding_language_body))
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md)) {
            AppLanguage.entries.forEach { language ->
                val chosen = language == selected
                val shape = CreatoraTheme.shapes.radius16
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = CreatoraTheme.dimens.minTouchTarget)
                        .clip(shape)
                        .background(if (chosen) colors.accentSoft else colors.surface)
                        .border(if (chosen) CreatoraTheme.dimens.borderWidth * 2 else CreatoraTheme.dimens.borderWidth, if (chosen) colors.accent else colors.border, shape)
                        .selectable(selected = chosen, role = Role.RadioButton, onClick = { onChoose(language) })
                        .padding(CreatoraTheme.spacing.lg),
                ) {
                    Text(stringResource(language.labelRes()), style = CreatoraTheme.typography.sectionTitle, color = colors.text)
                }
            }
        }
        PrimaryButton(stringResource(R.string.action_continue), onContinue, Modifier.fillMaxWidth())
    }
}

private data class Slide(val icon: ImageVector, val title: Int, val body: Int)

private val Slides = listOf(
    Slide(Icons.Outlined.AutoAwesome, R.string.welcome_1_title, R.string.welcome_1_body),
    Slide(Icons.Outlined.Movie, R.string.welcome_2_title, R.string.welcome_2_body),
    Slide(Icons.Outlined.BarChart, R.string.welcome_3_title, R.string.welcome_3_body),
)

/** 1B: three slides, Skip, and the big "Continue with Google". */
@Composable
private fun WelcomeStep(state: OnboardingUiState, onSignIn: () -> Unit) {
    val colors = CreatoraTheme.colors
    val spacing = CreatoraTheme.spacing
    val pager = rememberPagerState(pageCount = { Slides.size })
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { scope.launch { pager.animateScrollToPage(Slides.lastIndex) } }, modifier = Modifier.heightIn(min = CreatoraTheme.dimens.minTouchTarget)) {
                Text(stringResource(R.string.action_skip))
            }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            val slide = Slides[page]
            Column(
                Modifier.fillMaxSize().padding(horizontal = spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.lg, Alignment.CenterVertically),
            ) {
                Box(Modifier.size(CreatoraTheme.dimens.avatarSize * 2).clip(CircleShape).background(colors.accentSoft), contentAlignment = Alignment.Center) {
                    Icon(slide.icon, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(CreatoraTheme.dimens.avatarSize))
                }
                Text(stringResource(slide.title), style = CreatoraTheme.typography.screenTitle, color = colors.text, textAlign = TextAlign.Center)
                Text(stringResource(slide.body), style = CreatoraTheme.typography.body, color = colors.textMuted, textAlign = TextAlign.Center)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Slides.indices.forEach { index ->
                Box(
                    Modifier
                        .padding(CreatoraTheme.spacing.xs)
                        .size(CreatoraTheme.spacing.sm)
                        .clip(CircleShape)
                        .background(if (index == pager.currentPage) colors.accent else colors.border),
                )
            }
        }
        if (state.signInNotConfigured) Text(stringResource(R.string.signin_not_configured), style = CreatoraTheme.typography.body, color = colors.warning)
        if (state.signInFailed) Text(stringResource(R.string.signin_failed), style = CreatoraTheme.typography.body, color = colors.danger)
        PrimaryButton(
            text = stringResource(R.string.signin_google),
            onClick = onSignIn,
            modifier = Modifier.fillMaxWidth().heightIn(min = CreatoraTheme.dimens.minTouchTarget * 5 / 4),
            enabled = !state.signingIn,
        )
        Text(stringResource(R.string.signin_terms), style = CreatoraTheme.typography.caption, color = colors.textMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

/** 1C and 1E: the channels found on the Google account ("Is this you?"), plus adding other accounts. */
@Composable
private fun ConnectStep(state: OnboardingUiState, actions: OnboardingActions) {
    val colors = CreatoraTheme.colors
    StepColumn {
        Title(stringResource(R.string.connect_title), stringResource(R.string.connect_body))
        if (state.loadingChannels) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md)) {
                CircularProgressIndicator(Modifier.size(CreatoraTheme.dimens.icon))
                Text(stringResource(R.string.connect_loading), style = CreatoraTheme.typography.body, color = colors.textMuted)
            }
        }
        state.channelsProblem?.let {
            Text(it.message(), style = CreatoraTheme.typography.body, color = colors.danger)
            SecondaryButton(stringResource(R.string.action_try_again), actions.retryChannels, Modifier.fillMaxWidth())
        }
        if (!state.loadingChannels && state.channelsProblem == null && state.channels.isEmpty() && state.connectedTitles.isEmpty()) {
            Text(stringResource(R.string.connect_no_channels), style = CreatoraTheme.typography.body, color = colors.textMuted)
        }
        if (state.channels.size > 1) {
            Text(stringResource(R.string.connect_choose), style = CreatoraTheme.typography.cardTitle, color = colors.text)
        }
        state.channels.forEach { ChannelCard(it, stringResource(R.string.connect_is_this_you), { actions.confirmChannel(it) }) }
        state.connectedTitles.forEach {
            Text(stringResource(R.string.connect_connected, it), style = CreatoraTheme.typography.bodySemiBold, color = colors.success)
        }
        state.lookup.planLimitMax?.let {
            Text(stringResource(R.string.connect_plan_limit, it), style = CreatoraTheme.typography.body, color = colors.warning)
        }
        Text(stringResource(R.string.connect_add_other), style = CreatoraTheme.typography.cardTitle, color = colors.text)
        AddAccountPanel(state.lookup, actions.login, actions.lookUp, actions.confirmChannel, actions.dismissPasted)
        if (state.connectedTitles.isEmpty()) {
            SecondaryButton(stringResource(R.string.action_skip_for_now), actions.finishConnect, Modifier.fillMaxWidth())
        } else {
            PrimaryButton(stringResource(R.string.action_continue), actions.finishConnect, Modifier.fillMaxWidth())
        }
    }
}

/** 1D: the one question, only when nothing is connected. */
@Composable
private fun AskStep(onAnswer: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    StepColumn {
        Title(stringResource(R.string.ask_title), stringResource(R.string.ask_body))
        OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.ask_label)) }, singleLine = true)
        PrimaryButton(stringResource(R.string.action_continue), { onAnswer(text) }, Modifier.fillMaxWidth(), enabled = text.isNotBlank())
        SecondaryButton(stringResource(R.string.action_skip_for_now), { onAnswer("") }, Modifier.fillMaxWidth())
    }
}

/** 1F: permission ask and a switch per category. */
@Composable
private fun NotificationsStep(prefs: NotificationPrefs, actions: OnboardingActions) {
    StepColumn {
        Title(stringResource(R.string.onboarding_notifications_title), stringResource(R.string.onboarding_notifications_body))
        PrimaryButton(stringResource(R.string.notify_allow), actions.allowNotifications, Modifier.fillMaxWidth())
        NotificationToggles(prefs, actions.toggle, actions.milestone)
        SecondaryButton(stringResource(R.string.action_continue), actions.next, Modifier.fillMaxWidth())
    }
}

/** 1G: a soft plan card. Free is the primary choice. */
@Composable
private fun PlanStep(proUnavailable: Boolean, onFree: () -> Unit, onPro: () -> Unit) {
    val colors = CreatoraTheme.colors
    val shape = CreatoraTheme.shapes.radius16
    StepColumn {
        Title(stringResource(R.string.plan_title), stringResource(R.string.plan_body))
        Column(
            Modifier.fillMaxWidth().clip(shape).background(colors.surface).border(CreatoraTheme.dimens.borderWidth, colors.border, shape).padding(CreatoraTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm),
        ) {
            Text(stringResource(R.string.plan_free_name), style = CreatoraTheme.typography.cardTitle, color = colors.text)
            Text(stringResource(R.string.plan_free_features, Plan.FREE.aiLimit, Plan.FREE.maxAccounts), style = CreatoraTheme.typography.body, color = colors.textMuted)
            Text(stringResource(R.string.plan_pro_name), style = CreatoraTheme.typography.cardTitle, color = colors.text)
            Text(stringResource(R.string.plan_pro_features, Plan.PRO.aiLimit, Plan.PRO.maxAccounts), style = CreatoraTheme.typography.body, color = colors.textMuted)
        }
        if (proUnavailable) Text(stringResource(R.string.billing_unavailable), style = CreatoraTheme.typography.body, color = colors.warning)
        PrimaryButton(stringResource(R.string.plan_continue_free), onFree, Modifier.fillMaxWidth())
        SecondaryButton(stringResource(R.string.plan_see_pro), onPro, Modifier.fillMaxWidth())
    }
}

private val PreviewActions = OnboardingActions({}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}, { _, _ -> }, {}, {}, {}, {})

@Preview(name = "Light language", showBackground = true)
@Composable
private fun LanguageLightPreview() {
    CreatoraTheme(darkTheme = false) { OnboardingContent(OnboardingUiState(), PreviewActions) }
}

@Preview(name = "Dark welcome", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WelcomeDarkPreview() {
    CreatoraTheme(darkTheme = true) { OnboardingContent(OnboardingUiState(step = OnboardingStep.WELCOME), PreviewActions) }
}

@Preview(name = "Light connect", showBackground = true)
@Composable
private fun ConnectLightPreview() {
    CreatoraTheme(darkTheme = false) { OnboardingContent(OnboardingUiState(step = OnboardingStep.CONNECT, lookup = LookupUi()), PreviewActions) }
}

@Preview(name = "Dark plan", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PlanDarkPreview() {
    CreatoraTheme(darkTheme = true) { OnboardingContent(OnboardingUiState(step = OnboardingStep.PLAN), PreviewActions) }
}
