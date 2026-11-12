package com.creatora.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatora.app.domain.account.AdapterError
import com.creatora.app.domain.account.AppLanguage
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.home.AccountContext
import com.creatora.app.domain.home.ChannelOverviewLoader
import com.creatora.app.domain.home.nicheFromVideos
import com.creatora.app.domain.repository.ErrorReporter
import com.creatora.app.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import com.creatora.app.domain.account.VideoStats
import com.creatora.app.domain.home.CreatorIdea
import com.creatora.app.domain.home.IdeaStatus
import com.creatora.app.domain.home.dayKey
import com.creatora.app.domain.repository.AuthService
import com.creatora.app.domain.repository.CreatorIdeaRepository
import com.creatora.app.domain.repository.HomePrefsRepository
import com.creatora.app.domain.repository.TrendingRepository
import com.creatora.app.domain.repository.TrendingResult
import com.creatora.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface TrendingState {
    data object Loading : TrendingState
    /** [niche] is null when nothing is known about the creator yet: the list is then general for the region. */
    data class Ready(val items: List<VideoStats>, val region: String, val niche: String?, val cachedAt: Long, val fromCache: Boolean, val stale: Boolean) : TrendingState
    data class Failed(val error: AdapterError) : TrendingState
}

/** Trending in the user's niche and region (the most popular chart, cached for a day). */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TrendingViewModel @Inject constructor(
    private val trending: TrendingRepository,
    private val auth: AuthService,
    private val users: UserRepository,
    private val prefs: HomePrefsRepository,
    private val ideas: CreatorIdeaRepository,
    private val context: AccountContext,
    private val loader: ChannelOverviewLoader,
    private val settings: SettingsRepository,
    private val errors: ErrorReporter,
) : ViewModel() {

    private val _state = MutableStateFlow<TrendingState>(TrendingState.Loading)
    val state: StateFlow<TrendingState> = _state.asStateFlow()

    init {
        load(force = false)
    }

    /** A refresh may cost API quota, so it only happens when asked for. */
    fun refresh() = load(force = true)

    private fun load(force: Boolean) {
        _state.value = TrendingState.Loading
        viewModelScope.launch {
            try {
                val user = auth.user.first() ?: return@launch
                // The channel itself tells where it is and what it is about, so the list is not a generic worldwide chart.
                val account = context.accounts.first().firstOrNull { it.platform == ConnectPlatform.YOUTUBE }
                val overview = account?.let { loader.overview(it) }
                val niche = users.observe(user.uid).first()?.creatorProfile?.niche?.takeIf { it.isNotBlank() } ?: overview?.videos?.let(::nicheFromVideos)
                val language = settings.language.first()
                val languageRegion = if (language != null && language != AppLanguage.ENGLISH) "IN" else null
                val region = prefs.region.first() ?: overview?.channel?.country?.takeIf { it.length == 2 } ?: languageRegion
                    ?: Locale.getDefault().country.ifEmpty { "US" }
                _state.value = when (val result = trending.trending(user.uid, region, niche, force)) {
                    is TrendingResult.Ok -> TrendingState.Ready(result.items, region, niche, result.cachedAt, result.fromCache, result.stale)
                    is TrendingResult.Failed -> TrendingState.Failed(result.error)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errors.record(e)
                _state.value = TrendingState.Failed(AdapterError.Network)
            }
        }
    }

    /** Adds a trending video to the idea bank as a saved idea. */
    fun useAsIdea(video: VideoStats) {
        viewModelScope.launch {
            val uid = auth.user.first()?.uid ?: return@launch
            val now = System.currentTimeMillis()
            ideas.save(uid, CreatorIdea(title = video.title, reason = video.channelTitle, source = "trending", status = IdeaStatus.SAVED, day = dayKey(now), createdAt = now))
        }
    }
}
