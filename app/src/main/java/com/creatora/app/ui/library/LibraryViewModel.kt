package com.creatora.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatora.app.domain.model.Asset
import com.creatora.app.domain.model.AssetType
import com.creatora.app.domain.model.Character
import com.creatora.app.domain.model.Project
import com.creatora.app.domain.model.VoicePreset
import com.creatora.app.domain.repository.AssetRepository
import com.creatora.app.domain.repository.CharacterRepository
import com.creatora.app.domain.repository.LicenceRepository
import com.creatora.app.domain.repository.ProjectRepository
import com.creatora.app.domain.repository.ScheduledPostRepository
import com.creatora.app.domain.repository.UserFiles
import com.creatora.app.domain.repository.VoicePresetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LibraryFilter { PROJECTS, ASSETS, CAST, VOICES, TEMPLATES }

enum class DeleteKind { PROJECT, ASSET, CHARACTER, VOICE }

/** An item hidden from the list while its Undo snackbar shows; deleted for real when the snackbar ends. */
data class PendingDelete(val kind: DeleteKind, val id: Long, val name: String)

/** Result of the last import, shown once in a snackbar. */
data class ImportResult(val imported: Int, val skipped: Int)

data class ProjectItem(val project: Project, val goesLiveAt: Long?)

data class LibraryUiState(
    val filter: LibraryFilter = LibraryFilter.PROJECTS,
    val query: String = "",
    val projects: List<ProjectItem> = emptyList(),
    val templates: List<Project> = emptyList(),
    val assets: List<Asset> = emptyList(),
    val characters: List<Character> = emptyList(),
    val voices: List<VoicePreset> = emptyList(),
    val licenceCount: Int = 0,
    /** Assets that have at least one licence entry. */
    val licensedAssetIds: Set<Long> = emptySet(),
    val pendingDelete: PendingDelete? = null,
    val importResult: ImportResult? = null,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val projects: ProjectRepository,
    scheduledPosts: ScheduledPostRepository,
    private val assets: AssetRepository,
    private val characters: CharacterRepository,
    private val voices: VoicePresetRepository,
    licences: LicenceRepository,
    private val userFiles: UserFiles,
) : ViewModel() {

    private val filter = MutableStateFlow(LibraryFilter.PROJECTS)
    private val query = MutableStateFlow("")
    private val pendingDelete = MutableStateFlow<PendingDelete?>(null)
    private val importResult = MutableStateFlow<ImportResult?>(null)
    private var commitJob: Job? = null

    private val content = combine(
        projects.observeAll(),
        scheduledPosts.observeAll(),
        assets.observeAll(),
        characters.observeAll(),
        voices.observeAll(),
    ) { projectList, posts, assetList, characterList, voiceList ->
        val now = System.currentTimeMillis()
        val nextPost = posts.filter { it.scheduledAt >= now }.groupBy { it.projectId }
            .mapValues { (_, list) -> list.minOf { it.scheduledAt } }
        LibraryUiState(
            projects = projectList.filterNot { it.isTemplate }.map { ProjectItem(it, nextPost[it.id]) },
            templates = projectList.filter { it.isTemplate },
            assets = assetList,
            characters = characterList,
            voices = voiceList,
        )
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        content,
        licences.observeAll(),
        combine(filter, query, ::Pair),
        pendingDelete,
        importResult,
    ) { state, licenceList, (filter, query), pending, imported ->
        fun hidden(kind: DeleteKind, id: Long) = pending?.kind == kind && pending.id == id
        fun matches(text: String) = query.isBlank() || text.contains(query.trim(), ignoreCase = true)
        state.copy(
            filter = filter,
            query = query,
            projects = state.projects.filter { !hidden(DeleteKind.PROJECT, it.project.id) && matches(it.project.title) },
            templates = state.templates.filter { !hidden(DeleteKind.PROJECT, it.id) && matches(it.title) },
            assets = state.assets.filter { !hidden(DeleteKind.ASSET, it.id) && matches(it.name) },
            characters = state.characters.filter { !hidden(DeleteKind.CHARACTER, it.id) && matches(it.name) },
            voices = state.voices.filter { !hidden(DeleteKind.VOICE, it.id) && matches(it.name) },
            licenceCount = licenceList.size,
            licensedAssetIds = licenceList.mapTo(mutableSetOf()) { it.assetId },
            pendingDelete = pending,
            importResult = imported,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun setFilter(value: LibraryFilter) {
        filter.value = value
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun createProject(title: String) = launchIfNotBlank(title) {
        projects.save(Project(title = it))
    }

    fun renameProject(project: Project, title: String) = launchIfNotBlank(title) {
        projects.save(project.copy(title = it, updatedAt = System.currentTimeMillis()))
    }

    /** Copies [project] under [title]; also used to save a template and to start a project from one. */
    fun copyProject(project: Project, title: String, asTemplate: Boolean) = launchIfNotBlank(title) {
        val now = System.currentTimeMillis()
        projects.save(project.copy(id = 0, title = it, isTemplate = asTemplate, createdAt = now, updatedAt = now))
    }

    fun saveCharacter(character: Character) = launchIfNotBlank(character.name) {
        characters.save(character.copy(name = it))
    }

    fun saveVoice(voice: VoicePreset) = launchIfNotBlank(voice.name) {
        voices.save(voice.copy(name = it))
    }

    fun importFiles(uris: List<String>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            var imported = 0
            for (uri in uris) {
                val file = userFiles.import(uri) ?: continue
                val type = AssetType.fromMimeType(file.mimeType)
                if (type == null) {
                    userFiles.discard(file.uri)
                    continue
                }
                assets.save(Asset(uri = file.uri, type = type, name = file.name, mimeType = file.mimeType))
                imported++
            }
            importResult.value = ImportResult(imported, uris.size - imported)
        }
    }

    fun importResultShown() {
        importResult.value = null
    }

    /** Hides the item now and deletes it after [UNDO_WINDOW_MS] unless [undoDelete] is called. */
    fun delete(kind: DeleteKind, id: Long, name: String) {
        viewModelScope.launch {
            commitJob?.cancel()
            commitPendingDelete()
            pendingDelete.value = PendingDelete(kind, id, name)
            commitJob = launch {
                delay(UNDO_WINDOW_MS)
                commitPendingDelete()
            }
        }
    }

    fun undoDelete() {
        commitJob?.cancel()
        pendingDelete.value = null
    }

    /** Deletes the hidden item for real, and only then stops hiding it so it never flickers back. */
    private suspend fun commitPendingDelete() {
        val pending = pendingDelete.value ?: return
        when (pending.kind) {
            DeleteKind.PROJECT -> projects.delete(pending.id)
            DeleteKind.ASSET -> {
                val asset = assets.observe(pending.id).first()
                assets.delete(pending.id)
                asset?.let { userFiles.discard(it.uri) }
            }
            DeleteKind.CHARACTER -> characters.delete(pending.id)
            DeleteKind.VOICE -> voices.delete(pending.id)
        }
        pendingDelete.value = null
    }

    private fun launchIfNotBlank(text: String, block: suspend (String) -> Unit) {
        val trimmed = text.trim()
        if (trimmed.isNotEmpty()) viewModelScope.launch { block(trimmed) }
    }

    companion object {
        const val UNDO_WINDOW_MS = 5_000L
    }
}
