package com.creatora.app.domain.home

import com.creatora.app.domain.account.ConnectedAccount
import com.creatora.app.domain.repository.AuthService
import com.creatora.app.domain.repository.AuthUser
import com.creatora.app.domain.repository.ConnectedAccountRepository
import com.creatora.app.domain.repository.HomePrefsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * Who is signed in, which accounts they connected, and which of them the screens follow. Home, Research and the other
 * account screens share this, so the switcher on Home decides what every screen shows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class AccountContext @Inject constructor(
    auth: AuthService,
    accountsRepository: ConnectedAccountRepository,
    private val prefs: HomePrefsRepository,
) {
    val user: Flow<AuthUser?> = auth.user

    val accounts: Flow<List<ConnectedAccount>> =
        auth.user.flatMapLatest { user -> if (user == null) flowOf(emptyList()) else accountsRepository.observe(user.uid) }

    val selection: Flow<AccountSelection> = combine(accounts, prefs.selectedKey, ::resolveSelectionOf)

    /** The accounts the selection stands for (one, or all). */
    val selected: Flow<List<ConnectedAccount>> = combine(accounts, selection) { list, choice -> selectedAccounts(list, choice) }

    suspend fun select(selection: AccountSelection) = prefs.setSelectedKey(selection.storedKey())

    private fun resolveSelectionOf(accounts: List<ConnectedAccount>, stored: String?) = resolveSelection(stored, accounts)
}
