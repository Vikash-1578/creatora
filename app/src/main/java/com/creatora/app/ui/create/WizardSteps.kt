package com.creatora.app.ui.create

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.creatora.app.R
import com.creatora.app.domain.account.PlanLimits
import com.creatora.app.domain.create.ImageOutcome
import com.creatora.app.domain.create.scriptLines
import com.creatora.app.domain.model.Project
import com.creatora.app.domain.model.VideoFormat
import com.creatora.app.domain.model.VoicePreset
import com.creatora.app.domain.model.readingSeconds
import com.creatora.app.domain.render.splitSentences
import com.creatora.app.domain.score.CheckId
import com.creatora.app.domain.score.CheckStatus
import com.creatora.app.domain.score.Reason
import com.creatora.app.domain.score.VideoScore
import com.creatora.app.ui.ai.AiErrors
import com.creatora.app.ui.components.ChoiceChip
import com.creatora.app.ui.components.EmptyState
import com.creatora.app.ui.components.ListGroup
import com.creatora.app.ui.components.ListRowDivider
import com.creatora.app.ui.components.SecondaryButton
import com.creatora.app.ui.theme.CreatoraTheme

@Composable
private fun Intro(text: String) {
    Text(text, style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.textMuted)
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        supportingText = hint?.let { { Text(it) } },
        minLines = minLines,
        singleLine = minLines == 1,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun durationText(seconds: Int): String =
    if (seconds < 60) {
        stringResource(R.string.wizard_duration_seconds, seconds)
    } else {
        stringResource(R.string.wizard_duration_minutes, seconds / 60, seconds % 60)
    }

@Composable
fun BriefStep(project: Project, state: WizardUiState, actions: WizardActions) {
    Field(stringResource(R.string.wizard_title_label), project.title, actions.onTitle)
    Field(
        label = stringResource(R.string.wizard_brief_label),
        value = project.brief,
        onChange = actions.onBrief,
        hint = stringResource(R.string.wizard_brief_hint),
        minLines = 4,
    )
    if (project.sourceLink.isNotBlank()) Intro(stringResource(R.string.wizard_inspired_by, project.sourceLink))
    AiAction(stringResource(R.string.wizard_brief_ai), project.title.isNotBlank(), state, actions, actions.onSuggestBrief)
    CreditHint(PlanLimits.costs.text)
    Field(stringResource(R.string.wizard_angle_label), project.angle, actions.onAngle)
    Field(stringResource(R.string.wizard_tone_label), project.tone, actions.onTone)
    Field(stringResource(R.string.wizard_audience_label), project.audience, actions.onAudience)
    val max = state.plan.limits.maxVideoSeconds
    Field(
        label = stringResource(R.string.wizard_length_label),
        value = project.targetSeconds.takeIf { it > 0 }?.toString().orEmpty(),
        onChange = { actions.onTarget(it.filter { c -> c.isDigit() }.take(4).toIntOrNull() ?: 0) },
        hint = stringResource(R.string.wizard_length_hint, max),
    )
    if (project.targetSeconds > max) {
        Text(stringResource(R.string.wizard_length_over, max), style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.warning)
    }
    Text(stringResource(R.string.wizard_format_label), style = CreatoraTheme.typography.bodySemiBold, color = CreatoraTheme.colors.text)
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm),
    ) {
        VideoFormat.entries.forEach { format ->
            ChoiceChip(stringResource(format.titleRes()), project.format == format, { actions.onFormat(format) })
        }
    }
}

/** A button that calls the AI, with a progress line while it works and the errors if it fails. */
@Composable
internal fun AiAction(label: String, enabled: Boolean, state: WizardUiState, actions: WizardActions, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
        SecondaryButton(
            text = if (state.ai.busy) stringResource(R.string.wizard_ai_working) else label,
            onClick = onClick,
            enabled = enabled && !state.ai.busy,
        )
        state.ai.errors?.let { AiErrors(it, actions.onOpenAiProviders) }
    }
}

