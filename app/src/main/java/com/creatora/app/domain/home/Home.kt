package com.creatora.app.domain.home

import com.creatora.app.domain.model.Platform
import com.creatora.app.domain.model.ScheduledPost

enum class NoticeKind { POST_SOON, POST_MISSED, REPLIES_WAITING }

/** One thing that needs the user's attention. [count] is only used for [NoticeKind.REPLIES_WAITING]. */
data class Notice(
    val kind: NoticeKind,
    val projectId: Long? = null,
    val title: String = "",
    val platform: Platform? = null,
    val atMillis: Long = 0,
    val count: Int = 0,
)

const val SOON_WINDOW_MILLIS = 24L * 60 * 60 * 1000
const val MISSED_WINDOW_MILLIS = 2L * 24 * 60 * 60 * 1000

/**
 * Notices derived from the user's own data: posts due in the next 24 hours, posts whose time passed in the
 * last two days (they may not have been posted) and reply drafts waiting for a decision. Soonest first.
 */
fun buildNotices(nowMillis: Long, posts: List<ScheduledPost>, titles: Map<Long, String>, pendingReplies: Int): List<Notice> {
    fun ScheduledPost.notice(kind: NoticeKind) = Notice(kind, projectId, titles[projectId].orEmpty(), platform, scheduledAt)
    val soon = posts.filter { it.scheduledAt in nowMillis..(nowMillis + SOON_WINDOW_MILLIS) }
        .sortedBy { it.scheduledAt }.map { it.notice(NoticeKind.POST_SOON) }
    val missed = posts.filter { it.scheduledAt < nowMillis && it.scheduledAt >= nowMillis - MISSED_WINDOW_MILLIS }
        .sortedByDescending { it.scheduledAt }.map { it.notice(NoticeKind.POST_MISSED) }
    val replies = if (pendingReplies > 0) listOf(Notice(NoticeKind.REPLIES_WAITING, count = pendingReplies)) else emptyList()
    return soon + missed + replies
}
