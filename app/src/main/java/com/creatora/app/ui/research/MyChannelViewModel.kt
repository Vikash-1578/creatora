package com.creatora.app.ui.research

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatora.app.domain.account.AdapterError
import com.creatora.app.domain.account.ConnectedAccount
import com.creatora.app.domain.ai.AiTasks
import com.creatora.app.domain.home.AccountContext
import com.creatora.app.domain.home.CreatorIdea
import com.creatora.app.domain.home.ParsedIdea
import com.creatora.app.domain.home.dayKey
import com.creatora.app.domain.home.isFresh
import com.creatora.app.domain.home.parseDailyIdeas
import com.creatora.app.domain.model.AiError
import com.creatora.app.domain.model.AiOutcome
import com.creatora.app.domain.repository.AuthService
import com.creatora.app.domain.repository.CacheRepository
import com.creatora.app.domain.research.Fetched
import com.creatora.app.domain.research.IdeaActions
import com.creatora.app.domain.research.Pick
import com.creatora.app.domain.research.ResearchRepository
import com.creatora.app.domain.research.ScoredVideo
import com.creatora.app.domain.research.outlierFacts
import com.creatora.app.domain.research.pickAccount
import com.creatora.app.domain.research.scoreOutliers
import com.creatora.app.domain.research.weakest
import com.creatora.app.domain.research.winners
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface MyChannelState {
    data object Loading : MyChannelState
    data object NoAccount : MyChannelState
    data object PickOne : MyChannelState
    data object Unsupported : MyChannelState
    data class Failed(val error: AdapterError) : MyChannelState
    /** Fewer videos than an honest comparison needs. */
    data class NotEnough(val videos: Int) : MyChannelState
    data class Ready(val winners: List<ScoredVideo>, val weakest: List<ScoredVideo>, val cachedAt: Long, val stale: Boolean) : MyChannelState
}

sealed interface NoteState {
    data object Loading : NoteState
    data class Text(val text: String) : NoteState
    data class Failed(val errors: List<AiError>) : NoteState
}

sealed interface MoreState {
    data object Loading : MoreState
    data class Ready(val ideas: List<ParsedIdea>) : MoreState
    data class Failed(val errors: List<AiError>) : MoreState
}

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

