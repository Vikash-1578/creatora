package com.creatora.app.domain.home

import com.creatora.app.domain.account.AppLanguage
import com.creatora.app.domain.account.ConnectedAccount
import com.creatora.app.domain.account.Goal
import com.creatora.app.domain.account.GoalMetric
import com.creatora.app.domain.account.PeriodMetrics
import com.creatora.app.domain.account.VideoStats
import java.time.Instant
import java.util.Locale
import java.util.TimeZone

// ---------------------------------------------------------------- accounts

/** Which accounts Home shows: every one of them, or one. */
sealed interface AccountSelection {
    data object All : AccountSelection
    data class One(val key: String) : AccountSelection
}

const val ALL_ACCOUNTS_KEY = "all"

fun selectedAccounts(accounts: List<ConnectedAccount>, selection: AccountSelection): List<ConnectedAccount> = when (selection) {
    AccountSelection.All -> accounts
    is AccountSelection.One -> accounts.filter { it.key == selection.key }
}

/**
 * The saved choice if it still exists; otherwise the first account (or All when there is nothing to pick from).
 * "All accounts" is only offered with more than one account.
 */
fun resolveSelection(stored: String?, accounts: List<ConnectedAccount>): AccountSelection = when {
    accounts.isEmpty() -> AccountSelection.All
    stored == ALL_ACCOUNTS_KEY && accounts.size > 1 -> AccountSelection.All
    stored != null && accounts.any { it.key == stored } -> AccountSelection.One(stored)
    else -> AccountSelection.One(accounts.first().key)
}

fun AccountSelection.storedKey(): String = when (this) {
    AccountSelection.All -> ALL_ACCOUNTS_KEY
    is AccountSelection.One -> key
}

// ---------------------------------------------------------------- snapshot numbers

/** A number and how it moved against the previous period. [previous] is null when that period has no data. */
data class Change(val current: Double, val previous: Double?) {
    val delta: Double? get() = previous?.let { current - it }

    /** Percent change; null without a previous value or when it was zero (a percent of zero is meaningless). */
    val percent: Double? get() = previous?.takeIf { it != 0.0 }?.let { (current - it) / it * 100 }
}

/** Adds the metrics of several accounts. Revenue is only summed when every account reported it. */
fun sumMetrics(list: List<PeriodMetrics>): PeriodMetrics? {
    if (list.isEmpty()) return null
    return PeriodMetrics(
        views = list.sumOf { it.views },
        watchMinutes = list.mapNotNull { it.watchMinutes }.takeIf { it.isNotEmpty() }?.sum(),
        subscribersGained = list.mapNotNull { it.subscribersGained }.takeIf { it.isNotEmpty() }?.sum(),
        subscribersLost = list.mapNotNull { it.subscribersLost }.takeIf { it.isNotEmpty() }?.sum(),
        revenueUsd = if (list.all { it.revenueUsd != null }) list.sumOf { it.revenueUsd ?: 0.0 } else null,
    )
}

/** The Snapshot card: each value with its change. A tile is null when its data is not available. */
data class Snapshot(
    val subscribers: Long?,
    val views: Change?,
    val watchHours: Change?,
    val subscribersNet: Change?,
    val earningsUsd: Change?,
    /** Views per day for the mini chart; null when not available. */
    val viewsSeries: List<Long>? = null,
)

fun buildSnapshot(subscribers: Long?, current: PeriodMetrics?, previous: PeriodMetrics?, viewsSeries: List<Long>? = null): Snapshot = Snapshot(
    subscribers = subscribers,
    views = current?.let { Change(it.views.toDouble(), previous?.views?.toDouble()) },
    watchHours = current?.watchMinutes?.let { Change(it / 60.0, previous?.watchMinutes?.let { p -> p / 60.0 }) },
    subscribersNet = current?.netSubscribers?.let { Change(it.toDouble(), previous?.netSubscribers?.toDouble()) },
    earningsUsd = current?.revenueUsd?.let { Change(it, previous?.revenueUsd) },
    viewsSeries = viewsSeries,
)

/** Adds the daily series of several accounts day by day; null when any is missing or they differ in length (nothing is guessed). */
fun sumSeries(list: List<List<Long>?>): List<Long>? {
    if (list.isEmpty() || list.any { it == null }) return null
    val all = list.filterNotNull()
    if (all.map { it.size }.distinct().size != 1) return null
    return all.first().indices.map { i -> all.sumOf { it[i] } }
}

// ---------------------------------------------------------------- recent uploads

enum class UploadStatus { GOOD, AVERAGE, POOR, TOO_NEW, UNKNOWN }

data class UploadRow(val video: VideoStats, val status: UploadStatus, val percentOfMedian: Int?)

