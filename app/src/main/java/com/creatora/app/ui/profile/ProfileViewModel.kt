package com.creatora.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatora.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ProfileViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {

    val displayName: StateFlow<String> = settings.displayName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun setDisplayName(name: String) {
        viewModelScope.launch { settings.setDisplayName(name) }
    }
}
