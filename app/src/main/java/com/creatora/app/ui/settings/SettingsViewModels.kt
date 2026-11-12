package com.creatora.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatora.app.domain.account.AccountConnector
import com.creatora.app.domain.account.AppLanguage
import com.creatora.app.domain.account.ChannelPreview
import com.creatora.app.domain.account.ConnectOutcome
import com.creatora.app.domain.account.ConnectedAccount
import com.creatora.app.domain.account.CreatorProfile
import com.creatora.app.domain.account.CreatorProfileService
import com.creatora.app.domain.account.DeriveResult
import com.creatora.app.domain.account.NotificationPrefs
import com.creatora.app.domain.account.Plan
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.account.UsageStatus
import com.creatora.app.domain.account.monthKey
import com.creatora.app.domain.account.toAccount
import com.creatora.app.domain.account.usageStatus
import com.creatora.app.domain.repository.AiHistoryEntry
import com.creatora.app.domain.repository.AiHistoryRepository
import com.creatora.app.domain.repository.AiTone
import com.creatora.app.domain.repository.AuthService
import com.creatora.app.domain.repository.AuthUser
import com.creatora.app.domain.repository.BillingRepository
import com.creatora.app.domain.repository.BillingState
import com.creatora.app.domain.repository.ConnectedAccountRepository
import com.creatora.app.domain.repository.DeleteOutcome
import com.creatora.app.domain.repository.LaunchOutcome
import com.creatora.app.domain.repository.NotificationPrefsRepository
import com.creatora.app.domain.repository.SettingsRepository
import com.creatora.app.domain.repository.UsageRepository
import com.creatora.app.domain.repository.UserDoc
import com.creatora.app.domain.repository.UserRepository
import com.creatora.app.ui.accounts.LookupUi
import com.creatora.app.ui.accounts.lookupForPanel
import com.creatora.app.ui.accounts.toLookup
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The signed-in user's cloud document, or null while signed out or not loaded. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun userDocFlow(auth: AuthService, users: UserRepository): Flow<UserDoc?> =
    auth.user.flatMapLatest { user -> if (user == null) flowOf(null) else users.observe(user.uid) }

// ---------------------------------------------------------------- Account

data class AccountUiState(
    val user: AuthUser? = null,
    val plan: Plan = Plan.FREE,
    val confirmDelete: Boolean = false,
    val working: Boolean = false,
    val needsRecentSignIn: Boolean = false,
    val deleteFailed: Boolean = false,
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val auth: AuthService,
    private val users: UserRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val local = MutableStateFlow(AccountUiState())

    val state: StateFlow<AccountUiState> = combine(auth.user, userDocFlow(auth, users), local) { user, doc, local ->
        local.copy(user = user, plan = doc?.plan ?: Plan.FREE)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountUiState())

    fun signOut() {
        viewModelScope.launch {
            // Onboarding must run again for the next sign-in.
            settings.setOnboardingDone(false)
            auth.signOut()
        }
    }

    fun askDelete(show: Boolean) = local.update { it.copy(confirmDelete = show, needsRecentSignIn = false, deleteFailed = false) }

    /** Deletes the cloud data first, then the sign-in. Needs a recent sign-in; if Google asks for one, the user signs out and in again. */
    fun deleteAccount() {
        val uid = state.value.user?.uid ?: return
        local.update { it.copy(working = true, confirmDelete = false) }
        viewModelScope.launch {
            val outcome = runCatching { users.deleteAll(uid) }.map { auth.deleteAccount() }.getOrDefault(DeleteOutcome.FAILED)
            when (outcome) {
                DeleteOutcome.DELETED -> {
                    settings.setOnboardingDone(false)
                    auth.signOut()
                    local.update { it.copy(working = false) }
                }
                DeleteOutcome.NEEDS_RECENT_SIGN_IN -> local.update { it.copy(working = false, needsRecentSignIn = true) }
                DeleteOutcome.FAILED -> local.update { it.copy(working = false, deleteFailed = true) }
            }
        }
    }
}

// ---------------------------------------------------------------- Connected accounts