@Composable
fun ScriptStep(project: Project, state: WizardUiState, actions: WizardActions) {
    AiAction(stringResource(R.string.wizard_ai_draft), project.brief.isNotBlank(), state, actions, actions.onDraftScript)
    if (project.brief.isBlank()) Intro(stringResource(R.string.wizard_ai_draft_needs_brief))
    Field(
        stringResource(R.string.wizard_script_hook), project.hook, actions.onHook,
        hint = stringResource(R.string.wizard_script_hook_hint), minLines = 2,
    )
    Field(
        stringResource(R.string.wizard_script_structure), project.structure, actions.onStructure,
        hint = stringResource(R.string.wizard_script_structure_hint), minLines = 5,
    )
    Field(
        stringResource(R.string.wizard_script_cta), project.callToAction, actions.onCallToAction,
        hint = stringResource(R.string.wizard_script_cta_hint), minLines = 2,
    )
    Text(
        text = stringResource(
            R.string.wizard_script_stats,
            pluralStringResource(R.plurals.wizard_words, state.words, state.words),
            durationText(readingSeconds(state.words)),
        ),
        style = CreatoraTheme.typography.bodySemiBold,
        color = CreatoraTheme.colors.accentText,
    )
    if (state.overPlanLength) {
        Text(
            stringResource(R.string.wizard_script_over, durationText(state.seconds), state.plan.limits.maxVideoSeconds),
            style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.warning,
        )
        AiAction(stringResource(R.string.wizard_script_shorten), state.words > 0, state, actions, actions.onShorten)
        CreditHint(PlanLimits.costs.text)
    }
    ScriptLines(project, state, actions)
    Field(
        stringResource(R.string.wizard_pronunciations), project.pronunciations, actions.onPronunciations,
        hint = stringResource(R.string.wizard_pronunciations_hint), minLines = 3,
    )
}

/** Each sentence of the script with AI actions to rewrite it, shorten it, lengthen it or change its tone. */
@Composable
private fun ScriptLines(project: Project, state: WizardUiState, actions: WizardActions) {
    val lines = scriptLines(project.script)
    if (lines.isEmpty()) return
    Text(stringResource(R.string.wizard_lines_title), style = CreatoraTheme.typography.cardTitle, color = CreatoraTheme.colors.text)
    CreditHint(PlanLimits.costs.text)
    val tone = project.tone.ifBlank { stringResource(R.string.wizard_tone_default) }
    val instructions = listOf(
        R.string.wizard_line_rewrite to "rewrite it with different words",
        R.string.wizard_line_shorter to "make it shorter",
        R.string.wizard_line_longer to "make it a little longer",
    )
    lines.take(MAX_LINES).forEach { line ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.xs)) {
            Text(line, style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.text)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.xs)) {
                instructions.forEach { (label, how) ->
                    TextButton(onClick = { actions.onRewriteLine(line, how) }, enabled = !state.ai.busy, modifier = Modifier.heightIn(min = CreatoraTheme.dimens.minTouchTarget)) {
                        Text(stringResource(label))
                    }
                }
                TextButton(onClick = { actions.onRewriteLine(line, "change the tone to: $tone") }, enabled = !state.ai.busy, modifier = Modifier.heightIn(min = CreatoraTheme.dimens.minTouchTarget)) {
                    Text(stringResource(R.string.wizard_line_tone))
                }
            }
        }
    }
    if (state.ai.busy) Text(stringResource(R.string.wizard_ai_working), style = CreatoraTheme.typography.caption, color = CreatoraTheme.colors.textMuted)
    state.ai.errors?.let { AiErrors(it, actions.onOpenAiProviders) }
}

private const val MAX_LINES = 30

/** What an AI action costs in credits, shown before the button is tapped. */
@Composable
internal fun CreditHint(credits: Int) {
    Text(
        pluralStringResource(R.plurals.create_credit_cost, credits, credits),
        style = CreatoraTheme.typography.caption, color = CreatoraTheme.colors.textMuted,
    )
}

