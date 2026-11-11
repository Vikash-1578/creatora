package com.creatora.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.creatora.app.domain.model.ThemeMode
import com.creatora.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** App-wide state needed before any screen is drawn (currently the theme setting). */
class AppViewModel(settingsRepository: SettingsRepository) : ViewModel() {

    /** `null` until the saved setting has been read, so the app never flashes the wrong theme. */
    val themeMode: StateFlow<ThemeMode?> = settingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { AppViewModel(appContainer().settingsRepository) }
        }
    }
}
