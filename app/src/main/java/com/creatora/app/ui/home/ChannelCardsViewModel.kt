package com.creatora.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatora.app.domain.account.AdapterError
import com.creatora.app.domain.account.ConnectedAccount
import com.creatora.app.domain.account.PlatformSupport
import com.creatora.app.domain.account.VideoAnalytics
import com.creatora.app.domain.account.VideoStats
import com.creatora.app.domain.ai.AiTasks
import com.creatora.app.domain.home.AccountContext
import com.creatora.app.domain.home.ChannelOverview
import com.creatora.app.domain.home.ChannelOverviewLoader
import com.creatora.app.domain.home.Snapshot
import com.creatora.app.domain.home.Streak
import com.creatora.app.domain.home.StreakUnit
import com.creatora.app.domain.home.UploadRow
import com.creatora.app.domain.home.buildSnapshot
import com.creatora.app.domain.home.classifyUploads
import com.creatora.app.domain.home.computeStreak
import com.creatora.app.domain.home.parsePublished
import com.creatora.app.domain.home.sumMetrics
import com.creatora.app.domain.home.uploadFacts
import com.creatora.app.domain.model.AiError
import com.creatora.app.domain.model.AiOutcome
import com.creatora.app.domain.repository.AuthService
import com.creatora.app.domain.repository.CacheRepository
import com.creatora.app.domain.repository.HomePrefsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the explanation of one upload looks like while it is being asked for. */
sealed interface WhyState {
    data object Closed : WhyState
    data class Loading(val row: UploadRow) : WhyState
    data class Ready(val row: UploadRow, val text: String) : WhyState
    data class Failed(val row: UploadRow, val errors: List<AiError>) : WhyState
}

enum class CardsPhase {
    /** No account is connected. */
    NO_ACCOUNT,
    LOADING,
    READY,
    /** The selected account's platform cannot be read yet (coming soon or link only). */
    UNSUPPORTED,
    /** The channel's videos could not be read. */
    FAILED,
}

data class ChannelCardsState(
    val phase: CardsPhase = CardsPhase.LOADING,
    val error: AdapterError? = null,
    val accountCount: Int = 0,
    val snapshot: Snapshot? = null,
    /** Why the Analytics numbers (watch time, earnings) are missing, when only they are. */
    val metricsError: AdapterError? = null,
    val uploads: List<UploadRow> = emptyList(),
    /** CTR and retention per video id; only for a single account (they cannot be averaged across accounts). */
    val uploadMetrics: Map<String, VideoAnalytics> = emptyMap(),
    val streak: Streak? = null,
    val streakUnit: StreakUnit = StreakUnit.WEEKS,
    val hideAmounts: Boolean = false,
    val why: WhyState = WhyState.Closed,
)

