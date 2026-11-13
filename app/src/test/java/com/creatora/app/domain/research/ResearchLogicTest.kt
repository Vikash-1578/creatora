package com.creatora.app.domain.research

import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.account.ConnectedAccount
import com.creatora.app.domain.account.VideoAnalytics
import com.creatora.app.domain.account.VideoStats
import com.creatora.app.domain.home.CreatorIdea
import com.creatora.app.domain.home.IdeaStatus
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal fun vid(id: String, daysAgo: Long, views: Long, channelId: String = "c", title: String = "Video $id", tags: List<String> = emptyList()) =
    VideoStats(id, title, Instant.now().minus(Duration.ofDays(daysAgo)).toString(), views, channelTitle = "Chan", channelId = channelId, tags = tags)

class ResearchLogicTest {
    private val now get() = System.currentTimeMillis()
    private fun account(id: String, platform: ConnectPlatform = ConnectPlatform.YOUTUBE) = ConnectedAccount(platform, id, "Chan $id")

    @Test
    fun pickAccount_saysWhyThereIsNoSingleAccount() {
        assertEquals(Pick.NoAccount, pickAccount(emptyList()))
        assertEquals(Pick.PickOne, pickAccount(listOf(account("a"), account("b"))))
        assertEquals(Pick.Unsupported, pickAccount(listOf(account("a", ConnectPlatform.INSTAGRAM))))
        assertEquals(Pick.Unsupported, pickAccount(listOf(account("a", ConnectPlatform.MOJ))))
        val one = account("a")
        assertEquals(Pick.Account(one), pickAccount(listOf(one)))
    }

    @Test
    fun ageBands_followTheSpec() {
        assertEquals(AgeBand.WEEK, ageBandOf(0))
        assertEquals(AgeBand.WEEK, ageBandOf(7))
        assertEquals(AgeBand.MONTH, ageBandOf(8))
        assertEquals(AgeBand.MONTH, ageBandOf(30))
        assertEquals(AgeBand.QUARTER, ageBandOf(31))
        assertEquals(AgeBand.QUARTER, ageBandOf(90))
        assertEquals(AgeBand.OLDER, ageBandOf(91))
    }

    private val channel = listOf(
        vid("v1", 10, 1_000), vid("v2", 15, 1_000), vid("v3", 20, 1_000), vid("v4", 25, 1_000),
        vid("win", 12, 5_000), vid("old", 200, 100), vid("fresh", 1, 50),
    )

    @Test
    fun outlierScore_comparesWithTheMedianOfTheSameAgeBand() {
        val scored = scoreOutliers(channel, now)

        val win = scored.first()
        assertEquals("win", win.video.id)
        assertEquals(5.0, win.score, 0.001)
        assertEquals(AgeBand.MONTH, win.band)
        assertTrue(win.ageAdjusted)
        assertEquals(1_000L, win.baseline)
        assertEquals(1.0, scored.first { it.video.id == "v1" }.score, 0.001) // the others, with the winner, still have a median of 1,000
    }

    @Test
    fun aBandWithTooFewVideos_fallsBackToTheWholeChannel_andSaysSo() {
        val old = scoreOutliers(channel, now).first { it.video.id == "old" }

        assertFalse(old.ageAdjusted)
        assertEquals(AgeBand.OLDER, old.band)
        assertEquals(0.1, old.score, 0.001)
    }

    @Test
    fun videosUnderTwoDaysOldAreLeftOut_andTooFewVideosGiveNothing() {
        assertTrue(scoreOutliers(channel, now).none { it.video.id == "fresh" })
        assertTrue(scoreOutliers(channel.take(4), now).isEmpty())
        assertTrue(scoreOutliers(listOf(vid("bad", 5, 10).copy(publishedAt = "not a date")) + channel.take(4), now).isEmpty())
    }

    @Test
    fun aChannelWhoseVideosAllHaveNoViews_isNotScored() {
        assertTrue(scoreOutliers((1..6).map { vid("z$it", 10L + it, 0) }, now).isEmpty())
    }

