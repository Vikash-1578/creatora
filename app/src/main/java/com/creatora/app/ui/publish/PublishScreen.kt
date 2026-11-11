package com.creatora.app.ui.publish

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.creatora.app.R
import com.creatora.app.domain.model.Platform
import com.creatora.app.domain.model.Project
import com.creatora.app.domain.model.ScheduledPost
import com.creatora.app.domain.publish.ChecklistEntry
import com.creatora.app.domain.publish.ChecklistItem
import com.creatora.app.domain.publish.MAX_TITLE_LENGTH
import com.creatora.app.domain.publish.postText
import com.creatora.app.domain.publish.publishChecklist
import com.creatora.app.ui.ai.AiErrors
import com.creatora.app.ui.components.ChoiceChip
import com.creatora.app.ui.components.ListGroup
import com.creatora.app.ui.components.ListRowDivider
import com.creatora.app.ui.components.PrimaryButton
import com.creatora.app.ui.components.SecondaryButton
import com.creatora.app.ui.components.SectionTitle
import com.creatora.app.ui.components.SubScreenScaffold
import com.creatora.app.ui.theme.CreatoraTheme
import java.io.File

@Composable
fun PublishScreen(
    onBack: () -> Unit,
    onOpenThumbnail: (projectId: Long) -> Unit,
    onOpenKeys: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PublishViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var scheduling by rememberSaveable { mutableStateOf(false) }
    PublishContent(
        state = state,
        onBack = onBack,
        onTitle = viewModel::setTitle,
        onDescription = viewModel::setDescription,
        onSuggest = viewModel::suggest,
        onDismissSuggestion = viewModel::dismissSuggestion,
        onAiContent = viewModel::setAiContent,
        onMadeForKids = viewModel::setMadeForKids,
        onThumbnail = { state.project?.let { onOpenThumbnail(it.id) } },
        onSchedule = { scheduling = true },
        onRemovePost = viewModel::removePost,
        onOpenKeys = onOpenKeys,
        modifier = modifier,
    )
    if (scheduling) {
        ScheduleDialog(
            projects = null,
            onConfirm = { _, platform, at -> viewModel.schedule(platform, at); scheduling = false },
            onDismiss = { scheduling = false },
        )
    }
}

@Composable
fun PublishContent(
    state: PublishUiState,
    onBack: () -> Unit,
    onTitle: (String) -> Unit,
    onDescription: (String) -> Unit,
    onSuggest: () -> Unit,
    onDismissSuggestion: () -> Unit,
    onAiContent: (Boolean) -> Unit,
    onMadeForKids: (Boolean) -> Unit,
    onThumbnail: () -> Unit,
    onSchedule: () -> Unit,
    onRemovePost: (ScheduledPost) -> Unit,
    onOpenKeys: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = CreatoraTheme.spacing
    val colors = CreatoraTheme.colors
    val project = state.project
    val context = LocalContext.current
    SubScreenScaffold(stringResource(R.string.publish_title), onBack, modifier) {
        if (project == null) return@SubScreenScaffold
        // The text fields keep their own copy so typing never fights the saved value.
        var title by rememberSaveable(project.id) { mutableStateOf(project.publishTitle) }
        var description by rememberSaveable(project.id) { mutableStateOf(project.publishDescription) }

        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it; onTitle(it) },
                label = { Text(stringResource(R.string.publish_title_label)) },
                supportingText = { Text(stringResource(R.string.publish_title_count, title.length, MAX_TITLE_LENGTH)) },
                isError = title.length > MAX_TITLE_LENGTH,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it; onDescription(it) },
                label = { Text(stringResource(R.string.publish_description_label)) },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            SecondaryButton(
                text = stringResource(if (state.ai.busy) R.string.wizard_ai_working else R.string.publish_suggest),
                onClick = onSuggest,
                enabled = !state.ai.busy && project.script.isNotBlank(),
            )
            if (state.ai.busy) CircularProgressIndicator(color = colors.accent)
            state.ai.errors?.let { AiErrors(it, onOpenKeys) }
            state.ai.suggestion?.let { suggestion ->
                Text(stringResource(R.string.publish_suggestions), style = CreatoraTheme.typography.cardTitle, color = colors.text)
                suggestion.titles.forEach { suggested ->
                    ChoiceChip(suggested, selected = suggested == title, onClick = { title = suggested.take(MAX_TITLE_LENGTH); onTitle(title) })
                }
                if (suggestion.description.isNotBlank()) {
                    SecondaryButton(
                        text = stringResource(R.string.publish_use_description),
                        onClick = {
                            description = listOf(suggestion.description, suggestion.hashtags.joinToString(" ")).filter { it.isNotBlank() }.joinToString("\n\n")
                            onDescription(description)
                        },
                    )
                }
                SecondaryButton(stringResource(R.string.action_done), onDismissSuggestion)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            SectionTitle(stringResource(R.string.publish_checklist))
            ListGroup {
                state.checklist.forEachIndexed { index, entry ->
                    if (index > 0) ListRowDivider()
                    ChecklistRow(entry, project, onAiContent, onMadeForKids, onThumbnail)
                }
            }
            val open = state.checklist.count { !it.done }
            Text(
                text = if (open == 0) stringResource(R.string.publish_all_done) else stringResource(R.string.publish_open_checks, open),
                style = CreatoraTheme.typography.bodySemiBold,
                color = if (open == 0) colors.success else colors.warning,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            SectionTitle(stringResource(R.string.publish_share))
            Text(stringResource(R.string.publish_share_note), style = CreatoraTheme.typography.body, color = colors.textMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                PrimaryButton(
                    text = stringResource(R.string.publish_share_youtube),
                    onClick = { shareVideo(context, project, "com.google.android.youtube") },
                    enabled = project.renderUri != null,
                )
                PrimaryButton(
                    text = stringResource(R.string.publish_share_instagram),
                    onClick = { shareVideo(context, project, "com.instagram.android") },
                    enabled = project.renderUri != null,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            SectionTitle(stringResource(R.string.publish_reminders))
            state.posts.forEach { post -> PostRow(post, onRemovePost) }
            SecondaryButton(stringResource(R.string.publish_remind), onSchedule)
        }
    }
}

@Composable
private fun ChecklistRow(
    entry: ChecklistEntry,
    project: Project,
    onAiContent: (Boolean) -> Unit,
    onMadeForKids: (Boolean) -> Unit,
    onThumbnail: () -> Unit,
) {
    val colors = CreatoraTheme.colors
    Column(Modifier.padding(CreatoraTheme.spacing.lg), verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md)) {
            Icon(
                imageVector = if (entry.done) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = stringResource(if (entry.done) R.string.publish_check_done else R.string.publish_check_open),
                tint = if (entry.done) colors.success else colors.textMuted,
                modifier = Modifier.size(CreatoraTheme.dimens.icon),
            )
            Column(Modifier.weight(1f)) {
                Text(stringResource(entry.item.labelRes()), style = CreatoraTheme.typography.bodySemiBold, color = colors.text)
                Text(stringResource(entry.item.hintRes()), style = CreatoraTheme.typography.caption, color = colors.textMuted)
            }
        }
        when (entry.item) {
            ChecklistItem.AI_LABEL -> YesNo(project.aiContent, onAiContent)
            ChecklistItem.MADE_FOR_KIDS -> YesNo(project.madeForKids, onMadeForKids)
            ChecklistItem.THUMBNAIL -> if (!entry.done) SecondaryButton(stringResource(R.string.thumbnail_make), onThumbnail)
            else -> Unit
        }
    }
}

