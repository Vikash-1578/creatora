package com.creatora.app.ui.research

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatora.app.domain.account.ConnectedAccount
import com.creatora.app.domain.home.AccountContext
import com.creatora.app.domain.home.AccountSelection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ResearchHeader(val accounts: List<ConnectedAccount>, val selection: AccountSelection)

/** The account switcher at the top of Research. It is the same choice as Home's: changing it here changes it there. */
@HiltViewModel
class ResearchViewModel @Inject constructor(private val context: AccountContext) : ViewModel() {
    /** Null until the accounts have loaded. */
    val header: StateFlow<ResearchHeader?> = combine(context.accounts, context.selection, ::ResearchHeader)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), null)

    fun select(selection: AccountSelection) {
        viewModelScope.launch { context.select(selection) }
    }

    private companion object {
        const val STOP_MILLIS = 5_000L
    }
}
