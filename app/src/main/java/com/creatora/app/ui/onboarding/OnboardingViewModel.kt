package com.creatora.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatora.app.domain.account.AccountConnector
import com.creatora.app.domain.account.AdapterOutcome
import com.creatora.app.domain.account.AppLanguage
import com.creatora.app.domain.account.ChannelPreview
import com.creatora.app.domain.account.ConnectOutcome
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.account.CreatorProfile
import com.creatora.app.domain.account.NotificationCategory
import com.creatora.app.domain.account.NotificationPrefs
import com.creatora.app.domain.account.CreatorProfileService
import com.creatora.app.domain.account.ProfileSource
import com.creatora.app.domain.account.profileSamples
import com.creatora.app.domain.account.toAccount
import com.creatora.app.domain.repository.AuthService
import com.creatora.app.domain.repository.AuthUser
import com.creatora.app.domain.repository.BillingRepository
import com.creatora.app.domain.repository.LaunchOutcome
import com.creatora.app.domain.repository.NotificationPrefsRepository
import com.creatora.app.domain.repository.SettingsRepository
import com.creatora.app.domain.repository.SignInOutcome
import com.creatora.app.domain.repository.UserRepository
import com.creatora.app.ui.accounts.ConnectProblem
import com.creatora.app.ui.accounts.LookupUi
import com.creatora.app.ui.accounts.lookupForPanel
import com.creatora.app.ui.accounts.toProblem
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OnboardingStep { LANGUAGE, WELCOME, CONNECT, ASK, NOTIFICATIONS, PLAN }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.LANGUAGE,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val signingIn: Boolean = false,
    val signInNotConfigured: Boolean = false,
    val signInFailed: Boolean = false,
    /** True while YouTube channels are being fetched after sign-in. */
    val loadingChannels: Boolean = false,
    /** Channels of the Google account, waiting for "Is this you?". */
    val channels: List<ChannelPreview> = emptyList(),
    /** Accounts confirmed so far (titles), shown as done. */
    val connectedTitles: List<String> = emptyList(),
    /** Why fetching the account's own channels failed. */
    val channelsProblem: ConnectProblem? = null,
    /** The "paste a link or id" panel. */
    val lookup: LookupUi = LookupUi(),
    val notificationPrefs: NotificationPrefs = NotificationPrefs(),
    val proUnavailable: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val auth: AuthService,
    private val users: UserRepository,
    private val connector: AccountConnector,
    private val profiles: CreatorProfileService,
    private val notifications: NotificationPrefsRepository,
    private val billing: BillingRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    private var user: AuthUser? = null

    init {
        viewModelScope.launch {
            // The saved choice wins; otherwise the device's language is preselected (and applied at once).
            val saved = settings.language.first()
            val device = Locale.getDefault()
            val language = saved ?: AppLanguage.forDevice(device.language, device.script)
            _state.update { it.copy(language = language) }
            if (saved == null) settings.setLanguage(language)
            // Already signed in (for example the app was closed mid-onboarding): carry on from the connect step.
            user = auth.user.first()
            if (user != null) {
                _state.update { it.copy(step = OnboardingStep.CONNECT, loadingChannels = true) }
                loadChannels()
            }
        }
        viewModelScope.launch { notifications.prefs.collect { prefs -> _state.update { it.copy(notificationPrefs = prefs) } } }
    }

    fun chooseLanguage(language: AppLanguage) {
        _state.update { it.copy(language = language) }
        viewModelScope.launch { settings.setLanguage(language) }
    }

    fun next() = _state.update { it.copy(step = OnboardingStep.entries[(it.step.ordinal + 1).coerceAtMost(OnboardingStep.entries.lastIndex)]) }

    private fun goTo(step: OnboardingStep) = _state.update { it.copy(step = step) }

    fun signIn() {
        if (_state.value.signingIn) return
        _state.update { it.copy(signingIn = true, signInNotConfigured = false, signInFailed = false) }
        viewModelScope.launch {
            when (val outcome = auth.signInWithGoogle()) {
                is SignInOutcome.Ok -> {
                    user = outcome.user
                    val language = _state.value.language
                    runCatching {
                        users.createIfMissing(outcome.user, language)
                        users.setLanguage(outcome.user.uid, language)
                    }
                    _state.update { it.copy(signingIn = false, step = OnboardingStep.CONNECT, loadingChannels = true) }
                    loadChannels()
                }
                SignInOutcome.NotConfigured -> _state.update { it.copy(signingIn = false, signInNotConfigured = true) }
                SignInOutcome.Failed -> _state.update { it.copy(signingIn = false, signInFailed = true) }
                SignInOutcome.Cancelled -> _state.update { it.copy(signingIn = false) }
            }
        }
    }

    /** Asks Google for YouTube access and fetches every channel on the account. */
    fun loadChannels() {
        _state.update { it.copy(loadingChannels = true, channelsProblem = null) }
        viewModelScope.launch {
            when (val result = connector.ownChannels()) {
                is AdapterOutcome.Ok -> _state.update { it.copy(loadingChannels = false, channels = result.value) }
                is AdapterOutcome.Failed -> _state.update { it.copy(loadingChannels = false, channelsProblem = result.error.toProblem()) }
            }
        }
    }

    /** "Is this you? ✓" for one of the account's channels. */
    fun confirmChannel(preview: ChannelPreview) {
        val uid = user?.uid ?: return
        viewModelScope.launch {
            when (val outcome = connector.connect(uid, preview.toAccount())) {
                ConnectOutcome.Connected, ConnectOutcome.AlreadyConnected -> {
                    _state.update {
                        it.copy(
                            channels = it.channels.filterNot { c -> c.id == preview.id },
                            lookup = LookupUi(),
                            connectedTitles = it.connectedTitles + preview.title,
                        )
                    }
                    // Niche, formats and language are worked out from the channel itself, with no questions.
                    viewModelScope.launch { profiles.deriveFor(uid) }
                }
                is ConnectOutcome.PlanLimit -> _state.update { it.copy(lookup = LookupUi(planLimitMax = outcome.max)) }
            }
        }
    }

    /** Looks up a pasted link, id or handle for the picked platform. */
    fun lookUp(input: String, platform: ConnectPlatform?) {
        _state.update { it.copy(lookup = LookupUi(busy = true)) }
        viewModelScope.launch {
            val (result, saved) = connector.lookupForPanel(user?.uid, input, platform)
            _state.update { it.copy(lookup = result, connectedTitles = it.connectedTitles + listOfNotNull(saved?.title)) }
        }
    }

    fun dismissPasted() = _state.update { it.copy(lookup = LookupUi()) }

    /** Leaves the connect step. The one question is only asked when nothing is connected. */
    fun finishConnect() {
        goTo(if (_state.value.connectedTitles.isEmpty()) OnboardingStep.ASK else OnboardingStep.NOTIFICATIONS)
    }

    fun answerWhatYouMake(answer: String) {
        val text = answer.trim()
        val uid = user?.uid
        if (text.isNotEmpty() && uid != null) {
            viewModelScope.launch {
                runCatching { users.setCreatorProfile(uid, CreatorProfile(niche = text, source = ProfileSource.ASKED)) }
            }
        }
        goTo(OnboardingStep.NOTIFICATIONS)
    }

    fun setNotification(category: NotificationCategory, on: Boolean) {
        viewModelScope.launch { notifications.setEnabled(category, on) }
    }

    fun setMilestone(views: Long) {
        viewModelScope.launch { notifications.setMilestone(views) }
    }

    /** "Free mein continue karo": finishes onboarding on the Free plan. */
    fun continueFree() {
        viewModelScope.launch { settings.setOnboardingDone(true) }
    }

    fun tryPro() {
        viewModelScope.launch {
            billing.refresh()
            if (billing.launchUpgrade() == LaunchOutcome.UNAVAILABLE) _state.update { it.copy(proUnavailable = true) }
        }
    }
}
