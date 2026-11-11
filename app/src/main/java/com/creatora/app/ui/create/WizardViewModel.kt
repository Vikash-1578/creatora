package com.creatora.app.ui.create

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatora.app.domain.model.Character
import com.creatora.app.domain.model.Project
import com.creatora.app.domain.model.RenderEstimate
import com.creatora.app.domain.model.VideoFormat
import com.creatora.app.domain.model.VoicePreset
import com.creatora.app.domain.model.WizardStep
import com.creatora.app.domain.model.applyPronunciations
import com.creatora.app.domain.model.estimateRender
import com.creatora.app.domain.model.readingSeconds
import com.creatora.app.domain.model.wordCount
import com.creatora.app.domain.repository.AssetRepository
import com.creatora.app.domain.repository.CharacterRepository
import com.creatora.app.domain.repository.LicenceRepository
import com.creatora.app.domain.repository.ProjectRepository
import com.creatora.app.domain.repository.SpeechPlayer
import com.creatora.app.domain.repository.VoicePresetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Reasons a project can't be marked ready yet. */
enum class Blocker { EMPTY_SCRIPT }

data class WizardUiState(
    /** Null while the project is loading. */
    val project: Project? = null,
    val characters: List<Character> = emptyList(),
    val voices: List<VoicePreset> = emptyList(),
    val importedAssets: Int = 0,
    /** Imported assets with at least one licence log entry. */
    val licensedAssets: Int = 0,
    val speechUnavailable: Boolean = false,
) {
    val step: WizardStep
        get() = WizardStep.entries[(project?.step ?: 0).coerceIn(0, WizardStep.entries.lastIndex)]

    val cast: List<Character> get() = characters.filter { it.id in project?.castIds.orEmpty() }
    val words: Int get() = wordCount(project?.script.orEmpty())
    val estimate: RenderEstimate get() = estimateRender(readingSeconds(words))
    val blockers: List<Blocker> get() = if (words == 0) listOf(Blocker.EMPTY_SCRIPT) else emptyList()

    /** The Brief step needs a project name; every other step can be skipped through. */
    val canGoNext: Boolean get() = step != WizardStep.BRIEF || project?.title?.isNotBlank() == true
}

@OptIn(FlowPreview::class)
@HiltViewModel
class WizardViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val projects: ProjectRepository,
    private val characters: CharacterRepository,
    voices: VoicePresetRepository,
    assets: AssetRepository,
    licences: LicenceRepository,
    private val speech: SpeechPlayer,
) : ViewModel() {

    private val projectId: Long = savedStateHandle[ARG_PROJECT_ID] ?: 0L

    /** The project being edited. Edits show at once and are saved to Room after a short pause. */
    private val draft = MutableStateFlow<Project?>(null)
    private val speechUnavailable = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            draft.value = projects.observe(projectId).first()
            draft.filterNotNull().drop(1).debounce(AUTOSAVE_MS).collect { projects.save(it) }
        }
    }

    val uiState: StateFlow<WizardUiState> = combine(
        draft,
        characters.observeAll(),
        voices.observeAll(),
        combine(assets.observeAll(), licences.observeAll()) { assetList, licenceList ->
            assetList.size to assetList.count { asset -> licenceList.any { it.assetId == asset.id } }
        },
        speechUnavailable,
    ) { project, characterList, voiceList, (imported, licensed), unavailable ->
        WizardUiState(project, characterList, voiceList, imported, licensed, unavailable)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WizardUiState())

    private fun edit(transform: (Project) -> Project) {
        draft.update { it?.let(transform)?.copy(updatedAt = System.currentTimeMillis()) }
    }

    fun setTitle(title: String) = edit { it.copy(title = title) }
    fun setFormat(format: VideoFormat) = edit { it.copy(format = format) }
    fun setBrief(brief: String) = edit { it.copy(brief = brief) }
    fun setHook(text: String) = edit { it.copy(hook = text) }
    fun setStructure(text: String) = edit { it.copy(structure = text) }
    fun setCallToAction(text: String) = edit { it.copy(callToAction = text) }
    fun setPronunciations(text: String) = edit { it.copy(pronunciations = text) }
    fun setNarratorVoice(voiceId: Long?) = edit { it.copy(narratorVoiceId = voiceId) }
    fun setLicensedFootageOnly(value: Boolean) = edit { it.copy(licensedFootageOnly = value) }

    fun toggleCast(characterId: Long) = edit {
        it.copy(castIds = if (characterId in it.castIds) it.castIds - characterId else it.castIds + characterId)
    }

    /** Gives a character a voice preset. Saved on the character, so the Library shows it too. */
    fun assignVoice(character: Character, voiceId: Long?) {
        viewModelScope.launch { characters.save(character.copy(voicePresetId = voiceId)) }
    }

    /** Moves to another step and saves right away. */
    fun goToStep(index: Int) {
        stopPreview()
        edit { it.copy(step = index.coerceIn(0, WizardStep.entries.lastIndex)) }
        flush()
    }

    fun markReady() {
        edit { it.copy(isReady = true) }
        flush()
    }

    /** Saves the current draft now, even if this screen is closing. */
    fun flush() {
        viewModelScope.launch(NonCancellable) { draft.value?.let { projects.save(it) } }
    }

    /** Speaks the start of the script (or [fallbackText]) with [voice]; null is the phone's default voice. */
    fun preview(voice: VoicePreset?, fallbackText: String) {
        val project = draft.value ?: return
        val text = applyPronunciations(project.script.ifBlank { fallbackText }.take(PREVIEW_CHARS), project.pronunciations)
        viewModelScope.launch {
            if (!speech.speak(text, voice?.pitch ?: 1f, voice?.speechRate ?: 1f)) speechUnavailable.value = true
        }
    }

    fun stopPreview() {
        speech.stop()
    }

    override fun onCleared() {
        flush()
        speech.stop()
        speech.release()
    }

    companion object {
        const val ARG_PROJECT_ID = "projectId"
        const val AUTOSAVE_MS = 500L
        const val PREVIEW_CHARS = 240
    }
}
