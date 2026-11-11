package com.creatora.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.repository.AuthService
import com.creatora.app.domain.repository.ConnectedAccountRepository
import com.creatora.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val settings: SettingsRepository,
    auth: AuthService,
    accounts: ConnectedAccountRepository,
) : ViewModel() {

    val displayName: StateFlow<String> = settings.displayName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    /** The name of the connected YouTube channel, or null when none is connected. */
    val channelTitle: StateFlow<String?> = auth.user
        .flatMapLatest { user -> if (user == null) flowOf(emptyList()) else accounts.observe(user.uid) }
        .map { list -> list.firstOrNull { it.platform == ConnectPlatform.YOUTUBE }?.title }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setDisplayName(name: String) {
        viewModelScope.launch { settings.setDisplayName(name) }
    }
}
