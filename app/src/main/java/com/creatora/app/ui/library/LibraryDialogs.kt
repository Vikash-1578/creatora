package com.creatora.app.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.creatora.app.R
import com.creatora.app.domain.model.Character
import com.creatora.app.domain.model.VoicePreset
import com.creatora.app.ui.components.ChoiceChip
import com.creatora.app.ui.theme.CreatoraTheme
import java.util.Locale

@Composable
fun CharacterDialog(
    initial: Character?,
    voices: List<VoicePreset>,
    onSave: (Character) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var description by rememberSaveable { mutableStateOf(initial?.description.orEmpty()) }
    var voiceId by rememberSaveable { mutableStateOf(initial?.voicePresetId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(if (initial == null) R.string.library_add_character else R.string.library_edit_character),
                style = CreatoraTheme.typography.sectionTitle,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.library_character_name_label)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.library_character_description_label)) },
                    minLines = 2,
                )
                if (voices.isNotEmpty()) {
                    Text(
                        stringResource(R.string.library_character_voice_label),
                        style = CreatoraTheme.typography.body,
                        color = CreatoraTheme.colors.textMuted,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm),
                    ) {
                        ChoiceChip(stringResource(R.string.library_character_no_voice), voiceId == null, { voiceId = null })
                        voices.forEach { voice ->
                            ChoiceChip(voice.name, voiceId == voice.id, { voiceId = voice.id })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave((initial ?: Character(name = "")).copy(name = name, description = description.trim(), voicePresetId = voiceId)) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        containerColor = CreatoraTheme.colors.surface,
    )
}

@Composable
fun VoiceDialog(
    initial: VoicePreset?,
    onSave: (VoicePreset) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var pitch by rememberSaveable { mutableStateOf(initial?.pitch ?: 1f) }
    var rate by rememberSaveable { mutableStateOf(initial?.speechRate ?: 1f) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(if (initial == null) R.string.library_add_voice else R.string.library_edit_voice),
                style = CreatoraTheme.typography.sectionTitle,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.xs)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.library_voice_name_label)) },
                    singleLine = true,
                )
                LabelledSlider(stringResource(R.string.library_voice_pitch, formatMultiplier(pitch)), pitch) { pitch = it }
                LabelledSlider(stringResource(R.string.library_voice_speed, formatMultiplier(rate)), rate) { rate = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave((initial ?: VoicePreset(name = "")).copy(name = name, pitch = pitch, speechRate = rate)) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        containerColor = CreatoraTheme.colors.surface,
    )
}

@Composable
private fun LabelledSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Text(label, style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.text)
    Slider(
        value = value,
        onValueChange = onChange,
        valueRange = VOICE_RANGE,
        colors = SliderDefaults.colors(
            thumbColor = CreatoraTheme.colors.accent,
            activeTrackColor = CreatoraTheme.colors.accent,
            inactiveTrackColor = CreatoraTheme.colors.border,
        ),
    )
}

/** Pitch and speed range supported by Android TextToSpeech presets. */
val VOICE_RANGE = 0.5f..2f

fun formatMultiplier(value: Float): String = String.format(Locale.getDefault(), "%.1f×", value)