@Composable
private fun YesNo(value: Boolean?, onChange: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
        ChoiceChip(stringResource(R.string.publish_yes), value == true, { onChange(true) })
        ChoiceChip(stringResource(R.string.publish_no), value == false, { onChange(false) })
    }
}

@Composable
private fun PostRow(post: ScheduledPost, onRemove: (ScheduledPost) -> Unit) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.publish_post_row, stringResource(post.platform.labelRes()), DateUtils.formatDateTime(context, post.scheduledAt, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL)),
            style = CreatoraTheme.typography.body,
            color = CreatoraTheme.colors.text,
            modifier = Modifier.weight(1f),
        )
        SecondaryButton(stringResource(R.string.action_remove), { onRemove(post) })
    }
}

private fun ChecklistItem.labelRes() = when (this) {
    ChecklistItem.RENDERED -> R.string.publish_check_rendered
    ChecklistItem.TITLE -> R.string.publish_check_title
    ChecklistItem.THUMBNAIL -> R.string.publish_check_thumbnail
    ChecklistItem.CAPTIONS -> R.string.publish_check_captions
    ChecklistItem.AI_LABEL -> R.string.publish_check_ai
    ChecklistItem.MADE_FOR_KIDS -> R.string.publish_check_kids
}

private fun ChecklistItem.hintRes() = when (this) {
    ChecklistItem.RENDERED -> R.string.publish_hint_rendered
    ChecklistItem.TITLE -> R.string.publish_hint_title
    ChecklistItem.THUMBNAIL -> R.string.publish_hint_thumbnail
    ChecklistItem.CAPTIONS -> R.string.publish_hint_captions
    ChecklistItem.AI_LABEL -> R.string.publish_hint_ai
    ChecklistItem.MADE_FOR_KIDS -> R.string.publish_hint_kids
}

/**
 * Opens the app (YouTube or Instagram) with the video attached. The title and description are copied to
 * the clipboard first because those apps ignore text sent with a video. Falls back to the share sheet.
 */
private fun shareVideo(context: Context, project: Project, packageName: String) {
    val file = File(Uri.parse(project.renderUri ?: return).path ?: return)
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
        .setPrimaryClip(ClipData.newPlainText(project.publishTitle, postText(project)))
    val video = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("video/mp4")
        .putExtra(Intent.EXTRA_STREAM, video)
        .putExtra(Intent.EXTRA_TEXT, postText(project))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    val installed = context.packageManager.getLaunchIntentForPackage(packageName) != null
    context.startActivity(if (installed) send.setPackage(packageName) else Intent.createChooser(send, null))
}

private val PreviewProject = Project(id = 1, title = "Preview", publishTitle = "A title", renderUri = "file:///r.mp4", captionsIncluded = true)

@Composable
private fun PublishPreview(darkTheme: Boolean) {
    CreatoraTheme(darkTheme = darkTheme) {
        PublishContent(
            state = PublishUiState(project = PreviewProject, checklist = publishChecklist(PreviewProject)),
            onBack = {}, onTitle = {}, onDescription = {}, onSuggest = {},
            onDismissSuggestion = {}, onAiContent = {}, onMadeForKids = {}, onThumbnail = {}, onSchedule = {},
            onRemovePost = {}, onOpenKeys = {},
        )
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun PublishLightPreview() = PublishPreview(false)

@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PublishDarkPreview() = PublishPreview(true)