    @Test
    fun winnersWeakestAndViral_useTheirThresholds() {
        val scored = scoreOutliers(channel, now)

        assertEquals(listOf("win"), winners(scored).map { it.video.id })
        assertEquals(listOf("old"), weakest(scored).map { it.video.id })
        assertEquals(listOf("win"), viral(scored).map { it.video.id })
        assertTrue(winners(scored, minScore = 6.0).isEmpty())
        assertEquals(listOf("win"), winners(scored, take = 1).map { it.video.id })
    }

    @Test
    fun uploadFrequency_isUploadsPerWeekFromTheOldestUploadToNow() {
        assertEquals(1.0, uploadsPerWeek(listOf(vid("a", 7, 1), vid("b", 14, 1), vid("c", 21, 1)), now)!!, 0.01)
        assertNull(uploadsPerWeek(listOf(vid("a", 7, 1)), now))
        assertNull(uploadsPerWeek(emptyList(), now))
    }

    @Test
    fun averageViews_isTheMeanOfTheNewest15_andTopVideosAreTheMostViewed() {
        val videos = (1L..20L).map { vid("v$it", it, 100 * it) }

        assertEquals(800L, averageViews(videos)) // ages 1..15
        assertNull(averageViews(emptyList()))
        assertEquals(listOf("v20", "v19"), topVideos(videos, take = 2).map { it.id })
    }

    @Test
    fun growth_needsTwoDaysOfSnapshots_otherwiseItIsOnlyTracking() {
        assertEquals(Growth.Tracking(null), growthFrom(emptyList()))
        val first = CompetitorSnapshot("20261101", 100, 1_000, 10)
        assertEquals(Growth.Tracking("20261101"), growthFrom(listOf(first)))
        assertEquals(Growth.Tracking("20261101"), growthFrom(listOf(first, first.copy(subscribers = 150)))) // same day twice

        val later = CompetitorSnapshot("20261110", 130, 1_900, 12)
        assertEquals(Growth.Known(9, 30, 900, "20261101"), growthFrom(listOf(later, first)))
        assertEquals(Growth.Known(9, null, 900, "20261101"), growthFrom(listOf(first.copy(subscribers = null), later)))
    }

    private fun results(titleHas: Int, bigger: Int) = KeywordSearch(
        videos = (0 until 10).map { vid("k$it", 30, 3_000, channelId = "c$it", title = if (it < titleHas) "best dal recipe" else "something else") },
        channelSubscribers = (0 until 10).associate { "c$it" to (if (it < bigger) 5_000L else 500L) },
    )

    @Test
    fun keywordEstimate_isBuiltFromRealSignals_andLabelledByLevel() {
        val estimate = keywordEstimate("dal recipe", results(titleHas = 4, bigger = 5), userSubscribers = 1_000, nowMillis = now)!!

        assertEquals(100, estimate.recentPercent)
        assertEquals(100L, estimate.medianViewsPerDay)
        assertEquals(65, estimate.demandPoints)
        assertEquals(Level.MEDIUM, estimate.demand)
        assertEquals(40, estimate.exactMatchPercent)
        assertEquals(50, estimate.biggerChannelsPercent)
        assertEquals(46, estimate.competitionPoints)
        assertEquals(Level.MEDIUM, estimate.competition)
        assertEquals(61, estimate.score)
        assertEquals(30, estimate.medianAgeDays)
        assertEquals(10, estimate.sampleSize)
    }

    @Test
    fun withoutChannelSizes_competitionLeavesThatPartOutInsteadOfGuessing() {
        val estimate = keywordEstimate("dal recipe", results(titleHas = 4, bigger = 5), userSubscribers = null, nowMillis = now)!!

        assertNull(estimate.biggerChannelsPercent)
        assertEquals(40, estimate.competitionPoints)
    }

    @Test
    fun quietOldResults_meanLowDemand_andTooFewResultsGiveNoEstimate() {
        val quiet = KeywordSearch((0 until 6).map { vid("q$it", 300, 10) }, emptyMap())

        val estimate = keywordEstimate("x", quiet, 100, now)!!
        assertEquals(Level.LOW, estimate.demand)
        assertEquals(0, estimate.demandPoints)
        assertNull(keywordEstimate("x", KeywordSearch(quiet.videos.take(4), emptyMap()), 100, now))
    }

