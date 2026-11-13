package com.creatora.app.ui.create

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.creatora.app.R
import com.creatora.app.domain.model.Character
import com.creatora.app.domain.model.Project
import com.creatora.app.domain.model.StepState
import com.creatora.app.domain.model.VideoFormat
import com.creatora.app.domain.model.VoicePreset
import com.creatora.app.domain.model.WizardStep
import com.creatora.app.ui.components.HeaderIconButton
import com.creatora.app.ui.components.PrimaryButton
import com.creatora.app.ui.components.SecondaryButton
import com.creatora.app.ui.render.RenderPanel
import com.creatora.app.ui.theme.CreatoraTheme

/** Callbacks the step screens use to edit the project. */
class WizardActions(
    val onTitle: (String) -> Unit,
    val onFormat: (VideoFormat) -> Unit,
    val onBrief: (String) -> Unit,
    val onHook: (String) -> Unit,
    val onStructure: (String) -> Unit,
    val onCallToAction: (String) -> Unit,
    val onPronunciations: (String) -> Unit,
    val onToggleCast: (Long) -> Unit,
    val onNarratorVoice: (Long?) -> Unit,
    val onAssignVoice: (Character, Long?) -> Unit,
    val onPreview: (VoicePreset?) -> Unit,
    val onStopPreview: () -> Unit,
    val onLicensedOnly: (Boolean) -> Unit,
    val onOpenLibrary: () -> Unit,
    val onDraftScript: () -> Unit = {},
    val onApplyDraft: () -> Unit = {},
    val onDismissDraft: () -> Unit = {},
    val onPublishText: () -> Unit = {},
    val onDismissPublishText: () -> Unit = {},
    val onOpenAiProviders: () -> Unit = {},
    val onAngle: (String) -> Unit = {},
    val onTone: (String) -> Unit = {},
    val onAudience: (String) -> Unit = {},
    val onTarget: (Int) -> Unit = {},
    val onSuggestBrief: () -> Unit = {},
    val onRewriteLine: (String, String) -> Unit = { _, _ -> },
    val onShorten: () -> Unit = {},
    val onMakeImage: (String) -> Unit = {},
    val onFixStep: (Int) -> Unit = {},
    val onRunAuto: () -> Unit = {},
    val onFinishAuto: () -> Unit = {},
    val onCancelAuto: () -> Unit = {},
)

@Composable
fun WizardScreen(
    onClose: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenAiProviders: () -> Unit,
    onOpenThumbnail: (projectId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WizardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sampleText = stringResource(R.string.wizard_voice_sample)
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.flush() }

    val actions = WizardActions(
        onTitle = viewModel::setTitle,
        onFormat = viewModel::setFormat,
        onBrief = viewModel::setBrief,
        onHook = viewModel::setHook,
        onStructure = viewModel::setStructure,
        onCallToAction = viewModel::setCallToAction,
        onPronunciations = viewModel::setPronunciations,
        onToggleCast = viewModel::toggleCast,
        onNarratorVoice = viewModel::setNarratorVoice,
        onAssignVoice = viewModel::assignVoice,
        onPreview = { viewModel.preview(it, sampleText) },
        onStopPreview = viewModel::stopPreview,
        onLicensedOnly = viewModel::setLicensedFootageOnly,
        onOpenLibrary = onOpenLibrary,
        onDraftScript = viewModel::draftScript,
        onApplyDraft = viewModel::applyDraft,
        onDismissDraft = viewModel::dismissDraft,
        onPublishText = viewModel::generatePublishText,
        onDismissPublishText = viewModel::dismissPublishText,
        onOpenAiProviders = onOpenAiProviders,
        onAngle = viewModel::setAngle,
        onTone = viewModel::setTone,
        onAudience = viewModel::setAudience,
        onTarget = viewModel::setTargetSeconds,
        onSuggestBrief = viewModel::suggestBrief,
        onRewriteLine = viewModel::rewriteLine,
        onShorten = viewModel::shortenScript,
        onMakeImage = viewModel::makeImage,
        onFixStep = viewModel::goToStep,
        onRunAuto = viewModel::runAuto,
        onFinishAuto = viewModel::finishAuto,
        onCancelAuto = viewModel::cancelAuto,
    )
    WizardContent(
        state = state,
        actions = actions,
        onStep = viewModel::goToStep,
        onFinish = {
            viewModel.markReady()
            onClose()
        },
        onClose = onClose,
        generatePanel = { RenderPanel(onOpenLibrary = onOpenLibrary, onOpenThumbnail = onOpenThumbnail, autoStart = state.autoRender) },
        modifier = modifier,
    )
}

