package com.creatora.app.ui.create

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.creatora.app.R
import com.creatora.app.domain.model.PublishText
import com.creatora.app.ui.ai.message
import com.creatora.app.domain.model.ScriptDraft
import com.creatora.app.ui.theme.CreatoraTheme

/** Shows the script the AI wrote. Nothing changes until the user replaces their script with it. */
@Composable
fun DraftDialog(draft: ScriptDraft, onApply: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wizard_ai_draft_title), style = CreatoraTheme.typography.sectionTitle) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md)) {
                Labelled(stringResource(R.string.wizard_script_hook), draft.hook)
                Labelled(stringResource(R.string.wizard_script_structure), draft.structure)
                Labelled(stringResource(R.string.wizard_script_cta), draft.callToAction)
            }
        },
        confirmButton = { TextButton(onClick = onApply) { Text(stringResource(R.string.wizard_ai_draft_replace)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        containerColor = CreatoraTheme.colors.surface,
    )
}

/** Titles, description, hashtags and chapters, each with a Copy button. */
@Composable
fun PublishTextDialog(text: PublishText, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wizard_ai_publish_title), style = CreatoraTheme.typography.sectionTitle) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md)) {
                CopyBlock(context, stringResource(R.string.wizard_ai_titles), text.titles.joinToString("\n"))
                CopyBlock(context, stringResource(R.string.wizard_ai_description), text.description)
                CopyBlock(context, stringResource(R.string.wizard_ai_hashtags), text.hashtags.joinToString(" "))
                CopyBlock(context, stringResource(R.string.wizard_ai_chapters), text.chapters.joinToString("\n"))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) } },
        containerColor = CreatoraTheme.colors.surface,
    )
}

@Composable
private fun Labelled(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.xs)) {
        Text(label, style = CreatoraTheme.typography.bodySemiBold, color = CreatoraTheme.colors.text)
        Text(value.ifBlank { "—" }, style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.textMuted)
    }
}

@Composable
private fun CopyBlock(context: Context, label: String, value: String) {
    if (value.isBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = CreatoraTheme.typography.bodySemiBold, color = CreatoraTheme.colors.text, modifier = Modifier.weight(1f))
            TextButton(onClick = { copyToClipboard(context, label, value) }) { Text(stringResource(R.string.action_copy)) }
        }
        Text(value, style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.textMuted)
    }
}

private fun copyToClipboard(context: Context, label: String, value: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, value))
}

private fun AutoStep.labelRes() = when (this) {
    AutoStep.BRIEF -> R.string.auto_step_brief
    AutoStep.SCRIPT -> R.string.auto_step_script
    AutoStep.IMAGES -> R.string.auto_step_images
}

/**
 * Auto mode: first the plan and the total cost to confirm, then the progress of each step, then what was done. Nothing is
 * published; the result opens on the Generate step, where any step can be reopened and edited.
 */
@Composable
fun AutoDialog(run: AutoRun, actions: WizardActions) {
    AlertDialog(
        onDismissRequest = { if (!run.started) actions.onCancelAuto() },
        title = { Text(stringResource(R.string.auto_title), style = CreatoraTheme.typography.sectionTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
                if (!run.started) {
                    Text(stringResource(R.string.auto_body), style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.text)
                    Text(
                        stringResource(R.string.auto_cost, run.plan.credits, run.plan.images),
                        style = CreatoraTheme.typography.bodySemiBold, color = CreatoraTheme.colors.accentText,
                    )
                    if (run.plan.images == 0) Text(stringResource(R.string.auto_no_images), style = CreatoraTheme.typography.caption, color = CreatoraTheme.colors.warning)
                } else {
                    AutoStep.entries.forEach { step ->
                        val status = run.steps[step] ?: AutoStatus.Waiting
                        val note = when (status) {
                            AutoStatus.Waiting -> stringResource(R.string.auto_waiting)
                            AutoStatus.Running -> stringResource(R.string.auto_running)
                            AutoStatus.Done -> stringResource(R.string.auto_done)
                            is AutoStatus.Stopped -> stringResource(R.string.auto_stopped)
                        }
                        Text("${stringResource(step.labelRes())}: $note", style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.text)
                        (status as? AutoStatus.Stopped)?.errors?.forEach { Text(it.message(), style = CreatoraTheme.typography.caption, color = CreatoraTheme.colors.danger) }
                    }
                    if (run.finished) Text(stringResource(R.string.auto_review_note), style = CreatoraTheme.typography.caption, color = CreatoraTheme.colors.textMuted)
                }
            }
        },
        confirmButton = {
            when {
                !run.started -> TextButton(onClick = actions.onRunAuto) { Text(stringResource(R.string.auto_start)) }
                run.finished -> TextButton(onClick = actions.onFinishAuto) { Text(stringResource(R.string.auto_open)) }
            }
        },
        dismissButton = { if (!run.started) TextButton(onClick = actions.onCancelAuto) { Text(stringResource(R.string.auto_manual)) } },
        containerColor = CreatoraTheme.colors.surface,
    )
}
