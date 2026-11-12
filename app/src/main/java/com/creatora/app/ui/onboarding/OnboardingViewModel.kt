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
import com.creatora.app.domain.account.GoalMetric
import com.creatora.app.domain.account.newGoal
import com.creatora.app.domain.account.ProfileSource
import com.creatora.app.domain.account.profileSamples
import com.creatora.app.domain.account.toAccount
import com.creatora.app.domain.repository.AuthService
import com.creatora.app.domain.repository.AuthUser
import com.creatora.app.domain.repository.BillingRepository
import com.creatora.app.domain.repository.ErrorReporter
import com.creatora.app.domain.repository.ConnectedAccountRepository
import com.creatora.app.domain.repository.GoalRepository
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
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OnboardingStep { LANGUAGE, WELCOME, CONNECT, ASK, GOAL, NOTIFICATIONS, PLAN }

data class OnboardingUiState(
    /** False until the saved language, step and sign-in have been read, so the screen never flashes the wrong step. */
    val loaded: Boolean = false,
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
    /** The channel whose "Is this you? Yes" could not be saved; the card offers Try again. */
    val saveFailed: ChannelPreview? = null,
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
    private val goals: GoalRepository,
    private val accounts: ConnectedAccountRepository,
    private val errors: ErrorReporter,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    private var user: AuthUser? = null

    /**
     * Runs [block] so that nothing it calls (Firestore, YouTube, the AI, Play) can crash the app. A failure is reported to
     * Crashlytics and handed to [onError] so the screen can show a message with Try again; cancellation is left alone.
     */
    private fun safely(onError: (Throwable) -> Unit = {}, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                errors.record(e)
                onError(e)
            }
        }
    }

    init {
        safely(onError = { _state.update { it.copy(loaded = true) } }) {
            // The saved choice wins; otherwise the device's language is preselected (and applied at once).
            val saved = settings.language.first()
            val language = saved ?: AppLanguage.deviceDefault()
            if (saved == null) settings.setLanguage(language)
            user = auth.user.first()
            // Resume where the user left off: signed in never goes back to Language or Welcome; signed out never skips sign-in.
            val savedStep = settings.onboardingStep.first()?.let { name -> OnboardingStep.entries.firstOrNull { it.name == name } }
            val step = when {
                user != null -> maxOf(savedStep?.ordinal ?: 0, OnboardingStep.CONNECT.ordinal).let { OnboardingStep.entries[it] }
                savedStep != null && savedStep.ordinal <= OnboardingStep.WELCOME.ordinal -> savedStep
                saved != null -> OnboardingStep.WELCOME
                else -> OnboardingStep.LANGUAGE
            }
            _state.update { it.copy(loaded = true, language = language, step = step, loadingChannels = user != null && step == OnboardingStep.CONNECT) }
            if (user != null && step == OnboardingStep.CONNECT) loadChannels()
        }
        safely { notifications.prefs.collect { prefs -> _state.update { it.copy(notificationPrefs = prefs) } } }
    }

    fun chooseLanguage(language: AppLanguage) {
        _state.update { it.copy(language = language) }
        safely { settings.setLanguage(language) }
    }

    fun next() = goTo(OnboardingStep.entries[(_state.value.step.ordinal + 1).coerceAtMost(OnboardingStep.entries.lastIndex)])

    /** Moves to [step] and remembers it, so closing the app (or a crash) resumes here. */
    private fun goTo(step: OnboardingStep) {
        _state.update { it.copy(step = step) }
        safely { settings.setOnboardingStep(step.name) }
    }

    fun signIn() {
        if (_state.value.signingIn) return
        _state.update { it.copy(signingIn = true, signInNotConfigured = false, signInFailed = false) }
        safely(onError = { _state.update { it.copy(signingIn = false, signInFailed = true) } }) {
            when (val outcome = auth.signInWithGoogle()) {
                is SignInOutcome.Ok -> {
                    user = outcome.user
                    val language = _state.value.language
                    runCatching {
                        users.createIfMissing(outcome.user, language)
                        users.setLanguage(outcome.user.uid, language)
                    }.onFailure { errors.record(it) }
                    _state.update { it.copy(signingIn = false, loadingChannels = true) }
                    goTo(OnboardingStep.CONNECT)
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
        safely(onError = { _state.update { it.copy(loadingChannels = false, channelsProblem = ConnectProblem.NETWORK) } }) {
            when (val result = connector.ownChannels()) {
                is AdapterOutcome.Ok -> _state.update { it.copy(loadingChannels = false, channels = result.value) }
                is AdapterOutcome.Failed -> _state.update { it.copy(loadingChannels = false, channelsProblem = result.error.toProblem()) }
            }
        }
    }

    /** "Is this you? ✓" for one of the account's channels. A save that fails (for example no permission) shows Try again, never a crash. */
    fun confirmChannel(preview: ChannelPreview) {
        val uid = user?.uid ?: return
        _state.update { it.copy(saveFailed = null) }
        safely(onError = { _state.update { it.copy(saveFailed = preview) } }) {
            when (val outcome = connector.connect(uid, preview.toAccount())) {
                ConnectOutcome.Connected, ConnectOutcome.AlreadyConnected -> {
                    _state.update {
                        it.copy(
                            channels = it.channels.filterNot { c -> c.id == preview.id },
                            lookup = LookupUi(),
                            connectedTitles = it.connectedTitles + preview.title,
                        )
                    }
                    // Niche, formats and language are worked out from the channel itself, with no questions. A failure here is not shown.
                    safely { profiles.deriveFor(uid) }
                }
                is ConnectOutcome.PlanLimit -> _state.update { it.copy(lookup = LookupUi(planLimitMax = outcome.max)) }
            }
        }
    }

    /** Looks up a pasted link, id or handle for the picked platform. */
    fun lookUp(input: String, platform: ConnectPlatform?) {
        _state.update { it.copy(lookup = LookupUi(busy = true)) }
        safely(onError = { _state.update { it.copy(lookup = LookupUi(problem = ConnectProblem.NETWORK)) } }) {
            val (result, saved) = connector.lookupForPanel(user?.uid, input, platform)
            _state.update { it.copy(lookup = result, connectedTitles = it.connectedTitles + listOfNotNull(saved?.title)) }
        }
    }

    fun dismissPasted() = _state.update { it.copy(lookup = LookupUi()) }

    /** "Login with …": Google finds the YouTube channels again; other platforms are not connectable yet. */
    fun login(platform: ConnectPlatform) {
        if (platform == ConnectPlatform.YOUTUBE) loadChannels() else _state.update { it.copy(lookup = LookupUi(comingSoon = platform)) }
    }

    /** Leaves the connect step. The one question is only asked when nothing is connected. */
    fun finishConnect() {
        goTo(if (_state.value.connectedTitles.isEmpty()) OnboardingStep.ASK else OnboardingStep.GOAL)
    }

    fun answerWhatYouMake(answer: String) {
        val text = answer.trim()
        val uid = user?.uid
        if (text.isNotEmpty() && uid != null) {
            safely { users.setCreatorProfile(uid, CreatorProfile(niche = text, source = ProfileSource.ASKED)) }
        }
        goTo(OnboardingStep.GOAL)
    }

    /** Saves the 30-day goal (the aim the AI is steered by) and moves on. A target that is not a positive number saves nothing. */
    fun saveGoal(metric: GoalMetric, target: Long, desire: String, uploadsPerWeek: Int?) {
        val uid = user?.uid
        if (uid == null) {
            goTo(OnboardingStep.NOTIFICATIONS)
            return
        }
        safely(onError = { goTo(OnboardingStep.NOTIFICATIONS) }) {
            val goal = newGoal(metric, target, desire, uploadsPerWeek, accounts.observe(uid).first(), System.currentTimeMillis())
            if (goal != null) goals.save(uid, goal)
            goTo(OnboardingStep.NOTIFICATIONS)
        }
    }

    fun skipGoal() = goTo(OnboardingStep.NOTIFICATIONS)

    fun setNotification(category: NotificationCategory, on: Boolean) {
        safely { notifications.setEnabled(category, on) }
    }

    fun setMilestone(views: Long) {
        safely { notifications.setMilestone(views) }
    }

    /** "Free mein continue karo": finishes onboarding on the Free plan. */
    fun continueFree() {
        safely { settings.setOnboardingDone(true) }
    }

    fun tryPro() {
        safely(onError = { _state.update { it.copy(proUnavailable = true) } }) {
            billing.refresh()
            if (billing.launchUpgrade() == LaunchOutcome.UNAVAILABLE) _state.update { it.copy(proUnavailable = true) }
        }
    }
}
