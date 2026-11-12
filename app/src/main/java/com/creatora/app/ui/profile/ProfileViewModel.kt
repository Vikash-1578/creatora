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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val auth: AuthService,
    private val accounts: ConnectedAccountRepository,
) : ViewModel() {

    /** The name the user saved, or the Google account's name until they choose one. */
    val displayName: StateFlow<String> = combine(settings.displayName, auth.user) { saved, user -> saved.ifBlank { user?.name.orEmpty() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    /** The Google photo, or the connected channel's picture when there is none. */
    val photoUrl: StateFlow<String?> = combine(
        auth.user,
        auth.user.flatMapLatest { user -> if (user == null) flowOf(emptyList()) else accounts.observe(user.uid) },
    ) { user, list -> user?.photoUrl ?: list.firstNotNullOfOrNull { it.photoUrl } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The name of the connected YouTube channel, or null when none is connected. */
    val channelTitle: StateFlow<String?> = auth.user
        .flatMapLatest { user -> if (user == null) flowOf(emptyList()) else accounts.observe(user.uid) }
        .map { list -> list.firstOrNull { it.platform == ConnectPlatform.YOUTUBE }?.title }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setDisplayName(name: String) {
        viewModelScope.launch { settings.setDisplayName(name) }
    }
}
