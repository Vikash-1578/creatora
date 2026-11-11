package com.creatora.app.ui.usage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatora.app.domain.account.Plan
import com.creatora.app.domain.account.UsageStatus
import com.creatora.app.domain.account.monthKey
import com.creatora.app.domain.account.usageStatus
import com.creatora.app.domain.repository.AuthService
import com.creatora.app.domain.repository.BillingRepository
import com.creatora.app.domain.repository.SettingsRepository
import com.creatora.app.domain.repository.UsageRepository
import com.creatora.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Watches this month's AI usage for the whole app: a banner at 91% to 99% and a one-time dialog at 90%. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class UsageViewModel @Inject constructor(
    auth: AuthService,
    users: UserRepository,
    usage: UsageRepository,
    private val settings: SettingsRepository,
    billing: BillingRepository,
) : ViewModel() {

    private val month = monthKey(System.currentTimeMillis())
    private val dialogDismissed = MutableStateFlow(false)

    init {
        viewModelScope.launch { billing.refresh() }
    }

    /** Null when nobody is signed in. */
    val status: StateFlow<UsageStatus?> = auth.user.flatMapLatest { user ->
        if (user == null) {
            flowOf<UsageStatus?>(null)
        } else {
            combine(users.observe(user.uid), usage.observe(user.uid, month), billing.state) { doc, used, billingState ->
                val plan = if (billingState.hasPro) Plan.PRO else doc?.plan ?: Plan.FREE
                usageStatus(used, plan.aiLimit)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val shownMonth: Flow<String?> = settings.usageDialogMonth

    val showDialog: StateFlow<Boolean> = combine(status, shownMonth, dialogDismissed) { status, shown, dismissed ->
        status?.warnsWithDialog == true && shown != month && !dismissed
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** The dialog is shown once per month, however it is closed. */
    fun dialogSeen() {
        dialogDismissed.value = true
        viewModelScope.launch { settings.setUsageDialogMonth(month) }
    }
}
