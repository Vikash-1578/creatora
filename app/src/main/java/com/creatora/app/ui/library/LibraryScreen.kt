package com.creatora.app.ui.library

import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.lazy.LazyListScope
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.creatora.app.R
import com.creatora.app.domain.model.Asset
import com.creatora.app.domain.model.Character
import com.creatora.app.domain.model.Project
import com.creatora.app.domain.model.VoicePreset
import com.creatora.app.ui.components.ChoiceChip
import com.creatora.app.ui.components.EmptyState
import com.creatora.app.ui.components.HeaderIconButton
import com.creatora.app.ui.components.ListGroup
import com.creatora.app.ui.components.ListRowDivider
import com.creatora.app.ui.components.NavigationListRow
import com.creatora.app.ui.components.PrimaryButton
import com.creatora.app.ui.components.ScreenHeader
import com.creatora.app.ui.components.tabBottomSpace
import com.creatora.app.ui.components.TextInputDialog
import com.creatora.app.ui.theme.CreatoraTheme

/** Dialogs the Library can show. */
private sealed interface LibraryDialog {
    data object NewProject : LibraryDialog
    data class Rename(val project: Project) : LibraryDialog
    data class Duplicate(val project: Project) : LibraryDialog
    data class SaveTemplate(val project: Project) : LibraryDialog
    data class UseTemplate(val template: Project) : LibraryDialog
    data class EditCharacter(val character: Character?) : LibraryDialog
    data class EditVoice(val voice: VoicePreset?) : LibraryDialog
}

@Composable
fun LibraryScreen(
    onOpenBrandKit: () -> Unit,
    onOpenLicences: (assetId: Long?) -> Unit,
    onOpenProject: (projectId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        viewModel.importFiles(uris.map { it.toString() })
    }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.importFiles(uris.map { it.toString() })
    }

    state.pendingDelete?.let { pending ->
        val message = stringResource(R.string.library_deleted, pending.name)
        val undo = stringResource(R.string.action_undo)
        LaunchedEffect(pending) {
            if (snackbar.showSnackbar(message, undo, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
                viewModel.undoDelete()
            }
        }
    }
    state.importResult?.let { result ->
        val done = if (result.imported > 0) pluralStringResource(R.plurals.library_import_done, result.imported, result.imported) else null
        val skipped = if (result.skipped > 0) pluralStringResource(R.plurals.library_import_skipped, result.skipped, result.skipped) else null
        val message = listOfNotNull(done, skipped).joinToString(". ")
        LaunchedEffect(result) {
            snackbar.showSnackbar(message)
            viewModel.importResultShown()
        }
    }

    LibraryContent(
        state = state,
        snackbar = snackbar,
        onFilter = viewModel::setFilter,
        onQuery = viewModel::setQuery,
        onCreateProject = viewModel::createProject,
        onRenameProject = viewModel::renameProject,
        onCopyProject = viewModel::copyProject,
        onSaveCharacter = viewModel::saveCharacter,
        onSaveVoice = viewModel::saveVoice,
        onDelete = viewModel::delete,
        onAddMedia = {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
        },
        onAddAudio = { pickAudio.launch(arrayOf("audio/*")) },
        onOpenBrandKit = onOpenBrandKit,
        onOpenLicences = onOpenLicences,
        onOpenProject = onOpenProject,
        modifier = modifier,
    )
}

