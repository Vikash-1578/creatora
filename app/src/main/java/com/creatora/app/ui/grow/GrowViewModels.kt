package com.creatora.app.ui.grow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatora.app.domain.account.AdapterError
import com.creatora.app.domain.account.AdapterOutcome
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.account.ConnectedAccount
import com.creatora.app.domain.ai.AiTasks
import com.creatora.app.domain.grow.AudienceReport
import com.creatora.app.domain.grow.ContentReport
import com.creatora.app.domain.grow.Earnings
import com.creatora.app.domain.grow.GrowLoader
import com.creatora.app.domain.grow.Overview
import com.creatora.app.domain.grow.PathKind
import com.creatora.app.domain.grow.Period
import com.creatora.app.domain.grow.Requirement
import com.creatora.app.domain.grow.TierProgress
import com.creatora.app.domain.grow.monetizationJourney
import com.creatora.app.domain.grow.usual
import com.creatora.app.domain.home.AccountContext
import com.creatora.app.domain.model.AiError
import com.creatora.app.domain.model.AiOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What one card area shows while its report is fetched, missing or there. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data object NoAccount : Load<Nothing>
    /** "All accounts" is chosen: reports are per account. */
    data object PickOne : Load<Nothing>
    data class Failed(val error: AdapterError) : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
}

private fun <T> AdapterOutcome<T>.toLoad(): Load<T> = when (this) {
    is AdapterOutcome.Ok -> Load.Ready(value)
    is AdapterOutcome.Failed -> Load.Failed(error)
}

/** The one account a report can be made for, or why there is none. Reports exist for YouTube only. */
private fun <T> accountOrState(accounts: List<ConnectedAccount>): Pair<ConnectedAccount?, Load<T>?> {
    val one = accounts.singleOrNull()
    return when {
        accounts.isEmpty() -> null to Load.NoAccount
        one == null -> null to Load.PickOne
        one.platform != ConnectPlatform.YOUTUBE || one.link != null -> null to Load.Failed(AdapterError.NotAvailable("Analytics reports come from YouTube only"))
        else -> one to null
    }
}

enum class AnalyticsTab { OVERVIEW, CONTENT, AUDIENCE }

data class ExplainState(val busy: Boolean = false, val text: String? = null, val errors: List<AiError> = emptyList())

data class AnalyticsUiState(
    val period: Period = Period.MONTH,
    val tab: AnalyticsTab = AnalyticsTab.OVERVIEW,
    val overview: Load<Overview> = Load.Loading,
    val content: Load<ContentReport> = Load.Loading,
    val audience: Load<AudienceReport> = Load.Loading,
    val explain: ExplainState = ExplainState(),
)

/** Grow, Analytics: Overview, Content and Audience for the account chosen on Home, for 7, 28 or 90 days. */
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val context: AccountContext,
    private val loader: GrowLoader,
    private val ai: AiTasks,
) : ViewModel() {
    private val _state = MutableStateFlow(AnalyticsUiState())
    val state: StateFlow<AnalyticsUiState> = _state.asStateFlow()
    private var accounts: List<ConnectedAccount> = emptyList()

    init {
        viewModelScope.launch { context.selected.collectLatest { accounts = it; load(force = false) } }
    }

    fun setPeriod(period: Period) {
        _state.update { it.copy(period = period, explain = ExplainState()) }
        viewModelScope.launch { load(force = false) }
    }

    fun setTab(tab: AnalyticsTab) {
        _state.update { it.copy(tab = tab, explain = ExplainState()) }
        viewModelScope.launch { load(force = false) }
    }

    fun refresh() {
        viewModelScope.launch { load(force = true) }
    }

    /** Loads only the open tab, so a tab that is never opened costs no quota. */
    private suspend fun load(force: Boolean) {
        val current = _state.value
        when (current.tab) {
            AnalyticsTab.OVERVIEW -> {
                val (account, blocked) = accountOrState<Overview>(accounts)
                _state.update { it.copy(overview = blocked ?: Load.Loading) }
                if (account != null) loader.overview(account, current.period, force).toLoad().let { r -> _state.update { it.copy(overview = r) } }
            }
            AnalyticsTab.CONTENT -> {
                val (account, blocked) = accountOrState<ContentReport>(accounts)
                _state.update { it.copy(content = blocked ?: Load.Loading) }
                if (account != null) loader.content(account, current.period).toLoad().let { r -> _state.update { it.copy(content = r) } }
            }
            AnalyticsTab.AUDIENCE -> {
                val (account, blocked) = accountOrState<AudienceReport>(accounts)
                _state.update { it.copy(audience = blocked ?: Load.Loading) }
                if (account != null) loader.audience(account, current.period).toLoad().let { r -> _state.update { it.copy(audience = r) } }
            }
        }
    }

    /** One AI action: a line per card of the open tab, written from the numbers on screen only. */
    fun explain() {
        val facts = factsOf(_state.value) ?: return
        if (_state.value.explain.busy) return
        _state.update { it.copy(explain = ExplainState(busy = true)) }
        viewModelScope.launch {
            val result = when (val outcome = ai.explainNumbers(facts)) {
                is AiOutcome.Ok -> ExplainState(text = outcome.value.trim())
                is AiOutcome.Failed -> ExplainState(errors = outcome.errors)
            }
            _state.update { it.copy(explain = result) }
        }
    }
}

