package com.creatora.app.ui.render

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatora.app.domain.model.Asset
import com.creatora.app.domain.model.AssetType
import com.creatora.app.domain.model.Project
import com.creatora.app.domain.model.VideoFormat
import com.creatora.app.domain.model.applyPronunciations
import com.creatora.app.domain.render.AspectRatio
import com.creatora.app.domain.render.NarrationClip
import com.creatora.app.domain.render.RenderEvent
import com.creatora.app.domain.render.RenderRequest
import com.creatora.app.domain.render.SceneSource
import com.creatora.app.domain.render.VideoRenderer
import com.creatora.app.domain.render.buildCues
import com.creatora.app.domain.render.planScenes
import com.creatora.app.domain.render.splitSentences
import com.creatora.app.domain.repository.AssetRepository
import com.creatora.app.domain.repository.LicenceRepository
import com.creatora.app.domain.repository.MediaProbe
import com.creatora.app.domain.repository.MediaSaver
import com.creatora.app.domain.repository.Narrator
import com.creatora.app.domain.repository.ProjectRepository
import com.creatora.app.domain.repository.VoicePresetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RenderOptions(
    val ratio: AspectRatio = AspectRatio.LANDSCAPE,
    val captions: Boolean = true,
    /** Background music, an audio asset. */
    val musicAssetId: Long? = null,
)

/** Why a render could not start or finish. */
enum class RenderProblem { NO_SCRIPT, NO_FOOTAGE, NO_VOICE, RENDER_FAILED }

sealed interface RenderPhase {
    data object Idle : RenderPhase
    data class Narrating(val done: Int, val total: Int) : RenderPhase
    data class Rendering(val percent: Int) : RenderPhase
    data class Done(val uri: String) : RenderPhase
    data class Failed(val problem: RenderProblem) : RenderPhase
}

data class RenderUiState(
    val project: Project? = null,
    val options: RenderOptions = RenderOptions(),
    /** Pictures and videos this project may use, by its footage policy. */
    val footage: List<Asset> = emptyList(),
    val music: List<Asset> = emptyList(),
    val phase: RenderPhase = RenderPhase.Idle,
    val galleryMessage: GalleryMessage? = null,
) {
    /** The finished video: the one just made, or the project's last render. */
    val resultUri: String? get() = (phase as? RenderPhase.Done)?.uri ?: project?.renderUri
    val busy: Boolean get() = phase is RenderPhase.Narrating || phase is RenderPhase.Rendering
}

enum class GalleryMessage { SAVED, FAILED }