@Composable
fun LibraryContent(
    state: LibraryUiState,
    snackbar: SnackbarHostState,
    onFilter: (LibraryFilter) -> Unit,
    onQuery: (String) -> Unit,
    onCreateProject: (String) -> Unit,
    onRenameProject: (Project, String) -> Unit,
    onCopyProject: (Project, String, Boolean) -> Unit,
    onSaveCharacter: (Character) -> Unit,
    onSaveVoice: (VoicePreset) -> Unit,
    onDelete: (DeleteKind, Long, String) -> Unit,
    onAddMedia: () -> Unit,
    onAddAudio: () -> Unit,
    onOpenBrandKit: () -> Unit,
    onOpenLicences: (Long?) -> Unit,
    onOpenProject: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = CreatoraTheme.spacing
    var dialog by remember { mutableStateOf<LibraryDialog?>(null) }
    var searching by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CreatoraTheme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = tabBottomSpace()),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            item {
                ScreenHeader(
                    title = stringResource(R.string.tab_library),
                    subtitle = stringResource(R.string.library_subtitle),
                ) {
                    HeaderIconButton(
                        icon = if (searching) Icons.Outlined.Close else Icons.Outlined.Search,
                        contentDescription = stringResource(if (searching) R.string.action_close_search else R.string.action_search),
                        onClick = {
                            searching = !searching
                            if (!searching) onQuery("")
                        },
                    )
                }
            }
            if (searching) {
                item {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = onQuery,
                        label = { Text(stringResource(R.string.library_search_label)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.lg),
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    LibraryFilter.entries.forEach { filter ->
                        ChoiceChip(
                            label = stringResource(filter.labelRes()),
                            selected = filter == state.filter,
                            onClick = { onFilter(filter) },
                        )
                    }
                }
            }
            when (state.filter) {
                LibraryFilter.PROJECTS -> projectsSection(
                    state = state,
                    onNew = { dialog = LibraryDialog.NewProject },
                    onDialog = { dialog = it },
                    onDelete = onDelete,
                    onOpenBrandKit = onOpenBrandKit,
                    onOpenLicences = { onOpenLicences(null) },
                    onOpenProject = onOpenProject,
                )
                LibraryFilter.ASSETS -> assetsSection(state, onAddMedia, onAddAudio, onOpenLicences, onDelete)
                LibraryFilter.CAST -> castSection(state, onDialog = { dialog = it }, onDelete = onDelete)
                LibraryFilter.VOICES -> voicesSection(state, onDialog = { dialog = it }, onDelete = onDelete)
                LibraryFilter.TEMPLATES -> templatesSection(state, onDialog = { dialog = it }, onDelete = onDelete)
            }
        }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }

    when (val current = dialog) {
        null -> Unit
        LibraryDialog.NewProject -> TextInputDialog(
            title = stringResource(R.string.library_new_project_dialog_title),
            label = stringResource(R.string.library_project_title_label),
            confirmLabel = stringResource(R.string.action_create),
            onConfirm = { onCreateProject(it); dialog = null },
            onDismiss = { dialog = null },
        )
        is LibraryDialog.Rename -> TextInputDialog(
            title = stringResource(R.string.library_rename_project_title),
            label = stringResource(R.string.library_project_title_label),
            confirmLabel = stringResource(R.string.action_rename),
            initialValue = current.project.title,
            onConfirm = { onRenameProject(current.project, it); dialog = null },
            onDismiss = { dialog = null },
        )
        is LibraryDialog.Duplicate -> TextInputDialog(
            title = stringResource(R.string.library_duplicate_project_title),
            label = stringResource(R.string.library_project_title_label),
            confirmLabel = stringResource(R.string.action_duplicate),
            initialValue = stringResource(R.string.library_copy_suffix, current.project.title),
            onConfirm = { onCopyProject(current.project, it, false); dialog = null },
            onDismiss = { dialog = null },
        )
        is LibraryDialog.SaveTemplate -> TextInputDialog(
            title = stringResource(R.string.library_save_template_title),
            label = stringResource(R.string.library_template_name_label),
            confirmLabel = stringResource(R.string.action_save),
            initialValue = current.project.title,
            onConfirm = { onCopyProject(current.project, it, true); dialog = null },
            onDismiss = { dialog = null },
        )
        is LibraryDialog.UseTemplate -> TextInputDialog(
            title = stringResource(R.string.library_use_template_title),
            label = stringResource(R.string.library_project_title_label),
            confirmLabel = stringResource(R.string.action_create),
            initialValue = current.template.title,
            onConfirm = { onCopyProject(current.template, it, false); dialog = null },
            onDismiss = { dialog = null },
        )
        is LibraryDialog.EditCharacter -> CharacterDialog(
            initial = current.character,
            voices = state.voices,
            onSave = { onSaveCharacter(it); dialog = null },
            onDismiss = { dialog = null },
        )
        is LibraryDialog.EditVoice -> VoiceDialog(
            initial = current.voice,
            onSave = { onSaveVoice(it); dialog = null },
            onDismiss = { dialog = null },
        )
    }
}

private fun LibraryFilter.labelRes() = when (this) {
    LibraryFilter.PROJECTS -> R.string.library_filter_projects
    LibraryFilter.ASSETS -> R.string.library_filter_assets
    LibraryFilter.CAST -> R.string.library_filter_cast
    LibraryFilter.VOICES -> R.string.library_filter_voices
    LibraryFilter.TEMPLATES -> R.string.library_filter_templates
}

/** Rows of [perRow] items; the last row is padded with empty space so cells keep the same width. */
private fun <T> LazyListScope.gridRows(
    cells: List<T>,
    perRow: Int = 2,
    cell: @Composable (T, Modifier) -> Unit,
) {
    items(cells.chunked(perRow)) { row ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CreatoraTheme.spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md),
        ) {
            row.forEach { cell(it, Modifier.weight(1f)) }
            repeat(perRow - row.size) { Box(Modifier.weight(1f)) }
        }
    }
}

