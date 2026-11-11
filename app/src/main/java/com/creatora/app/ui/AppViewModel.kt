package com.creatora.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatora.app.domain.account.AppLanguage
import com.creatora.app.domain.model.ThemeMode
import com.creatora.app.domain.repository.AuthService
import com.creatora.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Which part of the app to show: first run, or the app itself. */
enum class StartState { ONBOARDING, HOME }

/** Everything needed before the first screen is drawn. */
data class AppSession(val themeMode: ThemeMode, val language: AppLanguage?, val start: StartState)

/** App-wide state needed before any screen is drawn: theme, language and whether onboarding is finished. */
@HiltViewModel
class AppViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    auth: AuthService,
) : ViewModel() {

    /** `null` until the saved setting has been read, so the app never flashes the wrong theme. */
    val themeMode: StateFlow<ThemeMode?> = settingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)

    /** `null` while the splash screen is showing. Signed in and onboarded: Home. Anything else: onboarding. */
    val session: StateFlow<AppSession?> = combine(
        settingsRepository.themeMode,
        settingsRepository.language,
        auth.user,
        settingsRepository.onboardingDone,
    ) { theme, language, user, done ->
        AppSession(theme, language, if (user != null && done) StartState.HOME else StartState.ONBOARDING)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)
}