@Composable
fun WizardContent(
    state: WizardUiState,
    actions: WizardActions,
    onStep: (Int) -> Unit,
    onFinish: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    generatePanel: @Composable () -> Unit = {},
) {
    val spacing = CreatoraTheme.spacing
    val project = state.project
    val index = state.step.ordinal
    val last = WizardStep.entries.lastIndex

    BackHandler(enabled = index > 0) { onStep(index - 1) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CreatoraTheme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.sm, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            HeaderIconButton(
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
                onClick = { if (index > 0) onStep(index - 1) else onClose() },
                outlined = false,
            )
            Column {
                Text(
                    text = stringResource(R.string.wizard_step_of, index + 1, last + 1),
                    style = CreatoraTheme.typography.caption,
                    color = CreatoraTheme.colors.textMuted,
                )
                Text(
                    text = stringResource(state.step.titleRes()),
                    style = CreatoraTheme.typography.screenTitle,
                    color = CreatoraTheme.colors.text,
                    modifier = Modifier.semantics { heading() },
                )
                project?.stepStates?.get(state.step)?.let { stepState ->
                    val label = when (stepState) {
                        StepState.EMPTY -> null
                        StepState.AI_DRAFT -> R.string.wizard_state_ai
                        StepState.EDITED -> R.string.wizard_state_edited
                        StepState.APPROVED -> R.string.wizard_state_approved
                        StepState.OUT_OF_DATE -> R.string.wizard_state_out_of_date
                    }
                    label?.let { Text(stringResource(it), style = CreatoraTheme.typography.caption, color = if (stepState == StepState.OUT_OF_DATE) CreatoraTheme.colors.warning else CreatoraTheme.colors.accentText) }
                }
            }
        }
        ProgressSegments(
            current = index,
            total = last + 1,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg),
        )

        Box(Modifier.weight(1f)) {
            if (project != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = spacing.lg, vertical = spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    when (state.step) {
                        WizardStep.BRIEF -> BriefStep(project, state, actions)
                        WizardStep.SCRIPT -> ScriptStep(project, state, actions)
                        WizardStep.CAST -> CastStep(project, state, actions)
                        WizardStep.VOICE -> VoiceStep(project, state, actions)
                        WizardStep.FOOTAGE -> FootageStep(project, state, actions)
                        WizardStep.REVIEW -> ReviewStep(project, state, actions)
                        WizardStep.GENERATE -> GenerateStep(state, generatePanel)
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg, vertical = spacing.md),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            if (index > 0) {
                SecondaryButton(stringResource(R.string.wizard_back), { onStep(index - 1) }, Modifier.weight(1f))
            }
            if (index < last) {
                PrimaryButton(stringResource(R.string.wizard_next), { onStep(index + 1) }, Modifier.weight(2f), enabled = state.canGoNext)
            } else {
                PrimaryButton(
                    text = stringResource(R.string.wizard_mark_ready),
                    onClick = onFinish,
                    modifier = Modifier.weight(2f),
                    enabled = state.blockers.isEmpty(),
                )
            }
        }
    }

    state.auto?.let { AutoDialog(it, actions) }
    state.ai.draft?.let { DraftDialog(it, actions.onApplyDraft, actions.onDismissDraft) }
    state.ai.publishText?.let { PublishTextDialog(it, actions.onDismissPublishText) }
}

/** One segment per step, filled up to the current one. */
@Composable
internal fun ProgressSegments(current: Int, total: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.xs),
    ) {
        repeat(total) { segment ->
            Box(
                Modifier
                    .weight(1f)
                    .height(CreatoraTheme.dimens.progressHeight)
                    .background(
                        if (segment <= current) CreatoraTheme.colors.accent else CreatoraTheme.colors.surfaceAlt,
                        CreatoraTheme.shapes.radius8,
                    ),
            )
        }
    }
}

fun WizardStep.titleRes(): Int = when (this) {
    WizardStep.BRIEF -> R.string.wizard_step_brief
    WizardStep.SCRIPT -> R.string.wizard_step_script
    WizardStep.CAST -> R.string.wizard_step_cast
    WizardStep.VOICE -> R.string.wizard_step_voice
    WizardStep.FOOTAGE -> R.string.wizard_step_footage
    WizardStep.REVIEW -> R.string.wizard_step_review
    WizardStep.GENERATE -> R.string.wizard_step_generate
}

@Composable
private fun WizardPreview(darkTheme: Boolean, step: WizardStep) {
    val project = Project(
        id = 1, title = "Preview project", format = VideoFormat.LONG_VIDEO, step = step.ordinal,
        hook = "Preview hook", structure = "A few words for the body.",
    )
    CreatoraTheme(darkTheme = darkTheme) {
        WizardContent(
            state = WizardUiState(project = project),
            actions = WizardActions({}, {}, {}, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}, {}),
            onStep = {}, onFinish = {}, onClose = {},
        )
    }
}

@Preview(name = "Brief, light", showBackground = true)
@Composable
private fun WizardBriefLightPreview() = WizardPreview(false, WizardStep.BRIEF)

@Preview(name = "Brief, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WizardBriefDarkPreview() = WizardPreview(true, WizardStep.BRIEF)

@Preview(name = "Review, light", showBackground = true)
@Composable
private fun WizardReviewLightPreview() = WizardPreview(false, WizardStep.REVIEW)

@Preview(name = "Review, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WizardReviewDarkPreview() = WizardPreview(true, WizardStep.REVIEW)