const val NEW_VIDEO_HOURS = 48
private const val HOUR_MILLIS = 60L * 60 * 1000

fun parsePublished(iso: String): Long? = runCatching { Instant.parse(iso).toEpochMilli() }.getOrNull()

/**
 * The newest [take] uploads, each compared with the median of the account's own videos older than 48 hours: red below
 * 50% of it, green above 120%, otherwise yellow. Videos under 48 hours old are too new to judge, and with fewer than 4
 * comparable videos nothing is judged.
 */
fun classifyUploads(videos: List<VideoStats>, nowMillis: Long, take: Int = 5): List<UploadRow> {
    val sorted = videos.sortedByDescending { parsePublished(it.publishedAt) ?: 0L }
    fun tooNew(v: VideoStats) = (parsePublished(v.publishedAt) ?: 0L) > nowMillis - NEW_VIDEO_HOURS * HOUR_MILLIS
    val views = sorted.filterNot(::tooNew).map { it.views }.sorted()
    val median = when {
        views.size < 4 -> null
        views.size % 2 == 1 -> views[views.size / 2].toDouble()
        else -> (views[views.size / 2 - 1] + views[views.size / 2]) / 2.0
    }?.takeIf { it > 0 }
    return sorted.take(take).map { v ->
        when {
            tooNew(v) -> UploadRow(v, UploadStatus.TOO_NEW, null)
            median == null -> UploadRow(v, UploadStatus.UNKNOWN, null)
            else -> {
                val ratio = v.views / median
                UploadRow(v, if (ratio < 0.5) UploadStatus.POOR else if (ratio > 1.2) UploadStatus.GOOD else UploadStatus.AVERAGE, (ratio * 100).toInt())
            }
        }
    }
}

// ---------------------------------------------------------------- streak

enum class StreakUnit { WEEKS, DAYS }

/** [atRisk]: the streak is alive but nothing has been uploaded in the current period yet. */
data class Streak(val current: Int, val best: Int, val atRisk: Boolean, val unit: StreakUnit)

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

private fun periodIndex(millis: Long, unit: StreakUnit, zone: TimeZone): Long {
    val day = Math.floorDiv(millis + zone.getOffset(millis), DAY_MILLIS)
    // 1 January 1970 was a Thursday, so +3 starts the weeks on Monday.
    return if (unit == StreakUnit.DAYS) day else Math.floorDiv(day + 3, 7L)
}

/** Consecutive weeks (or days) with at least one upload, ending now. The current period may still be empty. */
fun computeStreak(uploadTimes: List<Long>, unit: StreakUnit, nowMillis: Long, zone: TimeZone = TimeZone.getDefault()): Streak {
    val periods = uploadTimes.map { periodIndex(it, unit, zone) }.toSortedSet()
    val now = periodIndex(nowMillis, unit, zone)
    var best = 0
    var run = 0
    var previous: Long? = null
    for (p in periods) {
        run = if (previous != null && p == previous + 1) run + 1 else 1
        best = maxOf(best, run)
        previous = p
    }
    val liveEnd = if (now in periods) now else now - 1
    var current = 0
    var cursor = liveEnd
    while (cursor in periods) {
        current++
        cursor--
    }
    return Streak(current, best, atRisk = current > 0 && now !in periods, unit = unit)
}

// ---------------------------------------------------------------- goals

/** Progress toward a goal. [current] and [fraction] are null when the real data is not available. */
data class GoalProgress(val current: Long?, val target: Long, val fraction: Float?, val reached: Boolean, val missing: GoalMissing?)

enum class GoalMissing { NO_START_VALUE, NO_DATA }

/** The numbers a goal can be measured against, each null when the platform did not give it. */
data class GoalInputs(val subscribersNow: Long?, val viewsSince: Long?, val uploadsSince: Int?, val earningsSince: Double?)

/**
 * A subscriber goal is the total to reach ("10,000,000 subscribers"), so [GoalProgress.current] is today's total and the bar
 * runs from the starting count to the target. The other goals count what happened since the goal started.
 */
fun goalProgress(goal: Goal, inputs: GoalInputs): GoalProgress {
    val current: Long? = when (goal.metric) {
        GoalMetric.SUBSCRIBERS -> inputs.subscribersNow
        GoalMetric.VIEWS -> inputs.viewsSince
        GoalMetric.UPLOADS -> inputs.uploadsSince?.toLong()
        GoalMetric.EARNINGS -> inputs.earningsSince?.toLong()
    }
    if (current == null) return GoalProgress(null, goal.target, null, reached = false, missing = GoalMissing.NO_DATA)
    val fraction = if (goal.metric == GoalMetric.SUBSCRIBERS) {
        val start = (goal.startValue ?: 0L).coerceAtMost(current)
        if (goal.target > start) ((current - start).toFloat() / (goal.target - start)).coerceIn(0f, 1f) else if (current >= goal.target) 1f else 0f
    } else {
        (current.toFloat() / goal.target).coerceIn(0f, 1f)
    }
    return GoalProgress(current, goal.target, fraction, reached = current >= goal.target, missing = null)
}

