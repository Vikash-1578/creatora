package com.creatora.app.ui.research

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatora.app.domain.account.VideoStats
import com.creatora.app.domain.ai.AiTasks
import com.creatora.app.domain.home.CreatorIdea
import com.creatora.app.domain.home.ParsedIdea
import com.creatora.app.domain.home.dayKey
import com.creatora.app.domain.home.parseDailyIdeas
import com.creatora.app.domain.home.parsePublished
import com.creatora.app.domain.model.AiError
import com.creatora.app.domain.model.AiOutcome
import com.creatora.app.domain.repository.AuthService
import com.creatora.app.domain.repository.CacheRepository
import com.creatora.app.domain.repository.CompetitorRepository
import com.creatora.app.domain.repository.HomePrefsRepository
import com.creatora.app.domain.repository.TrendingRepository
import com.creatora.app.domain.repository.TrendingResult
import com.creatora.app.domain.repository.UserRepository
import com.creatora.app.domain.research.IdeaActions
import com.creatora.app.domain.research.ResearchRepository
import com.creatora.app.domain.research.scoreOutliers
import com.creatora.app.domain.research.uploadNextFacts
import com.creatora.app.domain.research.viral
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A competitor's video from the last 7 days that did at least twice that channel's usual (an estimate). */
data class RecentOutlier(val video: VideoStats, val score: Double, val competitor: String)

sealed interface NextState {
    data object Idle : NextState
    data object Loading : NextState
    /** There is no trending list and no competitor data yet, so nothing honest can be suggested. */
    data object NoSources : NextState
    data class Ready(val ideas: List<ParsedIdea>) : NextState
    data class Failed(val errors: List<AiError>) : NextState
}

data class TrendsUi(
    /** Null until the cached competitor data has been read. */
    val outliers: List<RecentOutlier>? = null,
    val hasCompetitors: Boolean = false,
    val next: NextState = NextState.Idle,
    val saved: Set<String> = emptySet(),
)

private const val WEEK_MILLIS = 7L * 24 * 60 * 60 * 1000
private const val DAY_MILLIS = 24L * 60 * 60 * 1000

/**
 * Trends beyond Home's chart: the competitors' outliers of the last 7 days (read from what is already cached, no quota)
 * and "Upload this next", one AI action a day built from real trending titles and those outliers.
 */
@HiltViewModel
class TrendsViewModel @Inject constructor(
    private val auth: AuthService,
    private val competitors: CompetitorRepository,
    private val research: ResearchRepository,
    private val trending: TrendingRepository,
    private val users: UserRepository,
    private val prefs: HomePrefsRepository,
    private val ai: AiTasks,
    private val cache: CacheRepository,
    private val actions: IdeaActions,
) : ViewModel() {

    private val _ui = MutableStateFlow(TrendsUi())
    val ui: StateFlow<TrendsUi> = _ui.asStateFlow()
    private var uid: String? = null

    init {
        viewModelScope.launch {
            val id = auth.user.first()?.uid ?: return@launch
            uid = id
            val now = System.currentTimeMillis()
            val followed = competitors.observe(id).first()
            val found = followed.flatMap { competitor ->
                val videos = research.peekChannelVideos(id, competitor.channelId).orEmpty()
                viral(scoreOutliers(videos, now)).filter { item -> parsePublished(item.video.publishedAt)?.let { now - it <= WEEK_MILLIS } == true }
                    .map { RecentOutlier(it.video, it.score, competitor.title) }
            }.sortedByDescending { it.score }
            _ui.update { it.copy(outliers = found, hasCompetitors = followed.isNotEmpty()) }
        }
    }

    /** "Upload this next": three suggestions, one AI action a day (a second tap the same day shows the cached ones). */
    fun suggest() {
        val id = uid ?: return
        if (_ui.value.next is NextState.Loading) return
        _ui.update { it.copy(next = NextState.Loading) }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val key = "tnext_${dayKey(now)}"
            val cachedText = cache.get(id, key)?.text
            var text = cachedText
            if (text == null) {
                val niche = users.observe(id).first()?.creatorProfile?.niche
                val region = prefs.region.first() ?: Locale.getDefault().country.ifEmpty { "US" }
                val chart = (trending.trending(id, region, niche, force = false) as? TrendingResult.Ok)?.items.orEmpty()
                val outliers = _ui.value.outliers.orEmpty()
                val facts = uploadNextFacts(niche, chart.map { it.title }, outliers.map { "${it.video.title} (${it.competitor})" })
                if (facts == null) { _ui.update { it.copy(next = NextState.NoSources) }; return@launch }
                text = when (val outcome = ai.uploadNext(facts)) {
                    is AiOutcome.Ok -> outcome.value
                    is AiOutcome.Failed -> { _ui.update { it.copy(next = NextState.Failed(outcome.errors)) }; return@launch }
                }
            }
            val ideas = parseDailyIdeas(text, SUGGESTIONS)
            // An unreadable reply is not kept, so trying again asks the AI again.
            if (ideas.isNotEmpty() && cachedText == null) cache.put(id, key, text, now)
            _ui.update { it.copy(next = if (ideas.isEmpty()) NextState.Failed(emptyList()) else NextState.Ready(ideas)) }
        }
    }

    fun save(idea: ParsedIdea) {
        val id = uid ?: return
        viewModelScope.launch {
            actions.save(id, toIdea(idea))
            _ui.update { it.copy(saved = it.saved + idea.title) }
        }
    }

    fun create(idea: ParsedIdea, onStarted: (Long) -> Unit) {
        val id = uid ?: return
        viewModelScope.launch { onStarted(actions.start(id, toIdea(idea)).projectId) }
    }

    private fun toIdea(idea: ParsedIdea): CreatorIdea {
        val now = System.currentTimeMillis()
        return CreatorIdea(title = idea.title, reason = idea.reason, score = idea.score, source = "trends", day = dayKey(now), createdAt = now)
    }

    private companion object {
        const val SUGGESTIONS = 3
    }
}
