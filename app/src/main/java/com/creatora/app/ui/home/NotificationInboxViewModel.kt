package com.creatora.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatora.app.domain.repository.InboxEntry
import com.creatora.app.domain.repository.NotificationInboxRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The bell: the last notifications the app sent. Null until the list has loaded. */
@HiltViewModel
class NotificationInboxViewModel @Inject constructor(private val inbox: NotificationInboxRepository) : ViewModel() {
    val entries: StateFlow<List<InboxEntry>?> = inbox.entries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun clear() {
        viewModelScope.launch { inbox.clear() }
    }
}
