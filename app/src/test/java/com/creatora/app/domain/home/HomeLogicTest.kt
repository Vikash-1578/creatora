package com.creatora.app.domain.home

import com.creatora.app.domain.model.Platform
import com.creatora.app.domain.model.ScheduledPost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLogicTest {
    private val now = 1_000_000_000_000L
    private val hour = 60L * 60 * 1000
    private fun post(id: Long, project: Long, at: Long) = ScheduledPost(id, project, Platform.YOUTUBE, at)

    @Test
    fun postsDueWithin24Hours_comeFirstSoonestFirst() {
        val notices = buildNotices(
            now,
            listOf(post(1, 10, now + 5 * hour), post(2, 20, now + hour), post(3, 30, now + 30 * hour)),
            mapOf(10L to "Ten", 20L to "Twenty", 30L to "Thirty"),
            pendingReplies = 0,
        )

        assertEquals(listOf(20L, 10L), notices.map { it.projectId })
        assertEquals(listOf("Twenty", "Ten"), notices.map { it.title })
        assertTrue(notices.all { it.kind == NoticeKind.POST_SOON })
    }

    @Test
    fun postsThatPassedInTheLastTwoDays_areMissed_olderOnesAreLeftOut() {
        val notices = buildNotices(
            now,
            listOf(post(1, 10, now - hour), post(2, 20, now - 47 * hour), post(3, 30, now - 49 * hour)),
            emptyMap(),
            pendingReplies = 0,
        )

        assertEquals(listOf(10L, 20L), notices.map { it.projectId })
        assertTrue(notices.all { it.kind == NoticeKind.POST_MISSED })
        assertEquals("", notices[0].title)
    }

    @Test
    fun replyDraftsAreCounted_andSkippedWhenNoneWait() {
        val withReplies = buildNotices(now, emptyList(), emptyMap(), pendingReplies = 3)
        assertEquals(listOf(Notice(NoticeKind.REPLIES_WAITING, count = 3)), withReplies)

        assertTrue(buildNotices(now, emptyList(), emptyMap(), pendingReplies = 0).isEmpty())
    }

    @Test
    fun order_isSoonThenMissedThenReplies() {
        val notices = buildNotices(now, listOf(post(1, 1, now - hour), post(2, 2, now + hour)), emptyMap(), pendingReplies = 1)
        assertEquals(listOf(NoticeKind.POST_SOON, NoticeKind.POST_MISSED, NoticeKind.REPLIES_WAITING), notices.map { it.kind })
    }
}