// ---------------------------------------------------------------- time, cache, locale

fun isFresh(cachedAtMillis: Long, ttlMillis: Long, nowMillis: Long): Boolean = nowMillis - cachedAtMillis in 0 until ttlMillis

/** yyyyMMdd for the local day. */
fun dayKey(nowMillis: Long, zone: TimeZone = TimeZone.getDefault()): String {
    val c = java.util.Calendar.getInstance(zone).apply { timeInMillis = nowMillis }
    return "%04d%02d%02d".format(c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH))
}

/** A key that changes every Monday, for the weekly re-plan. */
fun weekKey(nowMillis: Long, zone: TimeZone = TimeZone.getDefault()): String = "w" + periodIndex(nowMillis, StreakUnit.WEEKS, zone)

/** Dates follow the app language. Android has no Hinglish patterns, so Hinglish uses English (India). */
fun AppLanguage.dateLocale(): Locale = when (this) {
    AppLanguage.ENGLISH -> Locale.getDefault().takeIf { it.language == "en" } ?: Locale("en", "IN")
    AppLanguage.HINDI -> Locale("hi", "IN")
    AppLanguage.HINGLISH -> Locale("en", "IN")
}

/** The first word of the Google name, or null when there is none. */
fun firstName(fullName: String): String? = fullName.trim().split(Regex("\\s+")).firstOrNull()?.takeIf { it.isNotEmpty() }

// ---------------------------------------------------------------- facts for AI cards

/** What the daily ideas are built from: the channel's own recent titles with their real views. Null with too few videos. */
fun ideaFacts(channelTitle: String, subscribers: Long?, videos: List<VideoStats>): String? {
    if (videos.size < 3) return null
    val sorted = videos.sortedByDescending { parsePublished(it.publishedAt) ?: 0L }
    return buildString {
        appendLine("Channel: $channelTitle")
        subscribers?.let { appendLine("Subscribers: $it") }
        appendLine("Most viewed of the recent videos:")
        sorted.take(10).sortedByDescending { it.views }.take(5).forEach { appendLine("- \"${it.title}\": ${it.views} views") }
        appendLine("Newest videos:")
        sorted.take(5).forEach { appendLine("- \"${it.title}\"") }
    }
}

/** The numbers behind "why did this video do well or badly": all real, with missing ones marked. */
fun uploadFacts(row: UploadRow, analytics: com.creatora.app.domain.account.VideoAnalytics?, nowMillis: Long): String {
    val v = row.video
    fun num(x: Long?) = x?.toString() ?: "not available"
    fun pct(x: Double?) = x?.let { "%.1f%%".format(Locale.US, it) } ?: "not available"
    val days = parsePublished(v.publishedAt)?.let { (nowMillis - it) / (24L * 60 * 60 * 1000) }
    return buildString {
        appendLine("Title: ${v.title}")
        appendLine("Views: ${v.views}" + (row.percentOfMedian?.let { " ($it% of the channel's usual for recent videos)" } ?: ""))
        days?.let { appendLine("Days since upload: $it") }
        appendLine("Likes: ${num(v.likes)}")
        appendLine("Comments: ${num(v.comments)}")
        appendLine("Click-through rate: ${pct(analytics?.ctrPercent)}")
        appendLine("Average percentage viewed: ${pct(analytics?.averageViewPercent)}")
        append("Average view duration: ${analytics?.averageViewSeconds?.let { "%.0f seconds".format(Locale.US, it) } ?: "not available"}")
    }
}

/** How a goal stands, in words for the weekly plan: only real numbers. */
fun goalPlanFacts(goal: Goal, progress: GoalProgress, nowMillis: Long): String {
    val daysLeft = ((goal.deadline - nowMillis) / (24L * 60 * 60 * 1000)).coerceAtLeast(0)
    return buildString {
        append("Target ${goal.target} (${goal.metric.name.lowercase()}), $daysLeft days left. ")
        if (progress.current != null && progress.fraction != null) {
            append("So far ${progress.current}, which is ${(progress.fraction * 100).toInt()}% of the target.")
        } else {
            append("Progress is not available yet.")
        }
        goal.uploadsPerWeek?.let { append(" They want to upload $it times a week.") }
        if (goal.desire.isNotBlank()) append(" In their own words: \"${goal.desire}\".")
    }
}
