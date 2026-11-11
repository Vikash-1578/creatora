package com.creatora.app.ui.saved

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.creatora.app.R
import com.creatora.app.domain.model.Idea
import com.creatora.app.domain.research.SwipeItem
import com.creatora.app.ui.components.ChoiceChip
import com.creatora.app.ui.components.EmptyState
import com.creatora.app.ui.components.PrimaryButton
import com.creatora.app.ui.components.SubScreenScaffold
import com.creatora.app.ui.library.MenuAction
import com.creatora.app.ui.library.SimpleItemRow
import com.creatora.app.ui.theme.CreatoraTheme

@Composable
fun SavedScreen(
    onBack: () -> Unit,
    onProjectStarted: (projectId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SavedViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SavedContent(
        state, onBack, viewModel::addIdea, viewModel::deleteIdea,
        { idea -> viewModel.useIdea(idea, onProjectStarted) }, viewModel::deleteSwipe, modifier,
    )
}

@Composable
fun SavedContent(
    state: SavedUiState,
    onBack: () -> Unit,
    onAddIdea: (title: String, notes: String) -> Unit,
    onDeleteIdea: (Idea) -> Unit,
    onUseIdea: (Idea) -> Unit,
    onDeleteSwipe: (SwipeItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSwipe by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    SubScreenScaffold(stringResource(R.string.saved_title), onBack, modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
            ChoiceChip(stringResource(R.string.saved_tab_ideas), !showSwipe, { showSwipe = false })
            ChoiceChip(stringResource(R.string.saved_tab_swipe), showSwipe, { showSwipe = true })
        }
        if (!showSwipe) {
            PrimaryButton(stringResource(R.string.saved_add_idea), { adding = true })
            if (state.ideas.isEmpty()) {
                EmptyState(Icons.Outlined.Lightbulb, stringResource(R.string.saved_ideas_empty_title), stringResource(R.string.saved_ideas_empty_body))
            }
            state.ideas.forEach { idea ->
                SimpleItemRow(
                    title = idea.title,
                    subtitle = idea.notes.takeIf { it.isNotBlank() },
                    actions = listOfNotNull(
                        if (idea.projectId == null) MenuAction(stringResource(R.string.saved_use_idea)) { onUseIdea(idea) } else null,
                        MenuAction(stringResource(R.string.action_delete)) { onDeleteIdea(idea) },
                    ),
                )
            }
        } else {
            if (state.swipe.isEmpty()) {
                EmptyState(Icons.Outlined.Lightbulb, stringResource(R.string.saved_swipe_empty_title), stringResource(R.string.saved_swipe_empty_body))
            }
            state.swipe.forEach { item ->
                Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.xs)) {
                    SimpleItemRow(
                        title = item.title,
                        subtitle = listOf(item.channelTitle, item.note).filter { it.isNotBlank() }.joinToString("\n"),
                        actions = listOf(
                            MenuAction(stringResource(R.string.research_open_youtube)) {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url)))
                            },
                            MenuAction(stringResource(R.string.action_delete)) { onDeleteSwipe(item) },
                        ),
                    )
                }
            }
        }
    }
    if (adding) AddIdeaDialog({ title, notes -> onAddIdea(title, notes); adding = false }, { adding = false })
}

@Composable
private fun AddIdeaDialog(onAdd: (String, String) -> Unit, onDismiss: () -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.saved_add_idea), style = CreatoraTheme.typography.sectionTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
                OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.saved_idea_title_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.saved_idea_notes_label)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(title, notes) }, enabled = title.isNotBlank()) { Text(stringResource(R.string.action_add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        containerColor = CreatoraTheme.colors.surface,
    )
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun SavedLightPreview() {
    CreatoraTheme(darkTheme = false) {
        SavedContent(SavedUiState(ideas = listOf(Idea(id = 1, title = "An idea", notes = "Some notes"))), {}, { _, _ -> }, {}, {}, {})
    }
}

@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SavedDarkPreview() {
    CreatoraTheme(darkTheme = true) { SavedContent(SavedUiState(), {}, { _, _ -> }, {}, {}, {}) }
}
