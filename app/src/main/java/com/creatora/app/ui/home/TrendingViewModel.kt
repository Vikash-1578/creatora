package com.creatora.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatora.app.domain.account.AdapterError
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
            val user = auth.user.first() ?: return@launch
            val niche = users.observe(user.uid).first()?.creatorProfile?.niche
            val region = prefs.region.first() ?: Locale.getDefault().country.ifEmpty { "US" }
            _state.value = when (val result = trending.trending(user.uid, region, niche, force)) {
                is TrendingResult.Ok -> TrendingState.Ready(result.items, region, niche, result.cachedAt, result.fromCache, result.stale)
                is TrendingResult.Failed -> TrendingState.Failed(result.error)
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