@Composable
fun CastStep(project: Project, state: WizardUiState, actions: WizardActions) {
    Intro(stringResource(R.string.wizard_cast_body))
    if (state.characters.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.Person,
            title = stringResource(R.string.wizard_cast_empty_title),
            body = stringResource(R.string.wizard_cast_empty_body),
            actionLabel = stringResource(R.string.wizard_open_library),
            onAction = actions.onOpenLibrary,
        )
    } else {
        ListGroup {
            state.characters.forEachIndexed { index, character ->
                if (index > 0) ListRowDivider()
                val checked = character.id in project.castIds
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = CreatoraTheme.dimens.minTouchTarget)
                        .toggleable(checked, role = Role.Checkbox, onValueChange = { actions.onToggleCast(character.id) })
                        .padding(horizontal = CreatoraTheme.spacing.lg, vertical = CreatoraTheme.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md),
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(
                            checkedColor = CreatoraTheme.colors.accent,
                            checkmarkColor = CreatoraTheme.colors.onAccent,
                            uncheckedColor = CreatoraTheme.colors.textMuted,
                        ),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(character.name, style = CreatoraTheme.typography.bodyBold, color = CreatoraTheme.colors.text)
                        if (character.description.isNotBlank()) {
                            Text(character.description, style = CreatoraTheme.typography.caption, color = CreatoraTheme.colors.textMuted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VoiceStep(project: Project, state: WizardUiState, actions: WizardActions) {
    Intro(stringResource(R.string.wizard_voice_body))
    if (state.speechUnavailable) {
        Text(stringResource(R.string.wizard_voice_tts_missing), style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.warning)
    }
    if (state.voices.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.RecordVoiceOver,
            title = stringResource(R.string.wizard_voice_empty_title),
            body = stringResource(R.string.wizard_voice_empty_body),
            actionLabel = stringResource(R.string.wizard_open_library),
            onAction = actions.onOpenLibrary,
        )
    }
    VoiceChooser(
        name = stringResource(R.string.wizard_voice_narrator),
        selectedId = project.narratorVoiceId,
        voices = state.voices,
        onSelect = actions.onNarratorVoice,
        onPreview = actions.onPreview,
    )
    state.cast.forEach { character ->
        VoiceChooser(
            name = character.name,
            selectedId = character.voicePresetId,
            voices = state.voices,
            onSelect = { actions.onAssignVoice(character, it) },
            onPreview = actions.onPreview,
        )
    }
    // Premium voices and recording need a server and a recorder; they are shown as coming later, never faked.
    ListGroup {
        InfoRow(stringResource(R.string.wizard_voice_premium), stringResource(if (state.plan.limits.premiumVoices) R.string.wizard_coming_later else R.string.wizard_pro_only))
        ListRowDivider()
        InfoRow(stringResource(R.string.wizard_voice_record), stringResource(R.string.wizard_coming_later))
    }
    Intro(stringResource(R.string.wizard_voice_later_note))
}

@Composable
private fun VoiceChooser(
    name: String,
    selectedId: Long?,
    voices: List<VoicePreset>,
    onSelect: (Long?) -> Unit,
    onPreview: (VoicePreset?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
        Text(name, style = CreatoraTheme.typography.cardTitle, color = CreatoraTheme.colors.text)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm),
        ) {
            ChoiceChip(stringResource(R.string.wizard_voice_default), selectedId == null, { onSelect(null) })
            voices.forEach { voice ->
                ChoiceChip(voice.name, selectedId == voice.id, { onSelect(voice.id) })
            }
        }
        SecondaryButton(
            text = stringResource(R.string.wizard_voice_preview),
            onClick = { onPreview(voices.firstOrNull { it.id == selectedId }) },
        )
    }
}

@Composable
fun FootageStep(project: Project, state: WizardUiState, actions: WizardActions) {
    Intro(stringResource(R.string.wizard_footage_body))
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
        PolicyRow(
            title = stringResource(R.string.wizard_footage_licensed),
            body = stringResource(R.string.wizard_footage_licensed_body),
            selected = project.licensedFootageOnly,
            onClick = { actions.onLicensedOnly(true) },
        )
        PolicyRow(
            title = stringResource(R.string.wizard_footage_any),
            body = stringResource(R.string.wizard_footage_any_body),
            selected = !project.licensedFootageOnly,
            onClick = { actions.onLicensedOnly(false) },
        )
    }
    Text(
        text = if (state.importedAssets == 0) {
            stringResource(R.string.wizard_footage_none)
        } else {
            stringResource(R.string.wizard_footage_count, state.licensedAssets, state.importedAssets)
        },
        style = CreatoraTheme.typography.body,
        color = CreatoraTheme.colors.textMuted,
    )
    AiImageSection(project, state, actions)
}

