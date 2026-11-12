package com.creatora.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatora.app.domain.account.AppLanguage
import com.creatora.app.domain.account.ConnectedAccount
import com.creatora.app.domain.home.AccountContext
import com.creatora.app.domain.home.AccountSelection
import com.creatora.app.domain.home.firstName
import com.creatora.app.domain.model.Project
import com.creatora.app.domain.repository.ProjectRepository
import com.creatora.app.domain.repository.SettingsRepository
import com.creatora.app.domain.repository.UpcomingPost
import com.creatora.app.domain.repository.UpcomingPostRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    /** First word of the Google name; null when there is none. */
    val firstName: String? = null,
    val photoUrl: String? = null,
    val language: AppLanguage = AppLanguage.deviceDefault(),
    val accounts: List<ConnectedAccount> = emptyList(),
    val selection: AccountSelection = AccountSelection.All,
    /** The unfinished Create project worked on most recently. */
    val continueProject: Project? = null,
    /** Scheduled posts still to come for the selected accounts, soonest first. */
    val upcoming: List<UpcomingPost> = emptyList(),
) {
    val hasAccounts: Boolean get() = accounts.isNotEmpty()
}

/** Home's header, account switcher, Continue and Upcoming. The other cards have their own ViewModels. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val context: AccountContext,
    projects: ProjectRepository,
    upcoming: UpcomingPostRepository,
    settings: SettingsRepository,
) : ViewModel() {

    private val upcomingForUser: Flow<List<UpcomingPost>> =
        context.user.flatMapLatest { user -> if (user == null) flowOf(emptyList()) else upcoming.observe(user.uid) }

    private val continueProject: Flow<Project?> = projects.observeAll()
        .map { list -> list.filter { it.isInProgress }.maxByOrNull { it.updatedAt } }

    val state: StateFlow<HomeUiState> = combine(
        combine(context.user, settings.language) { user, language -> Triple(user?.name?.let(::firstName), user?.photoUrl, language ?: AppLanguage.deviceDefault()) },
        combine(context.accounts, context.selection, ::Pair),
        continueProject,
        upcomingForUser,
    ) { (name, photo, language), (accounts, selection), project, posts ->
        val keys = com.creatora.app.domain.home.selectedAccounts(accounts, selection).map { it.key }.toSet()
        val now = System.currentTimeMillis()
        HomeUiState(
            firstName = name,
            // The Google photo, or the connected channel's own picture when the Google account has none.
            photoUrl = photo ?: accounts.firstNotNullOfOrNull { it.photoUrl },
            language = language,
            accounts = accounts,
            selection = selection,
            continueProject = project,
            upcoming = posts.filter { it.scheduledAt >= now && (it.accountKey == null || it.accountKey in keys) }.sortedBy { it.scheduledAt }.take(MAX_UPCOMING),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun select(selection: AccountSelection) {
        viewModelScope.launch { context.select(selection) }
    }

    private companion object {
        const val MAX_UPCOMING = 5
    }
}
