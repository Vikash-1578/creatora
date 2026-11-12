package com.creatora.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatora.app.domain.account.AdapterOutcome
import com.creatora.app.domain.account.ConnectedAccount
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.account.Goal
import com.creatora.app.domain.account.GoalMetric
import com.creatora.app.domain.account.PlatformRegistry
import com.creatora.app.domain.account.PlatformSupport
import com.creatora.app.domain.ai.AiTasks
import com.creatora.app.domain.account.aimText
import com.creatora.app.domain.home.AccountContext
import com.creatora.app.domain.home.AccountSelection
import com.creatora.app.domain.home.ChannelOverviewLoader
import com.creatora.app.domain.home.GoalInputs
import com.creatora.app.domain.home.GoalProgress
import com.creatora.app.domain.home.goalPlanFacts
import com.creatora.app.domain.home.goalProgress
import com.creatora.app.domain.home.parsePublished
import com.creatora.app.domain.home.weekKey
import com.creatora.app.domain.model.AiError
import com.creatora.app.domain.model.AiOutcome
import com.creatora.app.domain.repository.AuthService
import com.creatora.app.domain.repository.CacheRepository
import com.creatora.app.domain.repository.GoalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import com.creatora.app.domain.home.ChannelOverview
import com.creatora.app.domain.home.goalRealism
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GoalItem(val goal: Goal, val progress: GoalProgress?)

sealed interface PlanState {
    data object None : PlanState
    data object Loading : PlanState
    data class Ready(val goalId: String, val text: String) : PlanState
    data class Failed(val errors: List<AiError>) : PlanState
}

data class GoalsState(
    val goals: List<GoalItem> = emptyList(),
    /** A goal that was just reached: the celebration shows once. */
    val celebrating: Goal? = null,
    val plan: PlanState = PlanState.None,
    val loading: Boolean = true,
    /** A goal that looks out of reach: shown with a kind note and a suggested target before anything is saved. */
    val advice: GoalAdvice? = null,
)

/** The goal as typed, the target that fits the recent pace, and the AI's kind note (null while it is written or if it failed). */
data class GoalAdvice(
    val metric: GoalMetric, val target: Long, val days: Int, val forAll: Boolean, val startValue: Long?,
    val suggested: Long, val message: String?, val loading: Boolean,
)

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