private fun LazyListScope.projectsSection(
    state: LibraryUiState,
    onNew: () -> Unit,
    onDialog: (LibraryDialog) -> Unit,
    onDelete: (DeleteKind, Long, String) -> Unit,
    onOpenBrandKit: () -> Unit,
    onOpenLicences: () -> Unit,
    onOpenProject: (Long) -> Unit,
) {
    // Project cards followed by the "New project" tile, two per row. Hidden while searching.
    val searching = state.query.isNotBlank()
    val cells: List<ProjectItem?> = if (searching) state.projects else state.projects + null
    if (searching && cells.isEmpty()) {
        item { NoResults() }
    } else {
        gridRows(cells) { cell, modifier ->
            if (cell == null) {
                NewProjectTile(onClick = onNew, modifier = modifier)
            } else {
                val project = cell.project
                ProjectCard(
                    item = cell,
                    modifier = modifier,
                    actions = listOfNotNull(
                        project.format?.let { MenuAction(stringResource(R.string.library_open)) { onOpenProject(project.id) } },
                        MenuAction(stringResource(R.string.action_rename)) { onDialog(LibraryDialog.Rename(project)) },
                        MenuAction(stringResource(R.string.action_duplicate)) { onDialog(LibraryDialog.Duplicate(project)) },
                        MenuAction(stringResource(R.string.library_save_template)) { onDialog(LibraryDialog.SaveTemplate(project)) },
                        MenuAction(stringResource(R.string.action_delete)) { onDelete(DeleteKind.PROJECT, project.id, project.title) },
                    ),
                )
            }
        }
    }
    item {
        ListGroup(modifier = Modifier.padding(horizontal = CreatoraTheme.spacing.lg)) {
            NavigationListRow(
                title = stringResource(R.string.library_row_brand_kit),
                value = stringResource(R.string.library_row_brand_kit_value),
                onClick = onOpenBrandKit,
            )
            ListRowDivider()
            NavigationListRow(
                title = stringResource(R.string.library_row_licences),
                value = stringResource(R.string.library_row_licences_value),
                onClick = onOpenLicences,
            )
        }
    }
}

private fun LazyListScope.assetsSection(
    state: LibraryUiState,
    onAddMedia: () -> Unit,
    onAddAudio: () -> Unit,
    onOpenLicences: (Long?) -> Unit,
    onDelete: (DeleteKind, Long, String) -> Unit,
) {
    item {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CreatoraTheme.spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm),
        ) {
            PrimaryButton(stringResource(R.string.library_add_media), onAddMedia, Modifier.weight(1f), Icons.Outlined.Image)
            PrimaryButton(stringResource(R.string.library_add_audio), onAddAudio, Modifier.weight(1f), Icons.Outlined.MusicNote)
        }
    }
    if (state.assets.isEmpty()) {
        item {
            if (state.query.isNotBlank()) NoResults() else EmptyState(
                icon = Icons.Outlined.Folder,
                title = stringResource(R.string.library_assets_empty_title),
                body = stringResource(R.string.library_assets_empty_body),
            )
        }
    } else {
        gridRows(state.assets) { asset: Asset, modifier ->
            AssetCard(
                asset = asset,
                hasLicence = asset.id in state.licensedAssetIds,
                modifier = modifier,
                actions = listOf(
                    MenuAction(stringResource(R.string.library_asset_licence)) { onOpenLicences(asset.id) },
                    MenuAction(stringResource(R.string.action_delete)) { onDelete(DeleteKind.ASSET, asset.id, asset.name) },
                ),
            )
        }
    }
}

private fun LazyListScope.castSection(
    state: LibraryUiState,
    onDialog: (LibraryDialog) -> Unit,
    onDelete: (DeleteKind, Long, String) -> Unit,
) {
    item {
        PrimaryButton(
            text = stringResource(R.string.library_add_character),
            onClick = { onDialog(LibraryDialog.EditCharacter(null)) },
            modifier = Modifier.padding(horizontal = CreatoraTheme.spacing.lg),
            icon = Icons.Outlined.Person,
        )
    }
    if (state.characters.isEmpty()) {
        item {
            if (state.query.isNotBlank()) NoResults() else EmptyState(
                icon = Icons.Outlined.Person,
                title = stringResource(R.string.library_cast_empty_title),
                body = stringResource(R.string.library_cast_empty_body),
            )
        }
    } else {
        items(state.characters) { character ->
            val voiceName = state.voices.firstOrNull { it.id == character.voicePresetId }?.name
                ?: stringResource(R.string.library_character_no_voice)
            SimpleItemRow(
                title = character.name,
                subtitle = listOf(character.description, voiceName).filter { it.isNotBlank() }.joinToString(" · "),
                modifier = Modifier.padding(horizontal = CreatoraTheme.spacing.lg),
                actions = listOf(
                    MenuAction(stringResource(R.string.action_rename)) { onDialog(LibraryDialog.EditCharacter(character)) },
                    MenuAction(stringResource(R.string.action_delete)) { onDelete(DeleteKind.CHARACTER, character.id, character.name) },
                ),
            )
        }
    }
}

