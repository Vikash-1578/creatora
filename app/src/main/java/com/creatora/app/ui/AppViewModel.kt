package com.creatora.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatora.app.domain.model.ThemeMode
import com.creatora.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** App-wide state needed before any screen is drawn (currently the theme setting). */
@HiltViewModel
class AppViewModel @Inject constructor(settingsRepository: SettingsRepository) : ViewModel() {

    /** `null` until the saved setting has been read, so the app never flashes the wrong theme. */
    val themeMode: StateFlow<ThemeMode?> = settingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)
}