/**
 * My Channel: the user's videos ranked by outlier score (an estimate, see [scoreOutliers]), the AI's reasons for the
 * winners and the weakest, and follow-up ideas. Each explanation is one AI action, cached for a day.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MyChannelViewModel @Inject constructor(
    private val context: AccountContext,
    private val research: ResearchRepository,
    private val ai: AiTasks,
    private val cache: CacheRepository,
    private val auth: AuthService,
    private val actions: IdeaActions,
) : ViewModel() {

    private val _state = MutableStateFlow<MyChannelState>(MyChannelState.Loading)
    val state: StateFlow<MyChannelState> = _state.asStateFlow()

    private val _notes = MutableStateFlow<Map<String, NoteState>>(emptyMap())
    /** The AI's explanation per video id. */
    val notes: StateFlow<Map<String, NoteState>> = _notes.asStateFlow()

    private val _more = MutableStateFlow<Map<String, MoreState>>(emptyMap())
    /** "Make more like this" ideas per video id. */
    val more: StateFlow<Map<String, MoreState>> = _more.asStateFlow()

    private val _saved = MutableStateFlow<Set<String>>(emptySet())
    /** Titles of ideas saved from here, so the button can say Saved. */
    val saved: StateFlow<Set<String>> = _saved.asStateFlow()

    private var target: ConnectedAccount? = null
    private var uid: String? = null

    init {
        viewModelScope.launch {
            context.selected.collectLatest { accounts ->
                when (val pick = pickAccount(accounts)) {
                    Pick.NoAccount -> _state.value = MyChannelState.NoAccount
                    Pick.PickOne -> _state.value = MyChannelState.PickOne
                    Pick.Unsupported -> _state.value = MyChannelState.Unsupported
                    is Pick.Account -> load(pick.account, force = false)
                }
            }
        }
    }

    /** A refresh costs 2 YouTube quota units, so it only happens when asked for. */
    fun refresh() {
        val account = target ?: return
        viewModelScope.launch { load(account, force = true) }
    }

    private suspend fun load(account: ConnectedAccount, force: Boolean) {
        target = account
        _state.value = MyChannelState.Loading
        _notes.value = emptyMap()
        _more.value = emptyMap()
        val id = auth.user.first()?.uid ?: return
        uid = id
        _state.value = when (val result = research.channelVideos(id, account.id, force)) {
            is Fetched.Failed -> MyChannelState.Failed(result.error)
            is Fetched.Ok -> {
                val scored = scoreOutliers(result.value, System.currentTimeMillis())
                if (scored.isEmpty()) MyChannelState.NotEnough(result.value.size)
                else MyChannelState.Ready(winners(scored), weakest(scored), result.cachedAt, result.stale)
            }
        }
    }

    /** Why a winner did well ([winner] true) or what is not working in a weak video. One AI action, cached for 24 hours. */
    fun explain(item: ScoredVideo, winner: Boolean) {
        val account = target ?: return
        val id = uid ?: return
        val videoId = item.video.id
        if (_notes.value[videoId] != null && _notes.value[videoId] !is NoteState.Failed) return
        setNote(videoId, NoteState.Loading)
        viewModelScope.launch {
            val key = (if (winner) "rwhy_" else "rfix_") + videoId
            val now = System.currentTimeMillis()
            cache.get(id, key)?.takeIf { isFresh(it.cachedAt, DAY_MILLIS, now) }?.let { setNote(videoId, NoteState.Text(it.text)); return@launch }
            val facts = outlierFacts(item, research.ownAnalytics(account.id, videoId), now)
            when (val outcome = if (winner) ai.whyVideo(facts) else ai.fixVideo(facts)) {
                is AiOutcome.Ok -> { cache.put(id, key, outcome.value, now); setNote(videoId, NoteState.Text(outcome.value)) }
                is AiOutcome.Failed -> setNote(videoId, NoteState.Failed(outcome.errors))
            }
        }
    }

    /** "Make more like this": 4 follow-up ideas in the winner's pattern. One AI action, cached for 24 hours. */
    fun makeMore(item: ScoredVideo) {
        val account = target ?: return
        val id = uid ?: return
        val videoId = item.video.id
        if (_more.value[videoId] != null && _more.value[videoId] !is MoreState.Failed) return
        setMore(videoId, MoreState.Loading)
        viewModelScope.launch {
            val key = "rmore_$videoId"
            val now = System.currentTimeMillis()
            val cachedText = cache.get(id, key)?.takeIf { isFresh(it.cachedAt, DAY_MILLIS, now) }?.text
            var text = cachedText
            if (text == null) {
                text = when (val outcome = ai.moreLikeThis(outlierFacts(item, research.ownAnalytics(account.id, videoId), now), IDEA_COUNT)) {
                    is AiOutcome.Ok -> outcome.value
                    is AiOutcome.Failed -> { setMore(videoId, MoreState.Failed(outcome.errors)); return@launch }
                }
            }
            val ideas = parseDailyIdeas(text, IDEA_COUNT)
            // An unreadable reply is not kept, so trying again asks the AI again.
            if (ideas.isNotEmpty() && cachedText == null) cache.put(id, key, text, now)
            setMore(videoId, if (ideas.isEmpty()) MoreState.Failed(emptyList()) else MoreState.Ready(ideas))
        }
    }

    fun save(idea: ParsedIdea) {
        val id = uid ?: return
        viewModelScope.launch {
            actions.save(id, toIdea(idea))
            _saved.update { it + idea.title }
        }
    }

    /** Starts a project from the idea; [onStarted] gets the project id to open. */
    fun create(idea: ParsedIdea, onStarted: (Long) -> Unit) {
        val id = uid ?: return
        viewModelScope.launch { onStarted(actions.start(id, toIdea(idea)).projectId) }
    }

    private fun toIdea(idea: ParsedIdea): CreatorIdea {
        val now = System.currentTimeMillis()
        return CreatorIdea(title = idea.title, reason = idea.reason, score = idea.score, source = "research", accountKey = target?.key, day = dayKey(now), createdAt = now)
    }

    private fun setNote(videoId: String, note: NoteState) = _notes.update { it + (videoId to note) }

    private fun setMore(videoId: String, more: MoreState) = _more.update { it + (videoId to more) }

    private companion object {
        const val IDEA_COUNT = 4
    }
}