/** Makes one AI picture for the project: the daily allowance and the credit cost are shown before the tap. */
@Composable
private fun AiImageSection(project: Project, state: WizardUiState, actions: WizardActions) {
    val firstSentence = splitSentences(project.script).firstOrNull().orEmpty()
    var prompt by rememberSaveable(firstSentence) { mutableStateOf(firstSentence) }
    val limit = state.plan.limits.aiImagesPerDay
    Text(stringResource(R.string.wizard_image_title), style = CreatoraTheme.typography.cardTitle, color = CreatoraTheme.colors.text)
    Field(stringResource(R.string.wizard_image_prompt), prompt, { prompt = it }, hint = stringResource(R.string.wizard_image_hint), minLines = 2)
    Text(
        stringResource(R.string.wizard_image_left, (limit - state.usage.aiImages).coerceAtLeast(0), limit),
        style = CreatoraTheme.typography.caption, color = CreatoraTheme.colors.textMuted,
    )
    CreditHint(PlanLimits.costs.image)
    SecondaryButton(
        text = stringResource(if (state.image.busy) R.string.wizard_ai_working else R.string.wizard_image_make),
        onClick = { actions.onMakeImage(prompt) },
        enabled = prompt.isNotBlank() && !state.image.busy,
        modifier = Modifier.fillMaxWidth(),
    )
    when (val outcome = state.image.outcome) {
        null -> Unit
        is ImageOutcome.Ok -> Text(stringResource(R.string.wizard_image_done), style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.success)
        is ImageOutcome.DailyLimit -> Text(stringResource(R.string.wizard_image_daily, outcome.max), style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.warning)
        ImageOutcome.CreditsUsedUp -> Text(stringResource(R.string.wizard_image_credits), style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.warning)
        ImageOutcome.SignInRequired -> Text(stringResource(R.string.ai_error_sign_in), style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.warning)
        ImageOutcome.Failed -> Text(stringResource(R.string.wizard_image_failed), style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.danger)
    }
}

@Composable
private fun PolicyRow(title: String, body: String, selected: Boolean, onClick: () -> Unit) {
    val colors = CreatoraTheme.colors
    val shape = CreatoraTheme.shapes.radius16
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) colors.accentSoft else colors.surface, shape)
            .border(CreatoraTheme.dimens.borderWidth, if (selected) colors.accent else colors.border, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(CreatoraTheme.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md),
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = colors.accent, unselectedColor = colors.textMuted),
        )
        Column {
            Text(title, style = CreatoraTheme.typography.bodyBold, color = colors.text)
            Text(body, style = CreatoraTheme.typography.caption, color = colors.textMuted)
        }
    }
}

@Composable
fun ReviewStep(project: Project, state: WizardUiState, actions: WizardActions) {
    val cast = state.cast.size
    state.score?.let { VideoScoreCard(it, actions.onFixStep) }
    ListGroup {
        InfoRow(stringResource(R.string.wizard_review_project), project.title)
        ListRowDivider()
        InfoRow(stringResource(R.string.wizard_review_format), project.format?.let { stringResource(it.titleRes()) }.orEmpty())
        ListRowDivider()
        InfoRow(stringResource(R.string.wizard_review_script), pluralStringResource(R.plurals.wizard_words, state.words, state.words))
        ListRowDivider()
        InfoRow(stringResource(R.string.wizard_review_cast), pluralStringResource(R.plurals.wizard_cast_count, cast, cast))
        ListRowDivider()
        InfoRow(
            stringResource(R.string.wizard_review_voice),
            state.voices.firstOrNull { it.id == project.narratorVoiceId }?.name ?: stringResource(R.string.wizard_voice_default),
        )
        ListRowDivider()
        InfoRow(
            stringResource(R.string.wizard_review_footage),
            stringResource(if (project.licensedFootageOnly) R.string.wizard_footage_licensed else R.string.wizard_footage_any),
        )
    }
    val estimate = state.estimate
    ListGroup {
        InfoRow(stringResource(R.string.wizard_estimate_length), durationText(estimate.spokenSeconds))
        ListRowDivider()
        InfoRow(stringResource(R.string.wizard_estimate_size), stringResource(R.string.wizard_estimate_size_value, estimate.approxSizeMb))
        ListRowDivider()
        InfoRow(stringResource(R.string.wizard_estimate_time), stringResource(R.string.wizard_estimate_time_value, estimate.approxRenderMinutes))
    }
    Intro(stringResource(R.string.wizard_estimate_note))
    AiAction(stringResource(R.string.wizard_ai_publish_text), state.words > 0, state, actions, actions.onPublishText)
    state.blockers.forEach {
        Text(stringResource(R.string.wizard_blocker_script), style = CreatoraTheme.typography.bodySemiBold, color = CreatoraTheme.colors.warning)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CreatoraTheme.spacing.lg, vertical = CreatoraTheme.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md),
    ) {
        Text(label, style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.textMuted, modifier = Modifier.weight(1f))
        Text(value, style = CreatoraTheme.typography.bodySemiBold, color = CreatoraTheme.colors.text)
    }
}

