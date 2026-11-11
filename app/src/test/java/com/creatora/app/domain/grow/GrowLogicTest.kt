package com.creatora.app.domain.grow

import com.creatora.app.domain.model.ChannelSnapshot
import java.util.GregorianCalendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GrowLogicTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val day = 24L * 60 * 60 * 1000

    private fun at(day: Int, hour: Int = 0): Long = GregorianCalendar(utc).apply { clear(); set(2026, 0, day, hour, 0, 0) }.timeInMillis
    private fun snap(time: Long, views: Long, subs: Long = 0, videos: Long = 0) =
        ChannelSnapshot(channelId = "c", subscriberCount = subs, viewCount = views, videoCount = videos, capturedAt = time)

    @Test
    fun dailyViewGains_isTheDifferenceBetweenEndOfDayCounts() {
        val snapshots = listOf(snap(at(10, 10), 1_400), snap(at(7, 20), 1_000), snap(at(9, 9), 1_150), snap(at(8, 18), 1_100), snap(at(9, 23), 1_300))

        val gains = dailyViewGains(snapshots, days = 3, nowMillis = at(10, 12), zone = utc)

        assertEquals(listOf(at(8), at(9), at(10)), gains.map { it.dayStartMillis })
        assertEquals(listOf<Long?>(100, 200, 100), gains.map { it.views })
    }

    @Test
    fun dailyViewGains_hasNoValueForDaysWithoutHistoryOnBothSides() {
        val gains = dailyViewGains(listOf(snap(at(9, 8), 500), snap(at(10, 8), 650)), days = 3, nowMillis = at(10, 12), zone = utc)

        assertEquals(listOf<Long?>(null, null, 150), gains.map { it.views })
    }

    @Test
    fun dailyViewGains_withNothingStoredIsAllNull() {
        val gains = dailyViewGains(emptyList(), days = 7, nowMillis = at(10, 12), zone = utc)
        assertEquals(7, gains.size)
        assertTrue(gains.all { it.views == null })
    }

    @Test
    fun dailyViewGains_coversTheRequestedNumberOfDaysEndingToday() {
        val gains = dailyViewGains(emptyList(), days = 28, nowMillis = at(30, 23), zone = utc)
        assertEquals(28, gains.size)
        assertEquals(at(30), gains.last().dayStartMillis)
        assertEquals(at(3), gains.first().dayStartMillis)
    }

    @Test
    fun deltaOver_comparesWithTheNewestSnapshotAtLeastThePeriodOlder() {
        val snapshots = listOf(snap(0, 100), snap(3 * day, 150), snap(8 * day, 300))

        assertEquals(Delta(300, 200), deltaOver(snapshots, 7) { it.viewCount })
        assertEquals(Delta(300, 150), deltaOver(snapshots, 5) { it.viewCount })
    }

    @Test
    fun deltaOver_fallsBackToTheOldestSnapshotWhenHistoryIsShort() {
        val snapshots = listOf(snap(0, 100), snap(2 * day, 160))
        assertEquals(Delta(160, 60), deltaOver(snapshots, 28) { it.viewCount })
    }

    @Test
    fun deltaOver_needsTwoSnapshotsToShowAChange() {
        assertEquals(Delta(100, null), deltaOver(listOf(snap(0, 100)), 7) { it.viewCount })
        assertNull(deltaOver(emptyList(), 7) { it.viewCount })
    }

    @Test
    fun deltaOver_canBeNegative_andSkipsHiddenValues() {
        val snapshots = listOf(snap(0, 100, subs = 50), snap(8 * day, 90, subs = 0))
        assertEquals(Delta(90, -10), deltaOver(snapshots, 7) { it.viewCount })
        assertNull(deltaOver(snapshots, 7) { it.subscriberCount.takeIf { count -> count > 0 } })
    }

    @Test
    fun shouldCapture_afterTwelveHours() {
        assertTrue(shouldCapture(null, 1_000))
        assertTrue(shouldCapture(0, 12L * 60 * 60 * 1000))
        assertEquals(false, shouldCapture(0, 12L * 60 * 60 * 1000 - 1))
    }

    @Test
    fun pendingCount_countsOnlyUndecidedDrafts() {
        val drafts = listOf(
            CommentDraft(comment = "a", reply = "r"),
            CommentDraft(comment = "b", reply = "r", status = DraftStatus.APPROVED),
            CommentDraft(comment = "c", reply = "r", status = DraftStatus.DISMISSED),
            CommentDraft(comment = "d", reply = "r"),
        )
        assertEquals(2, drafts.pendingCount())
    }
}