/**
 * The cards built from the selected account's real data: Snapshot, Recent uploads and Upload streak. "All accounts" sums
 * what can be summed and never averages CTR or retention.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChannelCardsViewModel @Inject constructor(
    private val context: AccountContext,
    private val loader: ChannelOverviewLoader,
    private val prefs: HomePrefsRepository,
    private val auth: AuthService,
    private val ai: AiTasks,
    private val cache: CacheRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChannelCardsState())
    val state: StateFlow<ChannelCardsState> = _state.asStateFlow()

    private var videos: List<VideoStats> = emptyList()
    private var current: List<ConnectedAccount> = emptyList()

    init {
        viewModelScope.launch { context.selected.collectLatest { load(it, force = false) } }
        viewModelScope.launch { prefs.hideAmounts.collect { hidden -> _state.update { it.copy(hideAmounts = hidden) } } }
        viewModelScope.launch {
            prefs.streakUnit.collect { unit ->
                _state.update { it.copy(streakUnit = unit, streak = streakFor(unit)) }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { load(current, force = true) }
    }

    fun setHideAmounts(hidden: Boolean) {
        viewModelScope.launch { prefs.setHideAmounts(hidden) }
    }

    fun setStreakUnit(unit: StreakUnit) {
        viewModelScope.launch { prefs.setStreakUnit(unit) }
    }

    private fun streakFor(unit: StreakUnit): Streak? =
        if (videos.isEmpty()) null else computeStreak(videos.mapNotNull { parsePublished(it.publishedAt) }, unit, System.currentTimeMillis())

    private suspend fun load(accounts: List<ConnectedAccount>, force: Boolean) {
        current = accounts
        if (accounts.isEmpty()) {
            videos = emptyList()
            _state.update { it.copy(phase = CardsPhase.NO_ACCOUNT, accountCount = 0, snapshot = null, uploads = emptyList(), streak = null, metricsError = null, error = null) }
            return
        }
        val readable = accounts.filter { it.platform.support == PlatformSupport.FULL }
        if (readable.isEmpty()) {
            videos = emptyList()
            val error = AdapterError.NotAvailable("Only the link is saved for this platform")
            _state.update { it.copy(phase = CardsPhase.UNSUPPORTED, error = error, accountCount = accounts.size, snapshot = null, uploads = emptyList(), streak = null) }
            return
        }
        _state.update { it.copy(phase = CardsPhase.LOADING, accountCount = accounts.size, error = null) }
        val overviews: List<ChannelOverview> = coroutineScope { readable.map { async { loader.overview(it, force) } }.map { it.await() } }
        val failed = overviews.firstOrNull { it.error != null }
        if (failed != null && overviews.all { it.error != null }) {
            videos = emptyList()
            _state.update { it.copy(phase = CardsPhase.FAILED, error = failed.error, snapshot = null, uploads = emptyList(), streak = null) }
            return
        }
        val ok = overviews.filter { it.error == null }
        videos = ok.flatMap { it.videos }
        val subscribers = ok.map { it.channel?.subscribers }.let { list -> if (list.any { it == null }) null else list.sumOf { it ?: 0L } }
        // A sum is only honest when every account has the numbers.
        val currentTotals = if (ok.all { it.current != null }) sumMetrics(ok.mapNotNull { it.current }) else null
        val previousTotals = if (ok.all { it.previous != null }) sumMetrics(ok.mapNotNull { it.previous }) else null
        val rows = classifyUploads(videos, System.currentTimeMillis())
        val metrics = if (ok.size == 1 && rows.isNotEmpty()) {
            loader.uploadMetrics(ok.first().account, rows.map { it.video.id }).associateBy { it.videoId }
        } else {
            emptyMap()
        }
        _state.update {
            it.copy(
                phase = CardsPhase.READY,
                snapshot = buildSnapshot(subscribers, currentTotals, previousTotals),
                metricsError = ok.firstOrNull { o -> o.metricsError != null }?.metricsError,
                uploads = rows,
                uploadMetrics = metrics,
                streak = streakFor(it.streakUnit),
                error = null,
            )
        }
    }

    /** One AI action explains why an upload did well or badly; the answer is cached for the video. */
    fun explain(row: UploadRow) {
        _state.update { it.copy(why = WhyState.Loading(row)) }
        viewModelScope.launch {
            val uid = auth.user.first()?.uid
            val key = "why_${row.video.id}"
            val cached = uid?.let { cache.get(it, key) }
            if (cached != null) {
                _state.update { it.copy(why = WhyState.Ready(row, cached.text)) }
                return@launch
            }
            val facts = uploadFacts(row, _state.value.uploadMetrics[row.video.id], System.currentTimeMillis())
            when (val outcome = ai.whyVideo(facts)) {
                is AiOutcome.Ok -> {
                    uid?.let { cache.put(it, key, outcome.value, System.currentTimeMillis()) }
                    _state.update { it.copy(why = WhyState.Ready(row, outcome.value)) }
                }
                is AiOutcome.Failed -> _state.update { it.copy(why = WhyState.Failed(row, outcome.errors)) }
            }
        }
    }

    fun closeWhy() = _state.update { it.copy(why = WhyState.Closed) }
}
