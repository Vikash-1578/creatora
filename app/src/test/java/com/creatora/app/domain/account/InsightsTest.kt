package com.creatora.app.domain.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightsTest {
    private fun video(id: String, views: Long) = VideoStats(id, "Title $id", "2026-01-01T00:00:00Z", views, likes = 5, comments = null)

    @Test
    fun findUnderperformers_picksVideosBelowHalfTheMedian_worstFirst() {
        val videos = listOf(video("a", 1_000), video("b", 900), video("c", 1_100), video("d", 100), video("e", 300), video("f", 1_000))

        val result = findUnderperformers(videos)

        assertEquals(listOf("d", "e"), result.map { it.video.id })
        // Six videos: the median is the average of the two middle values, (900 + 1000) / 2.
        assertEquals(950L, result[0].medianViews)
        assertEquals(10, result[0].percentOfMedian)
        assertEquals(31, result[1].percentOfMedian)
    }

    @Test
    fun findUnderperformers_needsEnoughVideos_andAMedian() {
        assertTrue(findUnderperformers(listOf(video("a", 10), video("b", 1_000), video("c", 900))).isEmpty())
        assertTrue(findUnderperformers((1..6).map { video("v$it", 0) }).isEmpty())
    }

    @Test
    fun findUnderperformers_capsTheList() {
        val videos = (1..10).map { video("low$it", 10L * it) } + (1..10).map { video("high$it", 10_000) }
        assertEquals(3, findUnderperformers(videos).size)
        assertEquals(5, findUnderperformers(videos, maxCount = 5).size)
    }

    @Test
    fun parseInsights_readsBlocksForTheAskedVideos_only() {
        val text = """
            VIDEO: a
            WHY: Few people clicked.
            The click-through rate was 1.2%.
            FIX: Try a clearer thumbnail.
            Shorten the title.

            VIDEO: zzz
            WHY: not asked about
            FIX: ignore

            VIDEO: b
            WHY: only a reason, no fix
        """.trimIndent()

        val result = parseInsights(text, setOf("a", "b"))

        assertEquals(1, result.size)
        assertEquals("a", result[0].videoId)
        assertEquals("Few people clicked. The click-through rate was 1.2%.", result[0].why)
        assertEquals("Try a clearer thumbnail. Shorten the title.", result[0].fix)
    }

    @Test
    fun insightFacts_writesMissingNumbersAsNotAvailable_andNeverInventsAny() {
        val under = listOf(Underperformer(video("a", 120), medianViews = 1_000, percentOfMedian = 12))
        val withAnalytics = mapOf("a" to VideoAnalytics("a", ctrPercent = 1.25, averageViewPercent = null, trafficSources = mapOf("YT_SEARCH" to 40L)))

        val facts = insightFacts(under, withAnalytics)
        assertTrue("Views: 120 (12% of the channel's median of 1000 views)" in facts)
        assertTrue("Click-through rate: 1.3%" in facts || "Click-through rate: 1.2%" in facts)
        assertTrue("Average percentage viewed: not available" in facts)
        assertTrue("Comments: not available" in facts)
        assertTrue("YT_SEARCH 40" in facts)

        val none = insightFacts(under, emptyMap())
        assertTrue("Click-through rate: not available" in none && "Traffic sources (views): not available" in none)
    }
}
