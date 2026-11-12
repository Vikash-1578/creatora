package com.creatora.app.ui.research

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatora.app.domain.account.AdapterError
import com.creatora.app.domain.ai.AiTasks
import com.creatora.app.domain.home.AccountContext
import com.creatora.app.domain.home.isFresh
import com.creatora.app.domain.model.AiError
import com.creatora.app.domain.model.AiOutcome
import com.creatora.app.domain.repository.AuthService
import com.creatora.app.domain.repository.CacheRepository
import com.creatora.app.domain.repository.SearchLogRepository
import com.creatora.app.domain.research.Fetched
import com.creatora.app.domain.research.KeywordEstimate
import com.creatora.app.domain.research.ResearchRepository
import com.creatora.app.domain.research.keywordEstimate
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface KeywordState {
    data object Idle : KeywordState
    data object Loading : KeywordState
    data class Failed(val error: AdapterError) : KeywordState
    /** YouTube returned too few results to estimate anything. */
    data class NotEnough(val results: Int) : KeywordState
    data class Ready(val query: String, val estimate: KeywordEstimate, val cachedAt: Long, val fromCache: Boolean, val stale: Boolean) : KeywordState
}

sealed interface QuestionsState {
    data object Idle : QuestionsState
    data object Loading : QuestionsState
    data class Ready(val questions: List<String>) : QuestionsState
    data class Failed(val errors: List<AiError>) : QuestionsState
}

private const val HALF_DAY_MILLIS = 12L * 60 * 60 * 1000

/**
 * Keyword search. A search costs about 102 YouTube quota units, so it only runs when the user submits (never on open),
 * and the result is cached for 12 hours. The numbers are an estimate from the top results, not search volume.
 */
@HiltViewModel
class KeywordsViewModel @Inject constructor(
    private val auth: AuthService,
    private val context: AccountContext,
    private val research: ResearchRepository,
    private val searchLog: SearchLogRepository,
    private val ai: AiTasks,
    private val cache: CacheRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<KeywordState>(KeywordState.Idle)
    val state: StateFlow<KeywordState> = _state.asStateFlow()

    private val _questions = MutableStateFlow<QuestionsState>(QuestionsState.Idle)
    val questions: StateFlow<QuestionsState> = _questions.asStateFlow()

    /** [force] is the Refresh button: it skips the cache and is not logged as a new search. */
    fun search(text: String, force: Boolean = false) {
        val query = text.trim()
        if (query.isEmpty() || _state.value is KeywordState.Loading) return
        _state.value = KeywordState.Loading
        _questions.value = QuestionsState.Idle
        viewModelScope.launch {
            val id = auth.user.first()?.uid ?: return@launch
            if (!force) searchLog.record(id, query, System.currentTimeMillis())
            val subscribers = context.selected.first().singleOrNull()?.subscribers
            _state.value = when (val result = research.keyword(id, query, force)) {
                is Fetched.Failed -> KeywordState.Failed(result.error)
                is Fetched.Ok -> keywordEstimate(query, result.value, subscribers, System.currentTimeMillis())
                    ?.let { KeywordState.Ready(query, it, result.cachedAt, result.fromCache, result.stale) }
                    ?: KeywordState.NotEnough(result.value.videos.size)
            }
        }
    }

    /** Questions people might ask: AI suggestions (one AI action, cached for 12 hours). */
    fun suggestQuestions() {
        val ready = _state.value as? KeywordState.Ready ?: return
        if (_questions.value is QuestionsState.Loading) return
        _questions.value = QuestionsState.Loading
        viewModelScope.launch {
            val id = auth.user.first()?.uid ?: return@launch
            val key = "rquestions_${ready.query.lowercase().hashCode()}"
            val now = System.currentTimeMillis()
            val cached = cache.get(id, key)?.takeIf { isFresh(it.cachedAt, HALF_DAY_MILLIS, now) }?.text?.lines()?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }
            if (cached != null) {
                _questions.value = QuestionsState.Ready(cached)
                return@launch
            }
            _questions.value = when (val outcome = ai.keywordQuestions(ready.query)) {
                is AiOutcome.Failed -> QuestionsState.Failed(outcome.errors)
                is AiOutcome.Ok -> {
                    if (outcome.value.isEmpty()) QuestionsState.Failed(emptyList())
                    else { cache.put(id, key, outcome.value.joinToString("\n"), now); QuestionsState.Ready(outcome.value) }
                }
            }
        }
    }
}