data class ConnectedAccountsUiState(
    val accounts: List<ConnectedAccount> = emptyList(),
    val plan: Plan = Plan.FREE,
    val lookup: LookupUi = LookupUi(),
    val loadingOwn: Boolean = false,
    val ownChannels: List<ChannelPreview> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ConnectedAccountsViewModel @Inject constructor(
    private val auth: AuthService,
    users: UserRepository,
    private val accounts: ConnectedAccountRepository,
    private val connector: AccountConnector,
    private val profiles: CreatorProfileService,
) : ViewModel() {
    private val local = MutableStateFlow(ConnectedAccountsUiState())

    val state: StateFlow<ConnectedAccountsUiState> = combine(
        auth.user.flatMapLatest { user -> if (user == null) flowOf(emptyList()) else accounts.observe(user.uid) },
        userDocFlow(auth, users),
        local,
    ) { list, doc, local -> local.copy(accounts = list, plan = doc?.plan ?: Plan.FREE) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectedAccountsUiState())

    fun lookUp(input: String, platform: ConnectPlatform) {
        local.update { it.copy(lookup = LookupUi(busy = true)) }
        viewModelScope.launch {
            val uid = auth.user.first()?.uid
            val (result, _) = connector.lookupForPanel(uid, input, platform)
            local.update { it.copy(lookup = result) }
        }
    }

    fun confirm(preview: ChannelPreview) {
        viewModelScope.launch {
            val uid = auth.user.first()?.uid ?: return@launch
            when (val outcome = connector.connect(uid, preview.toAccount())) {
                is ConnectOutcome.PlanLimit -> local.update { it.copy(lookup = LookupUi(planLimitMax = outcome.max)) }
                else -> {
                    local.update { it.copy(lookup = LookupUi(), ownChannels = it.ownChannels.filterNot { c -> c.id == preview.id }) }
                    profiles.deriveFor(uid)
                }
            }
        }
    }

    /** Fetches the channels on the signed-in Google account, for accounts the user has not connected yet. */
    fun loadOwn() {
        local.update { it.copy(loadingOwn = true) }
        viewModelScope.launch {
            val found = (connector.ownChannels() as? com.creatora.app.domain.account.AdapterOutcome.Ok)?.value.orEmpty()
            local.update { it.copy(loadingOwn = false, ownChannels = found) }
        }
    }

    fun dismissPasted() = local.update { it.copy(lookup = LookupUi()) }

    /** "Login with …": Google finds the YouTube channels again; the other platforms show their own sign-in. */
    fun login(platform: ConnectPlatform) {
        if (platform == ConnectPlatform.YOUTUBE) return loadOwn()
        local.update { it.copy(lookup = LookupUi(busy = true)) }
        viewModelScope.launch {
            val outcome = try {
                connector.login(platform)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                com.creatora.app.domain.platform.LoginOutcome.Failed(e.message ?: "Sign-in failed")
            }
            local.update { it.copy(lookup = outcome.toLookup(), ownChannels = it.ownChannels + ((outcome as? com.creatora.app.domain.platform.LoginOutcome.Signed)?.accounts.orEmpty())) }
        }
    }

    fun remove(account: ConnectedAccount) {
        viewModelScope.launch {
            val uid = auth.user.first()?.uid ?: return@launch
            accounts.remove(uid, account.key)
        }
    }
}

// ---------------------------------------------------------------- Notifications

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(private val repository: NotificationPrefsRepository) : ViewModel() {
    val prefs: StateFlow<NotificationPrefs> = repository.prefs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationPrefs())

    fun toggle(category: com.creatora.app.domain.account.NotificationCategory, on: Boolean) {
        viewModelScope.launch { repository.setEnabled(category, on) }
    }

    fun setMilestone(views: Long) {
        viewModelScope.launch { repository.setMilestone(views) }
    }
}

// ---------------------------------------------------------------- Creatora AI

data class AiSettingsUiState(
    val tone: AiTone = AiTone.FRIENDLY,
    val personalization: Boolean = true,
    val profile: CreatorProfile? = null,
    val language: AppLanguage? = null,
    val history: List<AiHistoryEntry> = emptyList(),
    val refreshing: Boolean = false,
    val refreshMessage: DeriveResult? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val auth: AuthService,
    users: UserRepository,
    private val history: AiHistoryRepository,
    private val profiles: CreatorProfileService,
) : ViewModel() {
    private val local = MutableStateFlow(AiSettingsUiState())

    private val historyFlow: Flow<List<AiHistoryEntry>> =
        auth.user.flatMapLatest { user -> if (user == null) flowOf(emptyList()) else history.observe(user.uid) }

    val state: StateFlow<AiSettingsUiState> = combine(
        combine(settings.aiTone, settings.personalization, settings.language) { tone, personal, language -> Triple(tone, personal, language) },
        userDocFlow(auth, users),
        historyFlow,
        local,
    ) { (tone, personal, language), doc, entries, local ->
        local.copy(tone = tone, personalization = personal, language = language, profile = doc?.creatorProfile, history = entries)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiSettingsUiState())

    fun setTone(tone: AiTone) {
        viewModelScope.launch { settings.setAiTone(tone) }
    }

    fun setPersonalization(on: Boolean) {
        viewModelScope.launch { settings.setPersonalization(on) }
    }

    fun refreshProfile() {
        local.update { it.copy(refreshing = true, refreshMessage = null) }
        viewModelScope.launch {
            val id = auth.user.first()?.uid
            val result = if (id == null) DeriveResult.NothingConnected else profiles.deriveFor(id)
            local.update { it.copy(refreshing = false, refreshMessage = result) }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            val uid = auth.user.first()?.uid ?: return@launch
            history.clear(uid)
        }
    }
}

// ---------------------------------------------------------------- Billing and usage

data class BillingUiState(
    val plan: Plan = Plan.FREE,
    val usage: UsageStatus = usageStatus(0, Plan.FREE.aiLimit),
    val billing: BillingState = BillingState(),
    val upgradeUnavailable: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BillingViewModel @Inject constructor(
    private val auth: AuthService,
    users: UserRepository,
    usage: UsageRepository,
    private val billing: BillingRepository,
) : ViewModel() {
    private val local = MutableStateFlow(false)

    init {
        viewModelScope.launch { billing.refresh() }
    }

    private val used: Flow<Int> =
        auth.user.flatMapLatest { user -> if (user == null) flowOf(0) else usage.observe(user.uid, monthKey(System.currentTimeMillis())) }

    val state: StateFlow<BillingUiState> = combine(userDocFlow(auth, users), used, billing.state, local) { doc, count, billingState, unavailable ->
        val plan = if (billingState.hasPro) Plan.PRO else doc?.plan ?: Plan.FREE
        BillingUiState(plan, usageStatus(count, plan.aiLimit), billingState, unavailable)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BillingUiState())

    fun upgrade() {
        viewModelScope.launch {
            local.value = billing.launchUpgrade() == LaunchOutcome.UNAVAILABLE
        }
    }
}

// ---------------------------------------------------------------- Language

@HiltViewModel
class LanguageViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val auth: AuthService,
    private val users: UserRepository,
) : ViewModel() {
    val language: StateFlow<AppLanguage?> = settings.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun choose(language: AppLanguage) {
        viewModelScope.launch {
            settings.setLanguage(language)
            auth.user.first()?.let { runCatching { users.setLanguage(it.uid, language) } }
        }
    }
}
