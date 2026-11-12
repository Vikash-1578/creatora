package com.creatora.app.domain.grow

import java.time.LocalDate

/** The periods the analytics screens offer. */
enum class Period(val days: Int) { WEEK(7), MONTH(28), QUARTER(90) }

/** The sums of [daily] (oldest first) for the latest period, then the one before it, and so on. Only whole periods are returned. */
fun periodSums(daily: List<Long>, days: Int): List<Long> {
    if (days <= 0) return emptyList()
    return (0 until daily.size / days).map { i ->
        val end = daily.size - i * days
        daily.subList(end - days, end).sum()
    }
}

private fun median(values: List<Long>): Double {
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid].toDouble() else (sorted[mid - 1] + sorted[mid]) / 2.0
}

/**
 * "Your usual": the median of up to six periods before the latest one. Needs at least three of them, otherwise null,
 * and the screen does not show a comparison it cannot back up.
 */
fun usual(sums: List<Long>): Double? = sums.drop(1).take(6).takeIf { it.size >= 3 }?.let(::median)

/** The change against the period before, as a percent; null without one, or when it was zero. */
fun changePercent(sums: List<Long>): Double? =
    sums.getOrNull(1)?.takeIf { it != 0L }?.let { (sums[0] - it) * 100.0 / it }

const val THIN_VIEWS = 100L
const val THIN_DAYS = 7

/** "Not enough data yet": fewer than 100 views in the period or fewer than 7 days of data. */
fun notEnoughData(views: Long, daysOfData: Int): Boolean = views < THIN_VIEWS || daysOfData < THIN_DAYS

/** Analytics runs one to two days behind: the last day a report can cover. */
fun dataAsOf(today: LocalDate): LocalDate = today.minusDays(2)

data class Share(val label: String, val percent: Double)

/** The biggest [take] items as percents of the whole (top 25 search terms, say); the rest is added up as "other" when there is any. */
fun shares(values: Map<String, Double>, take: Int = Int.MAX_VALUE, otherLabel: String = "other"): List<Share> {
    val total = values.values.sum()
    if (total <= 0.0) return emptyList()
    val sorted = values.entries.sortedByDescending { it.value }
    val top = sorted.take(take).map { Share(it.key, it.value * 100 / total) }
    val rest = sorted.drop(take).sumOf { it.value }
    return if (rest > 0) top + Share(otherLabel, rest * 100 / total) else top
}

/** Estimated revenue per 1,000 views, from the two real numbers; null when either is missing. */
fun rpm(revenueUsd: Double?, views: Long): Double? = if (revenueUsd == null || views <= 0) null else revenueUsd / views * 1000

/** A rule-based note under a chart: what moved and by how much, from the numbers only. */
sealed interface Trend {
    data object NoComparison : Trend
    data class Up(val percent: Double) : Trend
    data class Down(val percent: Double) : Trend
    data object Flat : Trend
}

fun trendOf(sums: List<Long>): Trend = when (val p = changePercent(sums)) {
    null -> Trend.NoComparison
    else -> if (p > 1) Trend.Up(p) else if (p < -1) Trend.Down(-p) else Trend.Flat
}
