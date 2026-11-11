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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.creatora.app.R
import com.creatora.app.domain.model.Project
import com.creatora.app.domain.model.VideoFormat
import com.creatora.app.domain.model.VoicePreset
import com.creatora.app.domain.model.readingSeconds
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
fun BriefStep(project: Project, actions: WizardActions) {
    Field(stringResource(R.string.wizard_title_label), project.title, actions.onTitle)
    Field(
        label = stringResource(R.string.wizard_brief_label),
        value = project.brief,
        onChange = actions.onBrief,
        hint = stringResource(R.string.wizard_brief_hint),
        minLines = 4,
    )
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

@Composable
fun ScriptStep(project: Project, state: WizardUiState, actions: WizardActions) {
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
    Field(
        stringResource(R.string.wizard_pronunciations), project.pronunciations, actions.onPronunciations,
        hint = stringResource(R.string.wizard_pronunciations_hint), minLines = 3,
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
fun ReviewStep(project: Project, state: WizardUiState) {
    val cast = state.cast.size
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
fun GenerateStep(state: WizardUiState) {
    Text(stringResource(R.string.wizard_generate_title), style = CreatoraTheme.typography.sectionTitle, color = CreatoraTheme.colors.text)
    Intro(stringResource(R.string.wizard_generate_body))
    if (state.blockers.isNotEmpty()) {
        Text(stringResource(R.string.wizard_blocker_script), style = CreatoraTheme.typography.bodySemiBold, color = CreatoraTheme.colors.warning)
    }
}