private fun LazyListScope.voicesSection(
    state: LibraryUiState,
    onDialog: (LibraryDialog) -> Unit,
    onDelete: (DeleteKind, Long, String) -> Unit,
) {
    item {
        PrimaryButton(
            text = stringResource(R.string.library_add_voice),
            onClick = { onDialog(LibraryDialog.EditVoice(null)) },
            modifier = Modifier.padding(horizontal = CreatoraTheme.spacing.lg),
            icon = Icons.Outlined.RecordVoiceOver,
        )
    }
    if (state.voices.isEmpty()) {
        item {
            if (state.query.isNotBlank()) NoResults() else EmptyState(
                icon = Icons.Outlined.RecordVoiceOver,
                title = stringResource(R.string.library_voices_empty_title),
                body = stringResource(R.string.library_voices_empty_body),
            )
        }
    } else {
        items(state.voices) { voice ->
            SimpleItemRow(
                title = voice.name,
                subtitle = stringResource(
                    R.string.library_voice_summary,
                    formatMultiplier(voice.pitch),
                    formatMultiplier(voice.speechRate),
                ),
                modifier = Modifier.padding(horizontal = CreatoraTheme.spacing.lg),
                actions = listOf(
                    MenuAction(stringResource(R.string.action_rename)) { onDialog(LibraryDialog.EditVoice(voice)) },
                    MenuAction(stringResource(R.string.action_delete)) { onDelete(DeleteKind.VOICE, voice.id, voice.name) },
                ),
            )
        }
    }
}

private fun LazyListScope.templatesSection(
    state: LibraryUiState,
    onDialog: (LibraryDialog) -> Unit,
    onDelete: (DeleteKind, Long, String) -> Unit,
) {
    if (state.templates.isEmpty()) {
        item {
            if (state.query.isNotBlank()) NoResults() else EmptyState(
                icon = Icons.Outlined.Share,
                title = stringResource(R.string.library_templates_empty_title),
                body = stringResource(R.string.library_templates_empty_body),
            )
        }
    } else {
        items(state.templates) { template ->
            SimpleItemRow(
                title = template.title,
                subtitle = null,
                modifier = Modifier.padding(horizontal = CreatoraTheme.spacing.lg),
                actions = listOf(
                    MenuAction(stringResource(R.string.library_use_template)) { onDialog(LibraryDialog.UseTemplate(template)) },
                    MenuAction(stringResource(R.string.action_delete)) { onDelete(DeleteKind.PROJECT, template.id, template.title) },
                ),
            )
        }
    }
}

@Composable
private fun NoResults() {
    Text(
        text = stringResource(R.string.library_no_results),
        style = CreatoraTheme.typography.body,
        color = CreatoraTheme.colors.textMuted,
        modifier = Modifier.padding(horizontal = CreatoraTheme.spacing.lg, vertical = CreatoraTheme.spacing.xl),
    )
}

private val PreviewState = LibraryUiState(
    projects = listOf(
        ProjectItem(Project(id = 1, title = "Preview project", createdAt = 0, updatedAt = 0), goesLiveAt = null),
    ),
)

@Composable
private fun LibraryPreview(darkTheme: Boolean, state: LibraryUiState = PreviewState) {
    CreatoraTheme(darkTheme = darkTheme) {
        LibraryContent(
            state = state,
            snackbar = remember { SnackbarHostState() },
            onFilter = {}, onQuery = {}, onCreateProject = {}, onRenameProject = { _, _ -> },
            onCopyProject = { _, _, _ -> }, onSaveCharacter = {}, onSaveVoice = {},
            onDelete = { _, _, _ -> }, onAddMedia = {}, onAddAudio = {},
            onOpenBrandKit = {}, onOpenLicences = {}, onOpenProject = {},
        )
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun LibraryLightPreview() = LibraryPreview(darkTheme = false)

@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LibraryDarkPreview() = LibraryPreview(darkTheme = true)

@Preview(name = "Assets empty, light", showBackground = true)
@Composable
private fun LibraryAssetsEmptyLightPreview() = LibraryPreview(false, LibraryUiState(filter = LibraryFilter.ASSETS))

@Preview(name = "Assets empty, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LibraryAssetsEmptyDarkPreview() = LibraryPreview(true, LibraryUiState(filter = LibraryFilter.ASSETS))