    @Test
    fun relatedTags_areTheOnesTwoOrMoreResultsShare_withoutTheQueryItself() {
        val videos = listOf(
            vid("a", 1, 1, tags = listOf("Dal", "street food", "q")),
            vid("b", 1, 1, tags = listOf("dal", "rice")),
            vid("c", 1, 1, tags = listOf(" DAL ", "street food", "q")),
        )

        assertEquals(listOf("dal", "street food"), relatedTags("q", videos))
        assertTrue(relatedTags("q", listOf(vid("a", 1, 1, tags = listOf("one")))).isEmpty())
    }

    @Test
    fun ideaStatus_isMadeOnceTheLinkedProjectIsReady_andTheBankFiltersByIt() {
        val saved = CreatorIdea("1", "t", status = IdeaStatus.SAVED)
        val planned = saved.copy(status = IdeaStatus.PLANNED, projectId = 4)

        assertEquals(IdeaStatus.SAVED, effectiveStatus(saved, null))
        assertEquals(IdeaStatus.PLANNED, effectiveStatus(planned, false))
        assertEquals(IdeaStatus.MADE, effectiveStatus(planned, true))
        assertEquals(IdeaStatus.SAVED, effectiveStatus(saved, true)) // no project, so nothing to be ready

        assertTrue(inBank(IdeaStatus.SAVED) && inBank(IdeaStatus.PLANNED) && inBank(IdeaStatus.MADE))
        assertFalse(inBank(IdeaStatus.NEW) || inBank(IdeaStatus.DISMISSED))
        assertTrue(BankFilter.NEW.matches(IdeaStatus.SAVED) && !BankFilter.NEW.matches(IdeaStatus.PLANNED))
        assertTrue(BankFilter.PLANNED.matches(IdeaStatus.PLANNED) && BankFilter.MADE.matches(IdeaStatus.MADE))
        assertTrue(BankFilter.ALL.matches(IdeaStatus.MADE) && !BankFilter.ALL.matches(IdeaStatus.DISMISSED))
    }

    @Test
    fun outlierFacts_quoteRealNumbers_andMarkWhatIsMissing() {
        val scored = scoreOutliers(channel, now)
        val win = scored.first { it.video.id == "win" }
        val old = scored.first { it.video.id == "old" }

        val bare = outlierFacts(win, null, now)
        assertTrue("Views: 5000" in bare && "5.0 times the channel's usual (1000)" in bare)
        assertTrue("Click-through rate: not available" in bare && "Traffic sources (views): not available" in bare)
        assertTrue("Thumbnail: not analysed" in bare)
        assertTrue("not age-adjusted" in outlierFacts(old, null, now))

        val full = outlierFacts(win, VideoAnalytics("win", ctrPercent = 4.2, averageViewPercent = 51.0, trafficSources = mapOf("YT_SEARCH" to 120L, "SUGGESTED" to 300L)), now)
        assertTrue("Click-through rate: 4.2%" in full && "Average percentage viewed: 51.0%" in full)
        assertTrue("Traffic sources (views): SUGGESTED 300, YT_SEARCH 120" in full)
    }

    @Test
    fun competitorFacts_sayWhenCommentsAreMissing_andQuoteThemWhenPresent() {
        val item = scoreOutliers(channel, now).first { it.video.id == "win" }

        assertTrue("Top comments: not available" in competitorFacts(item, "Dal Diaries", null, now))
        val with = competitorFacts(item, "Dal Diaries", listOf("great tip", "too long\nreally"), now)
        assertTrue("(only these 2)" in with && "- great tip" in with && "- too long really" in with)
        assertTrue("Retention and thumbnail are not available" in with)
    }

    @Test
    fun uploadNextFacts_needSomethingRealToStandOn() {
        assertNull(uploadNextFacts("food", emptyList(), emptyList()))
        val facts = uploadNextFacts(null, listOf("Hot video"), listOf("Rival video (Dal Diaries)"))!!
        assertTrue("Niche: not known" in facts && "- Hot video" in facts && "- Rival video (Dal Diaries)" in facts)
    }

    @Test
    fun viralConcept_needsBothParts() {
        assertEquals(ViralConcept("A fast dish", "It has a time limit."), parseViralConcept("CONCEPT: A fast dish\nWHY: It has a time limit."))
        assertNull(parseViralConcept("CONCEPT: only this"))
        assertNull(parseViralConcept("no markers at all"))
    }
}
