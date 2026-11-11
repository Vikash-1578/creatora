package com.creatora.app.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatora.app.domain.account.AdapterOutcome
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.account.PlatformRegistry
import com.creatora.app.domain.account.Underperformer
import com.creatora.app.domain.account.VideoAnalytics
import com.creatora.app.domain.account.VideoInsight
import com.creatora.app.domain.account.findUnderperformers
import com.creatora.app.domain.account.insightFacts
import com.creatora.app.domain.account.parseInsights
import com.creatora.app.domain.ai.AiTasks
import com.creatora.app.domain.model.AiError
import com.creatora.app.domain.model.AiOutcome
import com.creatora.app.domain.repository.AuthService
import com.creatora.app.domain.repository.ConnectedAccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** One underperforming video: the real numbers, and the AI's explanation when it could be produced. */
data class InsightCard(val item: Underperformer, val analytics: VideoAnalytics?, val insight: VideoInsight?)

sealed interface InsightsUiState {
    /** No YouTube account is connected, so there is nothing to analyse. */
    data object NoAccount : InsightsUiState
    data class Analysing(val channelTitle: String) : InsightsUiState
    /** Not enough recent videos to compare fairly. */
    data class NotEnoughVideos(val count: Int) : InsightsUiState
    /** Every recent video is at least half as strong as the channel's usual. */
    data object AllHealthy : InsightsUiState
    data class Ready(
        val channelTitle: String,
        val cards: List<InsightCard>,
        /** Set when the numbers loaded but the AI explanation did not. */
        val aiErrors: List<AiError>? = null,
        /** Analytics (CTR, retention, traffic) could not be fetched, so only the basic numbers are shown. */
        val analyticsMissing: Boolean = false,
    ) : InsightsUiState
    /** The channel's data could not be fetched. */
    data object FetchFailed : InsightsUiState
}

/**
 * First insights on Home. Underperformers are found from real view counts; the AI is only asked to explain them,
 * using the numbers it is given. The first run happens when Home opens; a refresh costs one AI action.
 */
@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val auth: AuthService,
    private val accounts: ConnectedAccountRepository,
    private val registry: PlatformRegistry,
    private val ai: AiTasks,
) : ViewModel() {

    private val _state = MutableStateFlow<InsightsUiState>(InsightsUiState.NoAccount)
    val state: StateFlow<InsightsUiState> = _state.asStateFlow()

    private var started = false

    fun start() {
        if (started) return
        started = true
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val uid = auth.user.first()?.uid ?: return@launch _state.emit(InsightsUiState.NoAccount)
            val account = accounts.observe(uid).first().firstOrNull { it.platform == ConnectPlatform.YOUTUBE }
                ?: return@launch _state.emit(InsightsUiState.NoAccount)
            _state.value = InsightsUiState.Analysing(account.title)
            val adapter = registry.adapter(ConnectPlatform.YOUTUBE)

            val videos = when (val result = adapter.recentVideos(account.id, VIDEOS)) {
                is AdapterOutcome.Ok -> result.value
                is AdapterOutcome.Failed -> return@launch _state.emit(InsightsUiState.FetchFailed)
            }
            val under = findUnderperformers(videos)
            if (under.isEmpty()) {
                _state.value = if (videos.size < MIN_VIDEOS) InsightsUiState.NotEnoughVideos(videos.size) else InsightsUiState.AllHealthy
                return@launch
            }

            val analyticsResult = adapter.analytics(account.id, under.map { it.video.id })
            val analytics = (analyticsResult as? AdapterOutcome.Ok)?.value.orEmpty().associateBy { it.videoId }
            val outcome = ai.videoInsights(account.title, insightFacts(under, analytics))
            val insights = (outcome as? AiOutcome.Ok)?.let { parseInsights(it.value, under.map { u -> u.video.id }.toSet()) }.orEmpty().associateBy { it.videoId }

            _state.value = InsightsUiState.Ready(
                channelTitle = account.title,
                cards = under.map { InsightCard(it, analytics[it.video.id], insights[it.video.id]) },
                aiErrors = (outcome as? AiOutcome.Failed)?.errors,
                analyticsMissing = analyticsResult is AdapterOutcome.Failed,
            )
        }
    }

    private companion object {
        const val VIDEOS = 20
        const val MIN_VIDEOS = 4
    }
}