/** The numbers of the open tab as plain lines for the AI. Null when there is nothing to explain yet. */
fun factsOf(state: AnalyticsUiState): String? {
    val days = state.period.days
    return when (state.tab) {
        AnalyticsTab.OVERVIEW -> (state.overview as? Load.Ready)?.value?.let { o ->
            fun line(name: String, sums: List<Long>) = "$name in the last $days days: ${sums.firstOrNull() ?: 0}; the $days days before: ${sums.getOrNull(1) ?: "not available"}; usual (median of earlier periods): ${usual(sums)?.toLong() ?: "not available"}"
            listOf(line("Views", o.views.sums), line("Watch time in minutes", o.watchMinutes.sums), line("Net subscribers", o.subscribersNet.sums)).joinToString("\n")
        }
        AnalyticsTab.CONTENT -> (state.content as? Load.Ready)?.value?.let { c ->
            listOf(
                "Traffic sources (share of views): " + c.traffic.take(6).joinToString { "${it.label} ${it.percent.toInt()}%" },
                "YouTube search terms: " + c.searchTerms.take(8).joinToString { "${it.label} ${it.percent.toInt()}%" }.ifBlank { "not available" },
                "Top videos by views: " + c.topVideos.take(5).joinToString { "${it.first} (${it.second})" }.ifBlank { "not available" },
            ).joinToString("\n")
        }
        AnalyticsTab.AUDIENCE -> (state.audience as? Load.Ready)?.value?.let { a ->
            listOf(
                "Age and gender (share of viewers): " + a.ageGender.take(6).joinToString { "${it.label} ${it.percent.toInt()}%" },
                "Countries: " + a.countries.take(5).joinToString { "${it.label} ${it.percent.toInt()}%" }.ifBlank { "not available" },
                "Devices: " + a.devices.joinToString { "${it.label} ${it.percent.toInt()}%" }.ifBlank { "not available" },
                "Views by weekday: " + a.weekdays.joinToString { "${it.label} ${it.percent.toInt()}%" }.ifBlank { "not available" },
            ).joinToString("\n")
        }
    }
}

data class EarnUiState(
    val period: Period = Period.MONTH,
    val earnings: Load<Earnings> = Load.Loading,
    val journey: Load<List<TierProgress>> = Load.Loading,
    val plan: ExplainState = ExplainState(),
)

/** Earnings (only when YouTube returns revenue) and the monetization journey, an estimate from API data. */
@HiltViewModel
class EarnViewModel @Inject constructor(
    private val context: AccountContext,
    private val loader: GrowLoader,
    private val ai: AiTasks,
) : ViewModel() {
    private val _state = MutableStateFlow(EarnUiState())
    val state: StateFlow<EarnUiState> = _state.asStateFlow()
    private var accounts: List<ConnectedAccount> = emptyList()

    init {
        viewModelScope.launch { context.selected.collectLatest { accounts = it; load() } }
    }

    fun setPeriod(period: Period) {
        _state.update { it.copy(period = period) }
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val (account, blockedEarnings) = accountOrState<Earnings>(accounts)
        if (account == null) {
            val (_, blockedJourney) = accountOrState<List<TierProgress>>(accounts)
            _state.update { it.copy(earnings = blockedEarnings ?: Load.Loading, journey = blockedJourney ?: Load.Loading) }
            return
        }
        _state.update { it.copy(earnings = Load.Loading, journey = if (it.journey is Load.Ready) it.journey else Load.Loading) }
        val earnings = loader.earnings(account, _state.value.period).toLoad()
        _state.update { it.copy(earnings = earnings) }
        val journey = monetizationJourney(loader.monetization(account))
        _state.update { it.copy(journey = Load.Ready(journey)) }
    }

    /** One AI action: a plan to the next milestone from the real gaps. A plan, never a promise. */
    fun makePlan() {
        val journey = (_state.value.journey as? Load.Ready)?.value ?: return
        if (_state.value.plan.busy) return
        val next = journey.firstOrNull { !it.met } ?: return
        _state.update { it.copy(plan = ExplainState(busy = true)) }
        viewModelScope.launch {
            val result = when (val outcome = ai.weeklyPlan("Reach YouTube Partner Program tier ${next.tier} (an estimate of the public rules)", gapsOf(next))) {
                is AiOutcome.Ok -> ExplainState(text = outcome.value.trim())
                is AiOutcome.Failed -> ExplainState(errors = outcome.errors)
            }
            _state.update { it.copy(plan = result) }
        }
    }
}

/** The gaps to a tier as plain lines for the AI: what the account has and needs on each path. */
fun gapsOf(tier: TierProgress): String = tier.paths.joinToString("\n") { path ->
    val name = if (path.kind == PathKind.WATCH_HOURS) "Watch hours path" else "Shorts path"
    name + ": " + path.items.joinToString("; ") { p ->
        val what = when (p.requirement) {
            Requirement.SUBSCRIBERS -> "subscribers"
            Requirement.UPLOADS_90 -> "public uploads in 90 days"
            Requirement.WATCH_HOURS_365 -> "watch hours in 365 days"
            Requirement.SHORTS_VIEWS_90 -> "Shorts views in 90 days"
        }
        "$what ${p.have?.toLong() ?: "not available"} of ${p.need.toLong()}"
    }
}