@Composable
fun GenerateStep(state: WizardUiState, panel: @Composable () -> Unit) {
    Text(stringResource(R.string.wizard_generate_title), style = CreatoraTheme.typography.sectionTitle, color = CreatoraTheme.colors.text)
    Intro(stringResource(R.string.wizard_generate_body))
    if (state.blockers.isNotEmpty()) {
        Text(stringResource(R.string.wizard_blocker_script), style = CreatoraTheme.typography.bodySemiBold, color = CreatoraTheme.colors.warning)
    }
    panel()
}

@Composable
private fun CheckId.titleRes() = when (this) {
    CheckId.TITLE -> R.string.score_title
    CheckId.HOOK -> R.string.score_hook
    CheckId.LENGTH -> R.string.score_length
    CheckId.KEYWORD -> R.string.score_keyword
    CheckId.LICENCES -> R.string.score_licences
    CheckId.THUMBNAIL -> R.string.score_thumbnail
}

@Composable
private fun Reason.text(): String = when (this) {
    is Reason.TitleLength -> stringResource(R.string.score_r_title_length, length)
    Reason.TitleCaps -> stringResource(R.string.score_r_title_caps)
    Reason.TitleOk -> stringResource(R.string.score_r_title_ok)
    is Reason.HookLong -> stringResource(R.string.score_r_hook_long, words)
    Reason.HookNoPromise -> stringResource(R.string.score_r_hook_no_promise)
    Reason.HookOk -> stringResource(R.string.score_r_hook_ok)
    Reason.NoScript -> stringResource(R.string.score_r_no_script)
    is Reason.LengthOverPlan -> stringResource(R.string.score_r_length_over, seconds, max)
    is Reason.LengthOutsideGuide -> stringResource(R.string.score_r_length_guide, seconds, min, max)
    is Reason.LengthOk -> stringResource(R.string.score_r_length_ok, seconds)
    is Reason.KeywordMissing -> stringResource(R.string.score_r_keyword_missing, keyword)
    is Reason.KeywordFound -> stringResource(R.string.score_r_keyword_found, keyword)
    Reason.KeywordUnknown -> stringResource(R.string.score_r_keyword_unknown)
    is Reason.LicencesMissing -> stringResource(R.string.score_r_licences_missing, missing)
    Reason.LicencesOk -> stringResource(R.string.score_r_licences_ok)
    Reason.ThumbnailNotChecked -> stringResource(R.string.score_r_thumbnail)
}

/** Creatora's checks of the video with the reason for each and a Fix it that opens the step with the problem. */
@Composable
fun VideoScoreCard(score: VideoScore, onFix: (Int) -> Unit) {
    val colors = CreatoraTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(CreatoraTheme.shapes.radius16).background(colors.surface)
            .border(CreatoraTheme.dimens.borderWidth, colors.border, CreatoraTheme.shapes.radius16).padding(CreatoraTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm),
    ) {
        Text(stringResource(R.string.score_title_card), style = CreatoraTheme.typography.sectionTitle, color = colors.text)
        Text(
            score.percent?.let { stringResource(R.string.score_value, it) } ?: stringResource(R.string.score_none),
            style = CreatoraTheme.typography.screenTitle, color = colors.text,
        )
        Text(stringResource(R.string.score_note), style = CreatoraTheme.typography.caption, color = colors.textMuted)
        score.checks.forEach { check ->
            val tint = when (check.status) {
                CheckStatus.PASS -> colors.success
                CheckStatus.WARN -> colors.warning
                CheckStatus.FAIL -> colors.danger
                CheckStatus.NOT_CHECKED -> colors.textMuted
            }
            val label = stringResource(
                when (check.status) {
                    CheckStatus.PASS -> R.string.score_pass
                    CheckStatus.WARN -> R.string.score_warn
                    CheckStatus.FAIL -> R.string.score_fail
                    CheckStatus.NOT_CHECKED -> R.string.score_not_checked
                },
            )
            Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.xs)) {
                Row(horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
                    Text(stringResource(check.id.titleRes()), style = CreatoraTheme.typography.bodySemiBold, color = colors.text)
                    Text(label, style = CreatoraTheme.typography.bodySemiBold, color = tint)
                }
                Text(check.reason.text(), style = CreatoraTheme.typography.caption, color = colors.textMuted)
                val fix = check.fix
                if (fix != null && check.status != CheckStatus.PASS) {
                    TextButton(onClick = { onFix(fix.ordinal) }, modifier = Modifier.heightIn(min = CreatoraTheme.dimens.minTouchTarget)) {
                        Text(stringResource(R.string.score_fix))
                    }
                }
            }
        }
    }
}
