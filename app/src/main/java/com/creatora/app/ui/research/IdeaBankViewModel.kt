package com.creatora.app.ui.research

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatora.app.domain.account.ConnectedAccount
import com.creatora.app.domain.home.AccountContext
import com.creatora.app.domain.home.CreatorIdea
import com.creatora.app.domain.home.IdeaStatus
import com.creatora.app.domain.repository.AuthService
import com.creatora.app.domain.repository.CreatorIdeaRepository
import com.creatora.app.domain.repository.ProjectRepository
import com.creatora.app.domain.research.BankFilter
import com.creatora.app.domain.research.IdeaActions
import com.creatora.app.domain.research.StartedProject
import com.creatora.app.domain.research.effectiveStatus
import com.creatora.app.domain.research.inBank
import com.creatora.app.domain.research.matches
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** An idea with its status as the bank shows it, and the account it is for (null: not tied to one). */
data class BankItem(val idea: CreatorIdea, val status: IdeaStatus, val account: ConnectedAccount?)

data class IdeaBankUi(
    val items: List<BankItem> = emptyList(),
    val filter: BankFilter = BankFilter.ALL,
    /** Null: every account. */
    val accountKey: String? = null,
    val accounts: List<ConnectedAccount> = emptyList(),
    val loaded: Boolean = false,
)

/**
 * The idea bank: every saved idea from Home, Research, Trends and competitor concepts, with status New, Planned or Made.
 * An idea is Made once the project started from it is marked ready (that is written back when seen).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class IdeaBankViewModel @Inject constructor(
    private val auth: AuthService,
    private val ideas: CreatorIdeaRepository,
    private val projects: ProjectRepository,
    private val context: AccountContext,
    private val actions: IdeaActions,
) : ViewModel() {

    private val filter = MutableStateFlow(BankFilter.ALL)
    private val accountKey = MutableStateFlow<String?>(null)

    val ui: StateFlow<IdeaBankUi> = combine(
        auth.user.flatMapLatest { user -> if (user == null) flowOf(emptyList()) else ideas.observe(user.uid) },
        projects.observeAll(),
        context.accounts,
        filter,
        accountKey,
    ) { all, projectList, accounts, chosenFilter, chosenAccount ->
        val ready = projectList.associate { it.id to it.isReady }
        val byKey = accounts.associateBy { it.key }
        val items = all.map { idea -> BankItem(idea, effectiveStatus(idea, idea.projectId?.let { ready[it] }), idea.accountKey?.let { byKey[it] }) }
            .filter { inBank(it.status) && chosenFilter.matches(it.status) && (chosenAccount == null || it.idea.accountKey == chosenAccount) }
        IdeaBankUi(items, chosenFilter, chosenAccount, accounts, loaded = true)
    }.onEach { state -> state.items.filter { it.status == IdeaStatus.MADE && it.idea.status != IdeaStatus.MADE }.forEach { markMade(it.idea) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), IdeaBankUi())

    fun setFilter(value: BankFilter) { filter.value = value }

    fun setAccount(key: String?) { accountKey.value = key }

    private fun markMade(idea: CreatorIdea) = setStatus(idea, IdeaStatus.MADE)

    fun markPlanned(idea: CreatorIdea) = setStatus(idea, IdeaStatus.PLANNED)

    /** Removes the idea from the bank (it is kept as dismissed). */
    fun remove(idea: CreatorIdea) = setStatus(idea, IdeaStatus.DISMISSED)

    private fun setStatus(idea: CreatorIdea, status: IdeaStatus) {
        viewModelScope.launch {
            val id = auth.user.first()?.uid ?: return@launch
            ideas.setStatus(id, idea.id, status, idea.projectId)
        }
    }

    /** Starts a project from the idea, marks it Planned and links them; [onStarted] gets the project to open. */
    fun create(idea: CreatorIdea, onStarted: (StartedProject) -> Unit) {
        viewModelScope.launch {
            val id = auth.user.first()?.uid ?: return@launch
            onStarted(actions.start(id, idea))
        }
    }

    private companion object {
        const val STOP_MILLIS = 5_000L
    }
}
