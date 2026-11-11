package com.creatora.app.domain.account

enum class GoalMetric { SUBSCRIBERS, VIEWS, UPLOADS, EARNINGS }

const val GOAL_DAYS = 30
private const val DAY_MILLIS = 24L * 60 * 60 * 1000
private const val DESIRE_LIMIT = 500

/**
 * What the creator is aiming for. [startValue] is the platform's number when the goal started (subscribers), so
 * progress is real; it is null when it was not known. [accountKey] null means all accounts. The first goal made in
 * onboarding is the [primary] one.
 */
data class Goal(
    val id: String = "",
    val metric: GoalMetric,
    val target: Long,
    val startedAt: Long,
    val deadline: Long,
    val startValue: Long? = null,
    val accountKey: String? = null,
    val desire: String = "",
    val uploadsPerWeek: Int? = null,
    val primary: Boolean = true,
    val reachedAt: Long? = null,
)

/** The 30-day goal from the onboarding step, or null when the target is not a positive number. */
fun newGoal(
    metric: GoalMetric,
    target: Long,
    desire: String,
    uploadsPerWeek: Int?,
    accounts: List<ConnectedAccount>,
    nowMillis: Long,
): Goal? {
    if (target <= 0) return null
    val fetchable = accounts.filter { it.platform.support == PlatformSupport.FULL }
    // A subscriber goal starts from the real total; with any unknown count there is no honest starting point.
    val start = if (metric == GoalMetric.SUBSCRIBERS && fetchable.isNotEmpty() && fetchable.all { it.subscribers != null }) {
        fetchable.sumOf { it.subscribers ?: 0L }
    } else {
        null
    }
    return Goal(
        metric = metric,
        target = target,
        startedAt = nowMillis,
        deadline = nowMillis + GOAL_DAYS * DAY_MILLIS,
        startValue = start,
        desire = desire.trim().take(DESIRE_LIMIT),
        uploadsPerWeek = uploadsPerWeek?.takeIf { it in 1..7 },
    )
}

/** The goal in words for the AI: an aim, never a promise. */
fun Goal.aimText(): String = buildString {
    append(
        when (metric) {
            GoalMetric.SUBSCRIBERS -> "gain $target subscribers"
            GoalMetric.VIEWS -> "reach $target views"
            GoalMetric.UPLOADS -> "publish $target videos"
            GoalMetric.EARNINGS -> "earn $target (in the currency of their channel's earnings)"
        },
    )
    startValue?.let { append(" (starting from $it)") }
}
