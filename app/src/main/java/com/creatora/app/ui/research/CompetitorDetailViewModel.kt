package com.creatora.app.ui.research

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatora.app.domain.account.AdapterError
import com.creatora.app.domain.account.ChannelPreview
import com.creatora.app.domain.account.VideoStats
import com.creatora.app.domain.ai.AiTasks
import com.creatora.app.domain.home.CreatorIdea
import com.creatora.app.domain.home.dayKey
import com.creatora.app.domain.home.isFresh
import com.creatora.app.domain.model.AiError
import com.creatora.app.domain.model.AiOutcome
import com.creatora.app.domain.repository.AuthService
import com.creatora.app.domain.repository.CacheRepository
import com.creatora.app.domain.repository.CompetitorRepository
import com.creatora.app.domain.research.CompetitorSnapshot
import com.creatora.app.domain.research.Fetched
import com.creatora.app.domain.research.Growth
import com.creatora.app.domain.research.IdeaActions
import com.creatora.app.domain.research.ResearchRepository
import com.creatora.app.domain.research.ScoredVideo
import com.creatora.app.domain.research.ViralConcept
import com.creatora.app.domain.research.averageViews
import com.creatora.app.domain.research.competitorFacts
import com.creatora.app.domain.research.growthFrom
import com.creatora.app.domain.research.parseViralConcept
import com.creatora.app.domain.research.scoreOutliers
import com.creatora.app.domain.research.topVideos
import com.creatora.app.domain.research.uploadsPerWeek
import com.creatora.app.domain.research.viral
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the competitor page shows. Numbers from the videos are null or empty when the videos could not be loaded. */
data class CompetitorDetail(
    val info: ChannelPreview,
    val growth: Growth,
    val uploadsPerWeek: Double?,
    val averageViews: Long?,
    val topVideos: List<VideoStats>,
    val viral: List<ScoredVideo>,
    val videosError: AdapterError?,
    val updatedAt: Long,
    val stale: Boolean,
)

sealed interface DetailState {
    data object Loading : DetailState
    data class Failed(val error: AdapterError) : DetailState
    data class Ready(val detail: CompetitorDetail) : DetailState
}

sealed interface ViralState {
    data object Loading : ViralState
    data class Ready(val concept: ViralConcept) : ViralState
    data class Failed(val errors: List<AiError>) : ViralState
}

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

/** One competitor: public numbers, our own growth history, top recent videos and the viral ones explained by the AI. */
@HiltViewModel
class CompetitorDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val auth: AuthService,
    private val competitors: CompetitorRepository,
    private val research: ResearchRepository,
    private val ai: AiTasks,
    private val cache: CacheRepository,
    private val actions: IdeaActions,
) : ViewModel() {

    private val channelId: String = checkNotNull(savedState[ARG_CHANNEL_ID])

    private val _state = MutableStateFlow<DetailState>(DetailState.Loading)
    val state: StateFlow<DetailState> = _state.asStateFlow()

    private val _viral = MutableStateFlow<Map<String, ViralState>>(emptyMap())
    val viralNotes: StateFlow<Map<String, ViralState>> = _viral.asStateFlow()

    private var uid: String? = null
    private var channelTitle = ""

    init {
        load(force = false)
    }

    /** A refresh costs about 3 YouTube quota units. */
    fun refresh() = load(force = true)

    private fun load(force: Boolean) {
        _state.value = DetailState.Loading
        viewModelScope.launch {
            val id = auth.user.first()?.uid ?: return@launch
            uid = id
            val info = when (val result = research.channelInfo(id, channelId, force)) {
                is Fetched.Failed -> { _state.value = DetailState.Failed(result.error); return@launch }
                is Fetched.Ok -> result
            }
            channelTitle = info.value.title
            val now = System.currentTimeMillis()
            val today = dayKey(now)
            val saved = competitors.snapshots(id, channelId)
            val snapshots = if (saved.any { it.day == today }) saved else {
                val fresh = CompetitorSnapshot(today, info.value.subscribers, info.value.viewCount ?: 0, info.value.videoCount ?: 0)
                competitors.addSnapshot(id, channelId, fresh)
                saved + fresh
            }
            val videos = research.channelVideos(id, channelId, force)
            val list = (videos as? Fetched.Ok)?.value.orEmpty()
            val scored = scoreOutliers(list, now)
            _state.value = DetailState.Ready(
                CompetitorDetail(
                    info = info.value,
                    growth = growthFrom(snapshots),
                    uploadsPerWeek = uploadsPerWeek(list, now),
                    averageViews = averageViews(list),
                    topVideos = topVideos(list),
                    viral = viral(scored).take(MAX_VIRAL),
                    videosError = (videos as? Fetched.Failed)?.error,
                    updatedAt = info.cachedAt,
                    stale = info.stale || (videos as? Fetched.Ok)?.stale == true,
                ),
            )
        }
    }

    /** Why a competitor's video went viral, with a summary of its top comments. One AI action, cached for 24 hours. */
    fun explain(item: ScoredVideo) {
        val id = uid ?: return
        val videoId = item.video.id
        if (_viral.value[videoId] != null && _viral.value[videoId] !is ViralState.Failed) return
        setViral(videoId, ViralState.Loading)
        viewModelScope.launch {
            val key = "cviral_$videoId"
            val now = System.currentTimeMillis()
            val cachedText = cache.get(id, key)?.takeIf { isFresh(it.cachedAt, DAY_MILLIS, now) }?.text
            var text = cachedText
            if (text == null) {
                val comments = (research.comments(id, videoId) as? Fetched.Ok)?.value
                text = when (val outcome = ai.competitorViral(competitorFacts(item, channelTitle, comments, now))) {
                    is AiOutcome.Ok -> outcome.value
                    is AiOutcome.Failed -> { setViral(videoId, ViralState.Failed(outcome.errors)); return@launch }
                }
            }
            val concept = parseViralConcept(text)
            // An unreadable reply is not kept, so trying again asks the AI again.
            if (concept != null && cachedText == null) cache.put(id, key, text, now)
            setViral(videoId, if (concept == null) ViralState.Failed(emptyList()) else ViralState.Ready(concept))
        }
    }

    /**
     * "Use this concept" ([letAi] false) opens a project with the concept as the brief; "Let AI make it" also has the AI write
     * the script draft. Either way it is the creator's own video: only the concept is used, never the original title.
     */
    fun use(concept: ViralConcept, letAi: Boolean, onStarted: (projectId: Long, draftFailed: Boolean) -> Unit) {
        val id = uid ?: return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val idea = CreatorIdea(title = concept.concept, reason = concept.why, source = "competitor", day = dayKey(now), createdAt = now)
            val started = actions.start(id, idea, draftWithAi = letAi)
            onStarted(started.projectId, started.draftFailed)
        }
    }

    private fun setViral(videoId: String, state: ViralState) = _viral.update { it + (videoId to state) }

    companion object {
        const val ARG_CHANNEL_ID = "channelId"
        private const val MAX_VIRAL = 3
    }
}
