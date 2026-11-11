package com.creatora.app.ui.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.creatora.app.R
import com.creatora.app.ui.ai.AiErrors
import com.creatora.app.ui.components.PrimaryButton
import com.creatora.app.ui.theme.CreatoraTheme

/** Asks the AI for video ideas about a topic; tapping Use starts a project from an idea. */
@Composable
fun IdeasDialog(
    state: IdeasUiState,
    onSuggest: (topic: String) -> Unit,
    onUse: (idea: String) -> Unit,
    onOpenProviders: () -> Unit,
    onDismiss: () -> Unit,
) {
    var topic by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_ideas_title), style = CreatoraTheme.typography.sectionTitle) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text(stringResource(R.string.create_ideas_topic_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                PrimaryButton(
                    text = stringResource(R.string.create_ideas_suggest),
                    onClick = { onSuggest(topic) },
                    enabled = !state.loading,
                )
                if (state.loading) CircularProgressIndicator(color = CreatoraTheme.colors.accent)
                state.errors?.let { AiErrors(it, onOpenProviders) }
                state.ideas.forEach { idea ->
                    Row(Modifier.padding(top = CreatoraTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                        Text(idea, style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.text, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onUse(idea) }) { Text(stringResource(R.string.create_ideas_use)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) } },
        containerColor = CreatoraTheme.colors.surface,
    )
}
