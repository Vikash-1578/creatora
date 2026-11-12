package com.creatora.app.domain.home

import com.creatora.app.domain.account.GoalMetric
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.pow

/** [suggested] is a target that fits the recent pace; it equals the asked target when the goal is realistic. */
data class Realism(val unrealistic: Boolean, val suggested: Long)

private fun minimumStretch(metric: GoalMetric): Double = when (metric) {
    GoalMetric.SUBSCRIBERS -> 10.0
    GoalMetric.VIEWS -> 100.0
    GoalMetric.UPLOADS -> 2.0
    GoalMetric.EARNINGS -> 5.0
}

/** Rounds up to 1, 2 or 5 times a power of ten, so a suggestion reads like a person chose it. */
fun niceNumber(value: Double): Long {
    if (value <= 1) return 1
    val magnitude = 10.0.pow(kotlin.math.floor(log10(value)))
    val step = listOf(1.0, 2.0, 5.0, 10.0).first { it * magnitude >= value }
    return ceil(step * magnitude).toLong()
}

/**
 * Whether a goal can be reached at the recent pace. [recent28] is what the account got in the last 28 days for this goal's
 * measure (subscribers gained, views, uploads, earnings); [current] is today's total for a subscriber goal. A goal is called
 * unrealistic only when what is still needed is more than three times a generous stretch (twice the pace) of the time given.
 * Without the numbers nothing is judged (null).
 */
fun goalRealism(metric: GoalMetric, target: Long, current: Long?, recent28: Long?, days: Int): Realism? {
    if (recent28 == null || days <= 0) return null
    val needed = if (metric == GoalMetric.SUBSCRIBERS) target - (current ?: return null) else target
    val stretch = maxOf(recent28.coerceAtLeast(0) * days / 28.0 * 2, minimumStretch(metric))
    if (needed <= stretch * 3) return Realism(false, target)
    val gain = niceNumber(stretch)
    return Realism(true, if (metric == GoalMetric.SUBSCRIBERS) (current ?: 0L) + gain else gain)
}

/** Large numbers in the user's own grouping and, for Hindi and Hinglish, with lakh and crore, like "10,000,000 (1 crore)". */
fun goalNumber(value: Long, locale: java.util.Locale, lakh: String, crore: String): String {
    val plain = java.text.NumberFormat.getInstance(locale).format(value)
    fun words(unit: Long, word: String) = if (word.isBlank() || value < unit || value % unit != 0L) null else "${value / unit} $word"
    val inWords = words(10_000_000L, crore) ?: words(100_000L, lakh)
    return if (inWords == null) plain else "$plain ($inWords)"
}
