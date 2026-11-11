package com.creatora.app.domain.grow

import com.creatora.app.domain.model.ChannelSnapshot
import java.util.Calendar
import java.util.TimeZone

/** The channel the user is growing. */
data class MyChannel(val id: String, val title: String)

enum class DraftStatus { PENDING, APPROVED, DISMISSED }

/** An AI-written reply to a pasted comment, waiting for the user's approval. */
data class CommentDraft(
    val id: Long = 0,
    val comment: String,
    val reply: String,
    val status: DraftStatus = DraftStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
)

enum class TestKind { TITLE, THUMBNAIL }

enum class TestVariant { A, B }

/** A title or thumbnail A/B test the user is running by hand. */
data class AbTest(
    val id: Long = 0,
    val name: String,
    val kind: TestKind,
    val variantA: String,
    val variantB: String,
    val notes: String = "",
    /** Null while the test is running. */
    val winner: TestVariant? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
)

/** How a number changed over a period. [change] is null when there is nothing to compare with. */
data class Delta(val current: Long, val change: Long?)

/** Views gained on one day; null when no snapshots cover that day. */
data class DayGain(val dayStartMillis: Long, val views: Long?)

private const val DAY_MS = 24L * 60 * 60 * 1000

private fun startOfDay(millis: Long, zone: TimeZone): Long = Calendar.getInstance(zone).apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** The start of the day after the one starting at [dayStart], correct across daylight-saving changes. */
private fun nextDay(dayStart: Long, zone: TimeZone): Long = Calendar.getInstance(zone).apply {
    timeInMillis = dayStart
    add(Calendar.DAY_OF_YEAR, 1)
}.timeInMillis

/**
 * Views gained on each of the last [days] days (oldest first, ending with today). A day's gain is the
 * view count at the end of that day minus the count at the end of the day before, each taken from the
 * latest snapshot up to that moment. Days without snapshots on both sides are null.
 */
fun dailyViewGains(snapshots: List<ChannelSnapshot>, days: Int, nowMillis: Long, zone: TimeZone = TimeZone.getDefault()): List<DayGain> {
    val sorted = snapshots.sortedBy { it.capturedAt }
    fun viewsAt(moment: Long): Long? = sorted.lastOrNull { it.capturedAt <= moment }?.viewCount
    var dayStart = startOfDay(nowMillis, zone)
    repeat(days - 1) { dayStart = startOfDay(dayStart - 1, zone) }
    return (0 until days).map {
        val end = nextDay(dayStart, zone) - 1
        val gain = viewsAt(end)?.let { endViews -> viewsAt(dayStart - 1)?.let { startViews -> endViews - startViews } }
        DayGain(dayStart, gain).also { dayStart = nextDay(dayStart, zone) }
    }
}

/**
 * The newest value of [value] and how much it changed since the newest snapshot that is at least
 * [days] days older. Falls back to the oldest snapshot when the history is shorter than the period.
 */
fun deltaOver(snapshots: List<ChannelSnapshot>, days: Int, value: (ChannelSnapshot) -> Long?): Delta? {
    val sorted = snapshots.sortedBy { it.capturedAt }
    val latest = sorted.lastOrNull() ?: return null
    val current = value(latest) ?: return null
    val cutoff = latest.capturedAt - days * DAY_MS
    val reference = sorted.lastOrNull { it.capturedAt <= cutoff } ?: sorted.first().takeIf { it !== latest }
    val before = reference?.let(value)
    return Delta(current, before?.let { current - it })
}

private const val RECAPTURE_AFTER_MS = 12L * 60 * 60 * 1000

/** Snapshots are taken when the Grow tab opens, at most every 12 hours. */
fun shouldCapture(lastCapturedAt: Long?, nowMillis: Long): Boolean = lastCapturedAt == null || nowMillis - lastCapturedAt >= RECAPTURE_AFTER_MS

/** Number of pasted comments that still wait for the user's decision. */
fun List<CommentDraft>.pendingCount(): Int = count { it.status == DraftStatus.PENDING }