@HiltViewModel
class RenderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val projects: ProjectRepository,
    assets: AssetRepository,
    licences: LicenceRepository,
    private val voices: VoicePresetRepository,
    private val narrator: Narrator,
    private val probe: MediaProbe,
    private val renderer: VideoRenderer,
    private val saver: MediaSaver,
) : ViewModel() {

    private val projectId: Long = savedStateHandle[ARG_PROJECT_ID] ?: 0L
    private val options = MutableStateFlow<RenderOptions?>(null)
    private val phase = MutableStateFlow<RenderPhase>(RenderPhase.Idle)
    private val galleryMessage = MutableStateFlow<GalleryMessage?>(null)
    private var job: Job? = null

    val uiState: StateFlow<RenderUiState> = combine(
        projects.observe(projectId),
        assets.observeAll(),
        licences.observeAll(),
        options,
        combine(phase, galleryMessage, ::Pair),
    ) { project, assetList, licenceList, chosen, (currentPhase, message) ->
        val licensed = licenceList.mapTo(mutableSetOf()) { it.assetId }
        val usable = assetList.filter { asset ->
            (asset.projectId == null || asset.projectId == project?.id) &&
                (project?.licensedFootageOnly != true || asset.id in licensed)
        }
        RenderUiState(
            project = project,
            options = chosen ?: RenderOptions(ratio = project?.format.defaultRatio()),
            footage = usable.filter { it.type != AssetType.AUDIO },
            music = usable.filter { it.type == AssetType.AUDIO },
            phase = currentPhase,
            galleryMessage = message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RenderUiState())

    private fun edit(transform: (RenderOptions) -> RenderOptions) {
        options.value = transform(uiState.value.options)
    }

    fun setRatio(ratio: AspectRatio) = edit { it.copy(ratio = ratio) }
    fun setCaptions(on: Boolean) = edit { it.copy(captions = on) }
    fun setMusic(assetId: Long?) = edit { it.copy(musicAssetId = assetId) }

    fun render() {
        val state = uiState.value
        val project = state.project ?: return
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            galleryMessage.value = null
            val problem = run(project, state)
            phase.value = if (problem == null) phase.value else RenderPhase.Failed(problem)
            narrator.release()
        }
    }

    /** Runs the render; returns the problem, or null if it finished or was cancelled. */
    private suspend fun run(project: Project, state: RenderUiState): RenderProblem? {
        val sentences = splitSentences(project.script)
        if (sentences.isEmpty()) return RenderProblem.NO_SCRIPT
        val sources = state.footage.mapNotNull { asset ->
            when (asset.type) {
                AssetType.IMAGE -> SceneSource(asset.uri, isVideo = false, durationMs = null)
                else -> SceneSource(asset.uri, isVideo = true, durationMs = probe.videoDurationMs(asset.uri))
            }
        }
        val voice = voices.observe(project.narratorVoiceId ?: 0L).first()
        val clips = mutableListOf<NarrationClip>()
        for ((index, sentence) in sentences.withIndex()) {
            phase.value = RenderPhase.Narrating(index, sentences.size)
            val spoken = applyPronunciations(sentence, project.pronunciations)
            clips += narrator.synthesize(spoken, voice?.pitch ?: 1f, voice?.speechRate ?: 1f) ?: return RenderProblem.NO_VOICE
        }
        val scenes = planScenes(sources, clips.sumOf { it.durationUs })
        if (scenes.isEmpty()) return RenderProblem.NO_FOOTAGE

        val request = RenderRequest(
            title = project.title,
            ratio = state.options.ratio,
            scenes = scenes,
            narration = clips,
            cues = if (state.options.captions) buildCues(sentences, clips) else emptyList(),
            musicUri = state.music.firstOrNull { it.id == state.options.musicAssetId }?.uri,
        )
        phase.value = RenderPhase.Rendering(0)
        var problem: RenderProblem? = null
        renderer.render(request).collect { event ->
            when (event) {
                is RenderEvent.Progress -> phase.value = RenderPhase.Rendering(event.percent)
                is RenderEvent.Failed -> problem = RenderProblem.RENDER_FAILED
                is RenderEvent.Done -> {
                    projects.save(project.copy(renderUri = event.uri, captionsIncluded = state.options.captions, isReady = true, updatedAt = System.currentTimeMillis()))
                    phase.value = RenderPhase.Done(event.uri)
                }
            }
        }
        return problem
    }

    fun cancel() {
        job?.cancel()
        narrator.release()
        phase.value = RenderPhase.Idle
    }

    /** Copies the finished video to the gallery (Android 10+). Older phones use [saveTo] with a file dialog. */
    fun saveToGallery(displayName: String) {
        val uri = uiState.value.resultUri ?: return
        viewModelScope.launch {
            galleryMessage.value = if (saver.saveToGallery(uri, displayName, "video/mp4")) GalleryMessage.SAVED else GalleryMessage.FAILED
        }
    }

    fun saveTo(destinationUri: String) {
        val uri = uiState.value.resultUri ?: return
        viewModelScope.launch {
            galleryMessage.value = if (saver.copyTo(uri, destinationUri)) GalleryMessage.SAVED else GalleryMessage.FAILED
        }
    }

    fun messageShown() = galleryMessage.update { null }

    override fun onCleared() {
        narrator.release()
    }

    companion object {
        const val ARG_PROJECT_ID = "projectId"
    }
}

/** Vertical for Shorts / Reels, square for posts, wide for everything else. */
fun VideoFormat?.defaultRatio(): AspectRatio = when (this) {
    VideoFormat.SHORTS_REELS -> AspectRatio.PORTRAIT
    VideoFormat.POST_CAROUSEL -> AspectRatio.SQUARE
    else -> AspectRatio.LANDSCAPE
}