/**
 * Goals for the selected account (and goals that cover all accounts), each with progress from real data, the
 * celebration when one is reached, and the AI's week-by-week plan for the primary goal, re-made every week.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val context: AccountContext,
    private val goals: GoalRepository,
    private val loader: ChannelOverviewLoader,
    private val registry: PlatformRegistry,
    private val ai: AiTasks,
    private val cache: CacheRepository,
    private val auth: AuthService,
) : ViewModel() {

    private val _state = MutableStateFlow(GoalsState())
    val state: StateFlow<GoalsState> = _state.asStateFlow()
    private var uid: String? = null
    private var selection: AccountSelection = AccountSelection.All

    init {
        viewModelScope.launch {
            val stream = auth.user.flatMapLatest { user -> if (user == null) flowOf(emptyList()) else goals.observe(user.uid) }
            combine(stream, context.selected, context.accounts) { all, selected, accounts -> Triple(all, selected, accounts) }
                .collectLatest { (all, selected, accounts) ->
                    uid = auth.user.first()?.uid
                    val keys = selected.map { it.key }.toSet()
                    val visible = all.filter { it.accountKey == null || it.accountKey in keys }
                    _state.update { it.copy(goals = visible.map { goal -> GoalItem(goal, null) }, loading = visible.isNotEmpty()) }
                    val items = visible.map { GoalItem(it, progressOf(it, accounts)) }
                    _state.update { it.copy(goals = items, loading = false) }
                    items.firstOrNull { item -> item.progress?.reached == true && item.goal.reachedAt == null }?.let { reached ->
                        uid?.let { id -> goals.save(id, reached.goal.copy(reachedAt = System.currentTimeMillis())) }
                        _state.update { it.copy(celebrating = reached.goal) }
                    }
                }
        }
    }

    /** Real numbers only: any missing input leaves the goal without a bar and says why. */
    private suspend fun progressOf(goal: Goal, accounts: List<ConnectedAccount>): GoalProgress {
        val scope = (if (goal.accountKey == null) accounts else accounts.filter { it.key == goal.accountKey })
            .filter { it.platform.support == PlatformSupport.FULL }
        if (scope.isEmpty()) return goalProgress(goal, GoalInputs(null, null, null, null))
        val today = LocalDate.now()
        val start = Instant.ofEpochMilli(goal.startedAt).atZone(ZoneId.systemDefault()).toLocalDate()
        val overviews = scope.map { loader.overview(it) }
        val subscribers = overviews.map { it.channel?.subscribers }.let { list -> if (list.any { it == null }) null else list.sumOf { it ?: 0L } }
        val uploads = if (overviews.any { it.error != null }) null else overviews.sumOf { o -> o.videos.count { (parsePublished(it.publishedAt) ?: 0L) >= goal.startedAt } }
        val ranged = scope.map { registry.adapter(ConnectPlatform.YOUTUBE).channelMetrics(it.id, start, today) }
        val metrics = if (ranged.all { it is AdapterOutcome.Ok }) ranged.map { (it as AdapterOutcome.Ok).value } else null
        return goalProgress(
            goal,
            GoalInputs(
                subscribersNow = subscribers,
                viewsSince = metrics?.sumOf { it.views },
                uploadsSince = uploads,
                earningsSince = metrics?.let { list -> if (list.all { it.revenueUsd != null }) list.sumOf { it.revenueUsd ?: 0.0 } else null },
            ),
        )
    }

    fun dismissCelebration() = _state.update { it.copy(celebrating = null) }

    /** A new goal for the selected account (or for all accounts when [forAll]). */
    fun add(metric: GoalMetric, target: Long, days: Int, forAll: Boolean, startValue: Long?) {
        val id = uid ?: return
        if (target <= 0 || days <= 0) return
        viewModelScope.launch {
            val accounts = context.selected.first()
            val scope = accounts.filter { it.platform.support == PlatformSupport.FULL }
            val overviews = scope.map { loader.overview(it) }
            val subscribers = overviews.map { it.channel?.subscribers }.let { list -> if (list.isEmpty() || list.any { it == null }) null else list.sumOf { it ?: 0L } }
            val start = startValue ?: if (metric == GoalMetric.SUBSCRIBERS) subscribers else null
            val now = System.currentTimeMillis()
            val recent = recentPace(metric, overviews, now)
            val realism = goalRealism(metric, target, subscribers, recent, days)
            if (realism != null && realism.unrealistic) {
                // Not saved yet: the user sees a kind note and a target that fits, and chooses.
                _state.update { it.copy(advice = GoalAdvice(metric, target, days, forAll, start, realism.suggested, null, loading = true)) }
                val message = (ai.goalAdvice(adviceFacts(metric, target, days, subscribers, recent, realism.suggested)) as? AiOutcome.Ok)?.value
                _state.update { state -> state.advice?.let { state.copy(advice = it.copy(message = message, loading = false)) } ?: state }
                return@launch
            }
            save(id, metric, target, days, forAll, start)
        }
    }

    private suspend fun save(uid: String, metric: GoalMetric, target: Long, days: Int, forAll: Boolean, startValue: Long?) {
        val key = if (forAll) null else context.selected.first().singleOrNull()?.key
        val now = System.currentTimeMillis()
        goals.save(uid, Goal(metric = metric, target = target, startedAt = now, deadline = now + days * DAY_MILLIS, startValue = startValue, accountKey = key, primary = false))
    }

    /** What the account got in the last 28 days for this measure; null when any of its data is missing. */
    private fun recentPace(metric: GoalMetric, overviews: List<ChannelOverview>, now: Long): Long? {
        if (overviews.isEmpty() || overviews.any { it.error != null }) return null
        return when (metric) {
            GoalMetric.UPLOADS -> overviews.sumOf { o -> o.videos.count { (parsePublished(it.publishedAt) ?: 0L) >= now - 28 * DAY_MILLIS } }.toLong()
            else -> {
                val current = overviews.map { it.current }
                if (current.any { it == null }) null else when (metric) {
                    GoalMetric.SUBSCRIBERS -> current.sumOf { it!!.netSubscribers ?: 0L }
                    GoalMetric.VIEWS -> current.sumOf { it!!.views }
                    else -> if (current.all { it!!.revenueUsd != null }) current.sumOf { it!!.revenueUsd!! }.toLong() else null
                }
            }
        }
    }

    private fun adviceFacts(metric: GoalMetric, target: Long, days: Int, current: Long?, recent: Long?, suggested: Long) =
        "Goal: ${metric.name.lowercase()} $target in $days days. Now: ${current ?: "not known"}. The last 28 days: $recent. A target that fits that pace: $suggested."

    /** The user picked the suggested target (or kept their own): saves the goal. */
    fun answerAdvice(useSuggested: Boolean) {
        val advice = _state.value.advice ?: return
        val id = uid ?: return
        _state.update { it.copy(advice = null) }
        viewModelScope.launch { save(id, advice.metric, if (useSuggested) advice.suggested else advice.target, advice.days, advice.forAll, advice.startValue) }
    }

    fun dismissAdvice() = _state.update { it.copy(advice = null) }

    fun edit(goal: Goal, target: Long, days: Int) {
        val id = uid ?: return
        if (target <= 0 || days <= 0) return
        viewModelScope.launch { goals.save(id, goal.copy(target = target, deadline = goal.startedAt + days * DAY_MILLIS, reachedAt = null)) }
    }

    fun delete(goal: Goal) {
        val id = uid ?: return
        viewModelScope.launch { goals.delete(id, goal.id) }
    }

    /** This week's plan for [item]: made once a week from the goal and its real progress, then cached. */
    fun plan(item: GoalItem, refresh: Boolean = false) {
        val id = uid ?: return
        _state.update { it.copy(plan = PlanState.Loading) }
        viewModelScope.launch {
            val key = "plan_${item.goal.id}_${weekKey(System.currentTimeMillis())}"
            val cached = if (refresh) null else cache.get(id, key)
            if (cached != null) {
                _state.update { it.copy(plan = PlanState.Ready(item.goal.id, cached.text)) }
                return@launch
            }
            val facts = goalPlanFacts(item.goal, item.progress ?: goalProgress(item.goal, GoalInputs(null, null, null, null)), System.currentTimeMillis())
            when (val outcome = ai.weeklyPlan(item.goal.aimText(), facts)) {
                is AiOutcome.Ok -> {
                    cache.put(id, key, outcome.value, System.currentTimeMillis())
                    _state.update { it.copy(plan = PlanState.Ready(item.goal.id, outcome.value)) }
                }
                is AiOutcome.Failed -> _state.update { it.copy(plan = PlanState.Failed(outcome.errors)) }
            }
        }
    }
}
