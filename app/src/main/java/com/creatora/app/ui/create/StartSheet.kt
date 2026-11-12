package com.creatora.app.ui.create

import android.content.res.Configuration
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import com.creatora.app.R
import com.creatora.app.domain.create.FormatAccess
import com.creatora.app.domain.home.CreatorIdea
import com.creatora.app.domain.model.CreateMode
import com.creatora.app.domain.model.StartSource
import com.creatora.app.domain.model.VideoFormat
import com.creatora.app.ui.ai.message
import com.creatora.app.ui.components.ChoiceChip
import com.creatora.app.ui.theme.CreatoraTheme

/** What a locked or not-yet-built format needs, with an Upgrade button for Pro formats. */
@Composable
fun LockedFormatDialog(format: VideoFormat, access: FormatAccess, onUpgrade: () -> Unit, onDismiss: () -> Unit) {
    val pro = access == FormatAccess.PRO
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(format.titleRes()), style = CreatoraTheme.typography.sectionTitle) },
        text = { Text(stringResource(if (pro) R.string.create_locked_pro_body else R.string.create_locked_later_body), style = CreatoraTheme.typography.body) },
        confirmButton = {
            if (pro) TextButton(onClick = onUpgrade) { Text(stringResource(R.string.create_upgrade)) } else TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
        dismissButton = { if (pro) TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
        containerColor = CreatoraTheme.colors.surface,
    )
}

/**
 * How to start (an idea, an own topic or a link) and how to make it (step by step, or Auto mode where the AI does every
 * step). A link is only used to learn its idea; the result says when it could not be read.
 */
@Composable
fun StartSheet(
    format: VideoFormat,
    savedIdeas: List<CreatorIdea>,
    result: StartResult,
    onStart: (StartSource, String, CreatorIdea?, CreateMode) -> Unit,
    onDismiss: () -> Unit,
) {
    var source by rememberSaveable { mutableStateOf(StartSource.TOPIC) }
    var text by rememberSaveable { mutableStateOf("") }
    var ideaId by rememberSaveable { mutableStateOf<String?>(null) }
    var mode by rememberSaveable { mutableStateOf(CreateMode.STEP_BY_STEP) }
    val idea = savedIdeas.firstOrNull { it.id == ideaId }
    val working = result is StartResult.Working
    val ready = when (source) {
        StartSource.IDEA -> idea != null
        else -> text.isNotBlank()
    }
    val spacing = CreatoraTheme.spacing
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.start_title, stringResource(format.titleRes())), style = CreatoraTheme.typography.sectionTitle) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                Text(stringResource(R.string.start_how), style = CreatoraTheme.typography.cardTitle, color = CreatoraTheme.colors.text)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    ChoiceChip(stringResource(R.string.start_source_topic), source == StartSource.TOPIC, { source = StartSource.TOPIC })
                    ChoiceChip(stringResource(R.string.start_source_idea), source == StartSource.IDEA, { source = StartSource.IDEA })
                    ChoiceChip(stringResource(R.string.start_source_link), source == StartSource.LINK, { source = StartSource.LINK })
                }
                when (source) {
                    StartSource.TOPIC -> OutlinedTextField(
                        value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.start_topic_label)) },
                    )
                    StartSource.LINK -> {
                        OutlinedTextField(
                            value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.start_link_label)) },
                        )
                        Text(stringResource(R.string.start_link_note), style = CreatoraTheme.typography.caption, color = CreatoraTheme.colors.textMuted)
                    }
                    StartSource.IDEA -> {
                        if (savedIdeas.isEmpty()) Text(stringResource(R.string.start_no_ideas), style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.textMuted)
                        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                            savedIdeas.take(MAX_IDEAS).forEach { item ->
                                OptionRow(item.title, item.reason, ideaId == item.id) { ideaId = item.id }
                            }
                        }
                    }
                }
                Text(stringResource(R.string.start_mode), style = CreatoraTheme.typography.cardTitle, color = CreatoraTheme.colors.text)
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    OptionRow(stringResource(R.string.start_mode_steps), stringResource(R.string.start_mode_steps_hint), mode == CreateMode.STEP_BY_STEP) { mode = CreateMode.STEP_BY_STEP }
                    OptionRow(stringResource(R.string.start_mode_auto), stringResource(R.string.start_mode_auto_hint), mode == CreateMode.AUTO) { mode = CreateMode.AUTO }
                }
                when (result) {
                    StartResult.LinkInvalid -> Problem(stringResource(R.string.start_error_link_invalid))
                    StartResult.LinkUnreadable -> Problem(stringResource(R.string.start_error_link_unreadable))
                    StartResult.TooClose -> Problem(stringResource(R.string.start_error_too_close))
                    is StartResult.AiFailed -> if (result.errors.isEmpty()) Problem(stringResource(R.string.start_error_ai_unreadable)) else result.errors.forEach { Problem(it.message()) }
                    StartResult.Working -> Text(stringResource(R.string.start_working), style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.textMuted)
                    else -> Unit
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onStart(source, text, idea, mode) }, enabled = ready && !working) { Text(stringResource(R.string.start_go)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        containerColor = CreatoraTheme.colors.surface,
    )
}

private const val MAX_IDEAS = 8

@Composable
private fun Problem(text: String) = Text(text, style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.danger)

@Composable
private fun OptionRow(title: String, hint: String, selected: Boolean, onClick: () -> Unit) {
    val colors = CreatoraTheme.colors
    val shape = CreatoraTheme.shapes.radius12
    Column(
        Modifier.fillMaxWidth().heightIn(min = CreatoraTheme.dimens.minTouchTarget).clip(shape)
            .border(CreatoraTheme.dimens.borderWidth, if (selected) colors.accent else colors.border, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(CreatoraTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.xs),
    ) {
        Text(title, style = CreatoraTheme.typography.bodySemiBold, color = colors.text)
        if (hint.isNotBlank()) Text(hint, style = CreatoraTheme.typography.caption, color = colors.textMuted)
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun StartSheetLightPreview() {
    CreatoraTheme(darkTheme = false) { StartSheet(VideoFormat.SHORTS_REELS, listOf(CreatorIdea("1", "Dal in 10 minutes", "Your best video")), StartResult.Idle, { _, _, _, _ -> }, {}) }
}

@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StartSheetDarkPreview() {
    CreatoraTheme(darkTheme = true) { StartSheet(VideoFormat.SHORTS_REELS, emptyList(), StartResult.LinkUnreadable, { _, _, _, _ -> }, {}) }
}
