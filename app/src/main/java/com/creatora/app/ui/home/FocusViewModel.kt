package com.creatora.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatora.app.domain.account.AdapterError
import com.creatora.app.domain.account.ConnectedAccount
import com.creatora.app.domain.account.PlatformSupport
import com.creatora.app.domain.ai.AiTasks
import com.creatora.app.domain.home.AccountContext
import com.creatora.app.domain.home.ChannelOverviewLoader
import com.creatora.app.domain.home.FocusTip
import com.creatora.app.domain.home.dayKey
import com.creatora.app.domain.home.focusFacts
import com.creatora.app.domain.home.parseFocus
import com.creatora.app.domain.model.AiError
import com.creatora.app.domain.model.AiOutcome
import com.creatora.app.domain.model.Project
import com.creatora.app.domain.model.VideoFormat
import com.creatora.app.domain.repository.AuthService
import com.creatora.app.domain.repository.CacheRepository
import com.creatora.app.domain.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface FocusState {
    data object NoAccount : FocusState
    /** "All accounts" is selected: today's focus is for one account at a time. */
    data object PickOne : FocusState
    data object Loading : FocusState
    data class Ready(val tip: FocusTip) : FocusState
    /** The user chose Later: hidden until tomorrow. */
    data object Later : FocusState
    /** Too few videos for an honest tip. */
    data object NotEnoughData : FocusState
    data class Unavailable(val error: AdapterError) : FocusState
    data class AiFailed(val errors: List<AiError>) : FocusState
}

/** "Aaj ka focus": one tip a day per account, grounded in the real numbers and cached (one AI action a day). */
@HiltViewModel
class FocusViewModel @Inject constructor(
    private val context: AccountContext,
    private val loader: ChannelOverviewLoader,
    private val ai: AiTasks,
    private val cache: CacheRepository,
    private val auth: AuthService,
    private val projects: ProjectRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<FocusState>(FocusState.Loading)
    val state: StateFlow<FocusState> = _state.asStateFlow()

    private var account: ConnectedAccount? = null
    private var tip: FocusTip? = null

    init {
        viewModelScope.launch { context.selected.collectLatest { load(it) } }
    }

    private suspend fun load(accounts: List<ConnectedAccount>) {
        account = accounts.singleOrNull()
        val target = account
        when {
            accounts.isEmpty() -> _state.value = FocusState.NoAccount
            target == null -> _state.value = FocusState.PickOne
            target.platform.support != PlatformSupport.FULL -> _state.value = FocusState.Unavailable(AdapterError.ComingSoon)
            else -> generate(target)
        }
    }

    private suspend fun generate(target: ConnectedAccount) {
        _state.value = FocusState.Loading
        val uid = auth.user.first()?.uid
        val day = dayKey(System.currentTimeMillis())
        val key = "focus_${target.key}_$day"
        if (uid != null && cache.get(uid, "later_$key") != null) {
            _state.value = FocusState.Later
            return
        }
        val cached = uid?.let { cache.get(it, key) }?.let { parseFocus(it.text) }
        if (cached != null) {
            tip = cached
            _state.value = FocusState.Ready(cached)
            return
        }
        val overview = loader.overview(target)
        overview.error?.let {
            _state.value = FocusState.Unavailable(it)
            return
        }
        val facts = focusFacts(overview.videos, System.currentTimeMillis())
        if (facts == null) {
            _state.value = FocusState.NotEnoughData
            return
        }
        when (val outcome = ai.todaysFocus(facts)) {
            is AiOutcome.Failed -> _state.value = FocusState.AiFailed(outcome.errors)
            is AiOutcome.Ok -> {
                val parsed = parseFocus(outcome.value)
                if (parsed == null) {
                    _state.value = FocusState.NotEnoughData
                } else {
                    uid?.let { cache.put(it, key, outcome.value, System.currentTimeMillis()) }
                    tip = parsed
                    _state.value = FocusState.Ready(parsed)
                }
            }
        }
    }

    /** "Later": hides today's tip until tomorrow. */
    fun later() {
        val target = account ?: return
        viewModelScope.launch {
            val uid = auth.user.first()?.uid
            uid?.let { cache.put(it, "later_focus_${target.key}_${dayKey(System.currentTimeMillis())}", "1", System.currentTimeMillis()) }
            _state.value = FocusState.Later
        }
    }

    /** "Fix it": starts a Create project with the tip as its brief and passes the project id to [onCreated]. */
    fun fixIt(onCreated: (Long) -> Unit) {
        val current = tip ?: return
        viewModelScope.launch {
            onCreated(projects.save(Project(title = current.tip.take(TITLE_LENGTH), format = VideoFormat.LONG_VIDEO, brief = current.tip)))
        }
    }

    private companion object {
        const val TITLE_LENGTH = 60
    }
}
