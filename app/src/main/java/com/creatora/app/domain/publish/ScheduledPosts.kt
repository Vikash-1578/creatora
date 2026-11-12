package com.creatora.app.domain.publish

import com.creatora.app.domain.account.ConnectPlatform
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Where one destination of a post stands. */
enum class PostStatus { QUEUED, SCHEDULED, UPLOADING, PROCESSING, LIVE, FAILED, AWAITING_PUBLIC, SHARED }

/** One destination of a post: a post to three accounts is three of these with the same [postId]. */
data class ScheduledPost(
    val id: String,
    val postId: String,
    val projectId: Long?,
    val accountKey: String?,
    val platform: ConnectPlatform,
    val title: String,
    val scheduledAt: Long?,
    val publishedAt: Long? = null,
    val status: PostStatus = PostStatus.QUEUED,
    val error: String? = null,
    val remoteId: String? = null,
    val remoteUrl: String? = null,
    val privacy: String? = null,
    /** Views in the first 24 hours so far, and when that number was read. */
    val views24h: Long? = null,
    val viewsAsOf: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** A finished destination: nothing more will happen to it. */
fun PostStatus.isFinished() = this == PostStatus.LIVE || this == PostStatus.FAILED || this == PostStatus.SHARED || this == PostStatus.AWAITING_PUBLIC

/** The steps a post may move through; anything else is a bug or a late answer and is ignored. */
fun canMove(from: PostStatus, to: PostStatus): Boolean = when (from) {
    PostStatus.QUEUED -> to in setOf(PostStatus.SCHEDULED, PostStatus.UPLOADING, PostStatus.SHARED, PostStatus.FAILED)
    PostStatus.SCHEDULED -> to in setOf(PostStatus.UPLOADING, PostStatus.SHARED, PostStatus.FAILED, PostStatus.QUEUED, PostStatus.PROCESSING, PostStatus.LIVE, PostStatus.AWAITING_PUBLIC)
    PostStatus.UPLOADING -> to in setOf(PostStatus.PROCESSING, PostStatus.AWAITING_PUBLIC, PostStatus.LIVE, PostStatus.SCHEDULED, PostStatus.FAILED)
    PostStatus.PROCESSING -> to in setOf(PostStatus.LIVE, PostStatus.AWAITING_PUBLIC, PostStatus.SCHEDULED, PostStatus.FAILED)
    PostStatus.FAILED -> to == PostStatus.QUEUED // Retry
    PostStatus.AWAITING_PUBLIC -> to == PostStatus.LIVE
    PostStatus.LIVE, PostStatus.SHARED -> false
}

/** A post can be moved to another day only while it has not gone out and its time is still ahead. */
fun canReschedule(post: ScheduledPost, now: Long): Boolean {
    val at = post.scheduledAt ?: return false
    return post.status in setOf(PostStatus.SCHEDULED, PostStatus.QUEUED, PostStatus.UPLOADING, PostStatus.PROCESSING) && at > now
}

/** The same time of day on [day]; the post's own time zone is the phone's. */
fun movedTo(post: ScheduledPost, day: LocalDate, zone: ZoneId): Long {
    val time = Instant.ofEpochMilli(post.scheduledAt ?: return 0L).atZone(zone).toLocalTime()
    return day.atTime(time).atZone(zone).toInstant().toEpochMilli()
}

/** The weeks of a month, each seven days from [firstDay], with the days of the neighbouring months that fill the first and last week. */
fun monthGrid(month: LocalDate, firstDay: DayOfWeek): List<List<LocalDate>> {
    val first = month.withDayOfMonth(1)
    val start = first.minusDays(((first.dayOfWeek.value - firstDay.value + 7) % 7).toLong())
    val last = month.withDayOfMonth(month.lengthOfMonth())
    val weeks = ArrayList<List<LocalDate>>()
    var day = start
    while (!day.isAfter(last)) {
        weeks += (0 until 7).map { day.plusDays(it.toLong()) }
        day = day.plusDays(7)
    }
    return weeks
}

/** The seven days of the week that holds [day]. */
fun weekOf(day: LocalDate, firstDay: DayOfWeek): List<LocalDate> {
    val start = day.minusDays(((day.dayOfWeek.value - firstDay.value + 7) % 7).toLong())
    return (0 until 7).map { start.plusDays(it.toLong()) }
}

/** Posts by the day they are scheduled for (or went out), soonest first within a day. */
fun postsByDay(posts: List<ScheduledPost>, zone: ZoneId): Map<LocalDate, List<ScheduledPost>> =
    posts.mapNotNull { p -> (p.scheduledAt ?: p.publishedAt)?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() to p } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, list) -> list.sortedBy { it.scheduledAt ?: it.publishedAt } }

/** The first 24 hours of views are read every 2 hours: true while a live post is inside that window and the last reading is old enough. */
fun shouldPollViews(post: ScheduledPost, now: Long): Boolean {
    val live = post.publishedAt ?: post.scheduledAt ?: return false
    if (post.status != PostStatus.LIVE || post.remoteId == null) return false
    if (now - live > DAY_MILLIS) return false
    return post.viewsAsOf == null || now - post.viewsAsOf >= TWO_HOURS
}

private const val DAY_MILLIS = 24L * 3_600_000
private const val TWO_HOURS = 2L * 3_600_000

/** What stops a publish, in the order it is checked. */
sealed interface PublishGate {
    data object Open : PublishGate
    data class TooManyDestinations(val max: Int) : PublishGate
    data class DailyLimit(val max: Int) : PublishGate
    data object NoDestination : PublishGate
}

/** [destinations] chosen and [publishesToday] already started on the phone's local day, against the plan. */
fun publishGate(limits: com.creatora.app.domain.account.Limits, destinations: Int, publishesToday: Int): PublishGate = when {
    destinations == 0 -> PublishGate.NoDestination
    destinations > limits.platformsPerPost -> PublishGate.TooManyDestinations(limits.platformsPerPost)
    publishesToday >= limits.publishesPerDay -> PublishGate.DailyLimit(limits.publishesPerDay)
    else -> PublishGate.Open
}
