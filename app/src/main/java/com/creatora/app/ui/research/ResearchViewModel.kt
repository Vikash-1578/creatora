package com.creatora.app.ui.research

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatora.app.domain.model.Idea
import com.creatora.app.domain.repository.CompetitorRepository
import com.creatora.app.domain.repository.IdeaRepository
import com.creatora.app.domain.repository.SwipeRepository
import com.creatora.app.domain.repository.YouTubeApi
import com.creatora.app.domain.repository.YouTubeKeyStore
import com.creatora.app.domain.research.Competition
import com.creatora.app.domain.research.Competitor
import com.creatora.app.domain.research.Outlier
import com.creatora.app.domain.research.SwipeItem
import com.creatora.app.domain.research.YtChannel
import com.creatora.app.domain.research.YtError
import com.creatora.app.domain.research.YtOutcome
import com.creatora.app.domain.research.YtVideo
import com.creatora.app.domain.research.competitionFor
import com.creatora.app.domain.research.findOutliers
import com.creatora.app.domain.research.parseChannelQuery
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ResearchTab { KEYWORDS, OUTLIERS, RIVALS, TRENDS }

/** What a keyword search found. */
data class KeywordResult(val query: String, val totalResults: Long, val competition: Competition, val videos: List<YtVideo>)

enum class ResearchNotice { SAVED_TO_SWIPE, SAVED_AS_IDEA, ALREADY_TRACKED }

data class ResearchUiState(
    val hasKey: Boolean = false,
    val tab: ResearchTab = ResearchTab.KEYWORDS,
    val query: String = "",
    val loading: Boolean = false,
    val error: YtError? = null,
    val keywords: KeywordResult? = null,
    /** Null until an outlier search was run. */
    val outliers: List<Outlier>? = null,
    /** The channel a Rivals search found; null if none (see [channelSearched]). */
    val foundChannel: YtChannel? = null,
    val channelSearched: Boolean = false,
    val trending: List<YtVideo>? = null,
    val competitors: List<Competitor> = emptyList(),
    val notice: ResearchNotice? = null,
)

@HiltViewModel
class ResearchViewModel @Inject constructor(
    private val api: YouTubeApi,
    keys: YouTubeKeyStore,
    private val competitors: CompetitorRepository,
    private val swipe: SwipeRepository,
    private val ideas: IdeaRepository,
) : ViewModel() {

    private val local = MutableStateFlow(ResearchUiState())

    val uiState: StateFlow<ResearchUiState> = combine(local, keys.hasKey, competitors.observeAll()) { state, hasKey, tracked ->
        state.copy(hasKey = hasKey, competitors = tracked)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ResearchUiState())

    fun setQuery(query: String) = local.update { it.copy(query = query) }

    fun selectTab(tab: ResearchTab) {
        local.update { it.copy(tab = tab, error = null) }
        if (tab == ResearchTab.TRENDS && local.value.trending == null) loadTrending()
    }

    /** Runs the search for the current tab (Trends reloads the chart). */
    fun search() {
        val state = local.value
        val query = state.query.trim()
        when (state.tab) {
            ResearchTab.TRENDS -> loadTrending()
            ResearchTab.KEYWORDS -> if (query.isNotEmpty()) run { searchKeywords(query) }
            ResearchTab.OUTLIERS -> if (query.isNotEmpty()) run { searchOutliers(query) }
            ResearchTab.RIVALS -> if (query.isNotEmpty()) run { searchChannel(query) }
        }
    }

    private fun loadTrending() = run {
        when (val result = api.trending(regionCode())) {
            is YtOutcome.Ok -> local.update { it.copy(trending = result.value) }
            is YtOutcome.Failed -> fail(result.error)
        }
    }

    private suspend fun searchKeywords(query: String) {
        when (val result = api.searchVideos(query)) {
            is YtOutcome.Ok -> local.update {
                it.copy(keywords = KeywordResult(query, result.value.totalResults, competitionFor(result.value.totalResults), result.value.videos))
            }
            is YtOutcome.Failed -> fail(result.error)
        }
    }

    private suspend fun searchOutliers(query: String) {
        val search = when (val result = api.searchVideos(query)) {
            is YtOutcome.Ok -> result.value
            is YtOutcome.Failed -> return fail(result.error)
        }
        val channels = when (val result = api.channels(search.videos.map { it.channelId }.distinct())) {
            is YtOutcome.Ok -> result.value
            is YtOutcome.Failed -> return fail(result.error)
        }
        local.update { it.copy(outliers = findOutliers(search.videos, channels)) }
    }

    private suspend fun searchChannel(query: String) {
        when (val result = api.findChannel(parseChannelQuery(query))) {
            is YtOutcome.Ok -> local.update { it.copy(foundChannel = result.value, channelSearched = true) }
            is YtOutcome.Failed -> fail(result.error)
        }
    }

    /** Starts tracking [channel]; a channel that is already tracked is left alone. */
    fun track(channel: YtChannel) {
        viewModelScope.launch {
            if (competitors.observeAll().first().any { it.channelId == channel.id }) {
                return@launch local.update { it.copy(notice = ResearchNotice.ALREADY_TRACKED) }
            }
            competitors.save(Competitor(channelId = channel.id, title = channel.title, subscribers = channel.subscribers, views = channel.views, videos = channel.videos))
        }
    }

    fun untrack(competitor: Competitor) {
        viewModelScope.launch { competitors.delete(competitor.id) }
    }

    /** Reads the tracked channels' current numbers again (one cheap API call for all of them). */
    fun refreshCompetitors() = run {
        val tracked = competitors.observeAll().first()
        if (tracked.isEmpty()) return@run
        when (val result = api.channels(tracked.map { it.channelId })) {
            is YtOutcome.Ok -> result.value.forEach { channel ->
                tracked.firstOrNull { it.channelId == channel.id }?.let {
                    competitors.save(it.copy(title = channel.title, subscribers = channel.subscribers, views = channel.views, videos = channel.videos, updatedAt = System.currentTimeMillis()))
                }
            }
            is YtOutcome.Failed -> fail(result.error)
        }
    }

    fun saveToSwipeFile(video: YtVideo) {
        viewModelScope.launch {
            swipe.save(SwipeItem(title = video.title, url = video.url, channelTitle = video.channelTitle))
            local.update { it.copy(notice = ResearchNotice.SAVED_TO_SWIPE) }
        }
    }

    fun saveAsIdea(video: YtVideo) {
        viewModelScope.launch {
            ideas.save(Idea(title = video.title, notes = video.url))
            local.update { it.copy(notice = ResearchNotice.SAVED_AS_IDEA) }
        }
    }

    fun noticeShown() = local.update { it.copy(notice = null) }

    private fun run(block: suspend () -> Unit) {
        if (local.value.loading) return
        local.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            block()
            local.update { it.copy(loading = false) }
        }
    }

    private fun fail(error: YtError) = local.update { it.copy(error = error) }

    private fun regionCode(): String = Locale.getDefault().country.ifEmpty { "US" }
}
