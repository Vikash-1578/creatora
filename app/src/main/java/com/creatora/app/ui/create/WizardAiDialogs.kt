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
