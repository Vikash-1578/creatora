package com.creatora.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatora.app.domain.account.AdapterError
import com.creatora.app.domain.account.ConnectedAccount
import com.creatora.app.domain.account.PlanResolver
import com.creatora.app.domain.account.PlatformSupport
import com.creatora.app.domain.ai.AiTasks
import com.creatora.app.domain.home.AccountContext
import com.creatora.app.domain.home.ChannelOverviewLoader
import com.creatora.app.domain.home.CreatorIdea
import com.creatora.app.domain.home.IdeaStatus
import com.creatora.app.domain.home.dayKey
import com.creatora.app.domain.home.ideaFacts
import com.creatora.app.domain.home.parseDailyIdeas
import com.creatora.app.domain.model.AiError
import com.creatora.app.domain.model.AiOutcome
import com.creatora.app.domain.model.Project
import com.creatora.app.domain.model.VideoFormat
import com.creatora.app.domain.repository.AuthService
import com.creatora.app.domain.repository.CacheRepository
import com.creatora.app.domain.repository.CreatorIdeaRepository
import com.creatora.app.domain.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface IdeasState {
    data object NoAccount : IdeasState
    data object PickOne : IdeasState
    data object Loading : IdeasState
    /** Today's ideas still waiting for a choice. Empty when the user has dealt with all of them. */
    data class Ready(val ideas: List<CreatorIdea>, val limit: Int) : IdeasState
    data object NotEnoughData : IdeasState
    data class Unavailable(val error: AdapterError) : IdeasState
    data class AiFailed(val errors: List<AiError>) : IdeasState
}

/**
 * Daily Ideas: Free 3 a day, Pro 10 a day, per account. One AI action makes the day's batch; it is stored in the idea
 * bank and not made again that day, even when every idea was dismissed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class IdeasViewModel @Inject constructor(
    private val context: AccountContext,
    private val loader: ChannelOverviewLoader,
    private val ai: AiTasks,
    private val ideas: CreatorIdeaRepository,
    private val cache: CacheRepository,
    private val auth: AuthService,
    private val plans: PlanResolver,
    private val projects: ProjectRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<IdeasState>(IdeasState.Loading)
    val state: StateFlow<IdeasState> = _state.asStateFlow()
    private var uid: String? = null

    init {
        viewModelScope.launch {
            context.selected.collectLatest { accounts ->
                val target = accounts.singleOrNull()
                when {
                    accounts.isEmpty() -> _state.value = IdeasState.NoAccount
                    target == null -> _state.value = IdeasState.PickOne
                    target.platform.support != PlatformSupport.FULL -> _state.value = IdeasState.Unavailable(AdapterError.NotAvailable("Only the link is saved for this platform"))
                    else -> follow(target)
                }
            }
        }
    }

    /** Shows today's NEW ideas for [target] as they change, and makes the day's batch first if none was made yet. */
    private suspend fun follow(target: ConnectedAccount) {
        _state.value = IdeasState.Loading
        val id = auth.user.first()?.uid ?: return
        uid = id
        val day = dayKey(System.currentTimeMillis())
        val limit = plans.plan(id).dailyIdeas
        val marker = "ideas_${target.key}_$day"
        if (cache.get(id, marker) == null) {
            val failure = makeBatch(id, target, day, limit, marker)
            if (failure != null) {
                _state.value = failure
                return
            }
        }
        ideas.observe(id)
            .map { all -> all.filter { it.source == "daily" && it.accountKey == target.key && it.day == day && it.status == IdeaStatus.NEW } }
            .collectLatest { today -> _state.value = IdeasState.Ready(today, limit) }
    }

    /** Null on success; otherwise the state to show instead. */
    private suspend fun makeBatch(uid: String, target: ConnectedAccount, day: String, limit: Int, marker: String): IdeasState? {
        val overview = loader.overview(target)
        overview.error?.let { return IdeasState.Unavailable(it) }
        val facts = ideaFacts(overview.channel?.title ?: target.title, overview.channel?.subscribers, overview.videos)
            ?: return IdeasState.NotEnoughData
        return when (val outcome = ai.dailyIdeas(facts, limit)) {
            is AiOutcome.Failed -> IdeasState.AiFailed(outcome.errors)
            is AiOutcome.Ok -> {
                val parsed = parseDailyIdeas(outcome.value, limit)
                if (parsed.isEmpty()) return IdeasState.AiFailed(emptyList())
                val now = System.currentTimeMillis()
                parsed.forEach { ideas.save(uid, CreatorIdea(title = it.title, reason = it.reason, score = it.score, source = "daily", accountKey = target.key, day = day, createdAt = now)) }
                cache.put(uid, marker, "1", now)
                null
            }
        }
    }

    fun save(idea: CreatorIdea) = setStatus(idea, IdeaStatus.SAVED)

    fun dismiss(idea: CreatorIdea) = setStatus(idea, IdeaStatus.DISMISSED)

    private fun setStatus(idea: CreatorIdea, status: IdeaStatus) {
        val id = uid ?: return
        viewModelScope.launch { ideas.setStatus(id, idea.id, status) }
    }

    /** Create: starts a project from the idea, marks the idea Planned and links the two. */
    fun create(idea: CreatorIdea, onCreated: (Long) -> Unit) {
        val id = uid ?: return
        viewModelScope.launch {
            val projectId = projects.save(Project(title = idea.title, format = VideoFormat.LONG_VIDEO, brief = listOf(idea.title, idea.reason).filter { it.isNotBlank() }.joinToString("\n")))
            ideas.setStatus(id, idea.id, IdeaStatus.PLANNED, projectId)
            onCreated(projectId)
        }
    }
}
