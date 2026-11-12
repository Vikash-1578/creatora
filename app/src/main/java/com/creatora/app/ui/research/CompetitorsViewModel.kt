package com.creatora.app.ui.research

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatora.app.domain.account.AdapterError
import com.creatora.app.domain.account.AdapterOutcome
import com.creatora.app.domain.account.ChannelPreview
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.account.Plan
import com.creatora.app.domain.account.PlanResolver
import com.creatora.app.domain.account.parseReference
import com.creatora.app.domain.home.AccountContext
import com.creatora.app.domain.home.dayKey
import com.creatora.app.domain.repository.AuthService
import com.creatora.app.domain.repository.Competitor
import com.creatora.app.domain.repository.CompetitorRepository
import com.creatora.app.domain.repository.UserRepository
import com.creatora.app.domain.research.CompetitorSnapshot
import com.creatora.app.domain.research.Fetched
import com.creatora.app.domain.research.ResearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why adding a competitor did not happen. */
sealed interface AddProblem {
    data object InvalidLink : AddProblem
    data object NotYouTube : AddProblem
    data object LimitReached : AddProblem
    data object Duplicate : AddProblem
    data object OwnChannel : AddProblem
    data class Failed(val error: AdapterError) : AddProblem
}

sealed interface Suggestions {
    data object Idle : Suggestions
    data object Loading : Suggestions
    /** The creator profile (niche) is not ready, so there is nothing to search for. */
    data object NeedsProfile : Suggestions
    data class Ready(val channels: List<ChannelPreview>, val cachedAt: Long, val fromCache: Boolean, val stale: Boolean) : Suggestions
    data class Failed(val error: AdapterError) : Suggestions
}

data class CompetitorsUi(
    val competitors: List<Competitor> = emptyList(),
    /** How many the plan allows: Free 3, Pro 20. */
    val limit: Int = Plan.FREE.maxCompetitors,
    val adding: Boolean = false,
    val problem: AddProblem? = null,
    val suggestions: Suggestions = Suggestions.Idle,
)

/** The competitors list: add by link, suggestions for the niche (only on tap, they cost 100 quota units) and remove. */
@HiltViewModel
class CompetitorsViewModel @Inject constructor(
    private val auth: AuthService,
    private val competitors: CompetitorRepository,
    private val research: ResearchRepository,
    private val plans: PlanResolver,
    private val users: UserRepository,
    private val context: AccountContext,
) : ViewModel() {

    private val _ui = MutableStateFlow(CompetitorsUi())
    val ui: StateFlow<CompetitorsUi> = _ui.asStateFlow()
    private var uid: String? = null

    init {
        viewModelScope.launch {
            val id = auth.user.first()?.uid ?: return@launch
            uid = id
            _ui.update { it.copy(limit = plans.plan(id).maxCompetitors) }
            competitors.observe(id).collect { list -> _ui.update { it.copy(competitors = list) } }
        }
    }

    /** Adds the channel behind a pasted link, @handle or channel id. */
    fun addByLink(text: String) {
        val id = uid ?: return
        val ref = parseReference(text)
        when {
            ref == null -> return problem(AddProblem.InvalidLink)
            ref.platform != ConnectPlatform.YOUTUBE -> return problem(AddProblem.NotYouTube)
        }
        _ui.update { it.copy(adding = true, problem = null) }
        viewModelScope.launch {
            val problem = when (val found = research.lookup(ref!!)) {
                is AdapterOutcome.Failed -> AddProblem.Failed(found.error)
                is AdapterOutcome.Ok -> add(id, found.value, "manual")
            }
            _ui.update { it.copy(adding = false, problem = problem) }
        }
    }

    fun addSuggestion(channel: ChannelPreview) {
        val id = uid ?: return
        viewModelScope.launch {
            val problem = add(id, channel, "auto")
            _ui.update { state ->
                val ready = state.suggestions as? Suggestions.Ready
                state.copy(
                    problem = problem,
                    suggestions = if (problem == null && ready != null) ready.copy(channels = ready.channels.filter { it.id != channel.id }) else state.suggestions,
                )
            }
        }
    }

    /** Checks the plan limit, duplicates and the user's own channels; null when the competitor was added. */
    private suspend fun add(uid: String, channel: ChannelPreview, source: String): AddProblem? {
        val current = _ui.value
        val own = context.accounts.first().map { it.id }
        return when {
            channel.id in own -> AddProblem.OwnChannel
            current.competitors.any { it.channelId == channel.id } -> AddProblem.Duplicate
            current.competitors.size >= current.limit -> AddProblem.LimitReached
            else -> {
                val now = System.currentTimeMillis()
                competitors.add(uid, Competitor(channel.id, channel.title, channel.photoUrl, channel.subscribers, channel.viewCount, channel.videoCount, now, source))
                // The first snapshot starts the growth history ("tracking since").
                competitors.addSnapshot(uid, channel.id, CompetitorSnapshot(dayKey(now), channel.subscribers, channel.viewCount ?: 0, channel.videoCount ?: 0))
                null
            }
        }
    }

    fun remove(competitor: Competitor) {
        val id = uid ?: return
        viewModelScope.launch { competitors.remove(id, competitor.channelId) }
    }

    fun dismissProblem() = _ui.update { it.copy(problem = null) }

    /** Channels for the creator's niche (search, 100 quota units) and channels featured by the user's and followed channels. */
    fun discover(force: Boolean = false) {
        val id = uid ?: return
        _ui.update { it.copy(suggestions = Suggestions.Loading) }
        viewModelScope.launch {
            val niche = users.observe(id).first()?.creatorProfile?.niche
            if (niche.isNullOrBlank()) {
                _ui.update { it.copy(suggestions = Suggestions.NeedsProfile) }
                return@launch
            }
            val own = context.accounts.first().filter { it.platform == ConnectPlatform.YOUTUBE }.map { it.id }
            val followed = _ui.value.competitors.map { it.channelId }
            val result = when (val found = research.discover(id, niche, (own + followed).distinct(), force)) {
                is Fetched.Failed -> Suggestions.Failed(found.error)
                is Fetched.Ok -> Suggestions.Ready(found.value.filter { it.id !in own && it.id !in followed }, found.cachedAt, found.fromCache, found.stale)
            }
            _ui.update { it.copy(suggestions = result) }
        }
    }

    private fun problem(problem: AddProblem) = _ui.update { it.copy(problem = problem) }
}
