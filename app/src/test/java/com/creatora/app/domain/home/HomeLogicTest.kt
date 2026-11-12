package com.creatora.app.domain.home

import com.creatora.app.domain.account.AppLanguage
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.account.ConnectedAccount
import com.creatora.app.domain.account.Goal
import com.creatora.app.domain.account.GoalMetric
import com.creatora.app.domain.account.PeriodMetrics
import com.creatora.app.domain.account.VideoAnalytics
import com.creatora.app.domain.account.VideoStats
import java.time.Instant
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLogicTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val now = Instant.parse("2026-01-14T12:00:00Z").toEpochMilli() // a Wednesday
    private val day = 24L * 60 * 60 * 1000
    private fun at(iso: String) = Instant.parse(iso).toEpochMilli()
    private fun account(id: String) = ConnectedAccount(ConnectPlatform.YOUTUBE, id, "Channel $id")

    // ---- accounts
    @Test
    fun resolveSelection_prefersTheSavedChoice_andFallsBackToTheFirstAccount() {
        val two = listOf(account("a"), account("b"))
        assertEquals(AccountSelection.All, resolveSelection("all", two))
        assertEquals(AccountSelection.One("YOUTUBE_b"), resolveSelection("YOUTUBE_b", two))
        assertEquals(AccountSelection.One("YOUTUBE_a"), resolveSelection("YOUTUBE_gone", two))
        assertEquals(AccountSelection.One("YOUTUBE_a"), resolveSelection(null, two))
        assertEquals(AccountSelection.One("YOUTUBE_a"), resolveSelection("all", listOf(account("a")))) // All needs more than one
        assertEquals(AccountSelection.All, resolveSelection("YOUTUBE_a", emptyList()))
    }

    @Test
    fun selectedAccounts_followsTheSelection() {
        val two = listOf(account("a"), account("b"))
        assertEquals(two, selectedAccounts(two, AccountSelection.All))
        assertEquals(listOf(two[1]), selectedAccounts(two, AccountSelection.One("YOUTUBE_b")))
        assertEquals("all", AccountSelection.All.storedKey())
        assertEquals("YOUTUBE_a", AccountSelection.One("YOUTUBE_a").storedKey())
    }

    // ---- numbers
    @Test
    fun change_hasADelta_andAPercentOnlyWhenThereWasSomethingBefore() {
        assertEquals(20.0, Change(120.0, 100.0).percent!!, 0.001)
        assertEquals(-50.0, Change(50.0, 100.0).percent!!, 0.001)
        assertEquals(20.0, Change(120.0, 100.0).delta!!, 0.001)
        assertNull(Change(120.0, null).delta)
        assertNull(Change(120.0, 0.0).percent)
    }

    @Test
    fun sumMetrics_addsEverything_butRevenueOnlyWhenEveryAccountHasIt() {
        val a = PeriodMetrics(100, 600, 10, 2, revenueUsd = 5.0)
        val b = PeriodMetrics(50, 300, 4, 1, revenueUsd = null)
        val both = sumMetrics(listOf(a, b))!!
        assertEquals(listOf(150L, 900L, 14L, 3L), listOf(both.views, both.watchMinutes, both.subscribersGained, both.subscribersLost))
        assertNull(both.revenueUsd)
        assertEquals(15.0, sumMetrics(listOf(a, a.copy(revenueUsd = 10.0)))!!.revenueUsd!!, 0.001)
        assertNull(sumMetrics(emptyList()))
    }

    @Test
    fun buildSnapshot_hidesEarningsWhenNotMonetized_andConvertsWatchTimeToHours() {
        val current = PeriodMetrics(1_000, 180, 30, 10, revenueUsd = null)
        val previous = PeriodMetrics(800, 120, 20, 5)
        val snapshot = buildSnapshot(5_000, current, previous)
        assertEquals(5_000L, snapshot.subscribers)
        assertEquals(3.0, snapshot.watchHours!!.current, 0.001)
        assertEquals(2.0, snapshot.watchHours!!.previous!!, 0.001)
        assertEquals(20.0, snapshot.subscribersNet!!.current, 0.001)
        assertEquals(15.0, snapshot.subscribersNet!!.previous!!, 0.001)
        assertNull(snapshot.earningsUsd)
        assertNull(buildSnapshot(1, null, null).views)
        assertEquals(12.5, buildSnapshot(1, current.copy(revenueUsd = 12.5), null).earningsUsd!!.current, 0.001)
    }

    // ---- uploads
    private fun video(id: String, daysAgo: Double, views: Long) =
        VideoStats(id, "Title $id", Instant.ofEpochMilli(now - (daysAgo * day).toLong()).toString(), views)

    @Test
    fun classifyUploads_comparesWithTheMedian_ofVideosOlderThanFortyEightHours() {
        val videos = listOf(
            video("new", 1.0, 5), // under 48 hours
            video("a", 3.0, 1_000), video("b", 5.0, 1_000), video("c", 7.0, 1_000), video("good", 9.0, 1_500),
            video("poor", 11.0, 400), video("avg", 13.0, 900),
        )

        val rows = classifyUploads(videos, now, take = 7).associateBy { it.video.id }

        assertEquals(UploadStatus.TOO_NEW, rows.getValue("new").status)
        assertEquals(UploadStatus.GOOD, rows.getValue("good").status)
        assertEquals(UploadStatus.POOR, rows.getValue("poor").status)
        assertEquals(UploadStatus.AVERAGE, rows.getValue("avg").status)
        assertEquals(100, rows.getValue("a").percentOfMedian)
        assertEquals(listOf("new", "a", "b", "c", "good"), classifyUploads(videos, now).map { it.video.id }) // newest five
    }

    @Test
    fun classifyUploads_judgesNothingWithTooFewVideos() {
        val rows = classifyUploads(listOf(video("a", 5.0, 10), video("b", 6.0, 900), video("c", 7.0, 1_000)), now)
        assertTrue(rows.all { it.status == UploadStatus.UNKNOWN && it.percentOfMedian == null })
    }

    // ---- streak
    @Test
    fun weeklyStreak_countsConsecutiveWeeks_startingOnMonday() {
        // Today is Wednesday 14 January. Weeks start on Monday: 12 January is this week, 5 January the week before.
        val uploads = listOf("2026-01-05T10:00:00Z", "2025-12-29T10:00:00Z", "2025-12-15T10:00:00Z", "2025-12-08T10:00:00Z", "2025-12-01T10:00:00Z").map(::at)

        val streak = computeStreak(uploads, StreakUnit.WEEKS, now, utc)

        assertEquals(2, streak.current) // 29 December and 5 January, this week is still empty
        assertEquals(3, streak.best) // 1, 8 and 15 December
        assertTrue(streak.atRisk)
    }

    @Test
    fun weeklyStreak_includesTheCurrentWeek_andSundayBelongsToTheWeekBefore() {
        val withThisWeek = computeStreak(listOf("2026-01-13T08:00:00Z", "2026-01-05T10:00:00Z", "2025-12-29T10:00:00Z").map(::at), StreakUnit.WEEKS, now, utc)
        assertEquals(3, withThisWeek.current)
        assertFalse(withThisWeek.atRisk)

        // Sunday 11 January and Monday 12 January are different weeks.
        val sundayAndMonday = computeStreak(listOf("2026-01-11T23:00:00Z", "2026-01-12T01:00:00Z").map(::at), StreakUnit.WEEKS, now, utc)
        assertEquals(2, sundayAndMonday.current)
    }

    @Test
    fun dailyStreak_andNoUploads() {
        val today = computeStreak(listOf(now, now - day, now - 3 * day), StreakUnit.DAYS, now, utc)
        assertEquals(2, today.current)
        assertEquals(2, today.best)
        assertFalse(today.atRisk)

        val yesterdayOnly = computeStreak(listOf(now - day, now - 2 * day), StreakUnit.DAYS, now, utc)
        assertEquals(2, yesterdayOnly.current)
        assertTrue(yesterdayOnly.atRisk)

        val gap = computeStreak(listOf(now - 3 * day), StreakUnit.DAYS, now, utc)
        assertEquals(0, gap.current)
        assertEquals(1, gap.best)
        assertFalse(gap.atRisk)
        assertEquals(0, computeStreak(emptyList(), StreakUnit.WEEKS, now, utc).best)
    }

    // ---- goals
    private fun goal(metric: GoalMetric, target: Long, start: Long? = null) = Goal(metric = metric, target = target, startedAt = 0, deadline = 1, startValue = start)

    @Test
    fun goalProgress_usesRealNumbersForEachMetric() {
        val subs = goalProgress(goal(GoalMetric.SUBSCRIBERS, 1_000, start = 400), GoalInputs(subscribersNow = 800, viewsSince = null, uploadsSince = null, earningsSince = null))
        assertEquals(400L, subs.current)
        assertEquals(0.4f, subs.fraction!!, 0.001f)
        assertFalse(subs.reached)

        assertEquals(0.5f, goalProgress(goal(GoalMetric.VIEWS, 2_000), GoalInputs(null, 1_000, null, null)).fraction!!, 0.001f)
        val uploads = goalProgress(goal(GoalMetric.UPLOADS, 4), GoalInputs(null, null, 5, null))
        assertTrue(uploads.reached)
        assertEquals(1f, uploads.fraction!!, 0.001f) // never above 100%
        assertEquals(250L, goalProgress(goal(GoalMetric.EARNINGS, 500), GoalInputs(null, null, null, 250.9)).current)
    }

    @Test
    fun goalProgress_saysWhatIsMissing_insteadOfInventingABar() {
        val noStart = goalProgress(goal(GoalMetric.SUBSCRIBERS, 100), GoalInputs(500, null, null, null))
        assertNull(noStart.fraction)
        assertEquals(GoalMissing.NO_START_VALUE, noStart.missing)

        val noData = goalProgress(goal(GoalMetric.VIEWS, 100), GoalInputs(null, null, null, null))
        assertEquals(GoalMissing.NO_DATA, noData.missing)
        assertNull(noData.current)

        // Subscribers cannot go below zero: a net loss is shown as no progress.
        assertEquals(0L, goalProgress(goal(GoalMetric.SUBSCRIBERS, 100, start = 500), GoalInputs(450, null, null, null)).current)
    }

    // ---- time, cache, locale
    @Test
    fun isFresh_and_keys() {
        assertTrue(isFresh(1_000, 500, 1_400))
        assertFalse(isFresh(1_000, 500, 1_500))
        assertFalse(isFresh(2_000, 500, 1_000)) // a time in the future is not fresh
        assertEquals("20260114", dayKey(now, utc))
        assertNotEquals(weekKey(at("2026-01-11T23:00:00Z"), utc), weekKey(at("2026-01-12T01:00:00Z"), utc))
        assertEquals(weekKey(at("2026-01-12T01:00:00Z"), utc), weekKey(at("2026-01-18T23:00:00Z"), utc))
    }

    @Test
    fun dateLocale_followsTheLanguage_andHinglishUsesEnglishIndia() {
        assertEquals("hi", AppLanguage.HINDI.dateLocale().language)
        assertEquals("IN", AppLanguage.HINGLISH.dateLocale().country)
        assertEquals("en", AppLanguage.HINGLISH.dateLocale().language)
        assertEquals("en", AppLanguage.ENGLISH.dateLocale().language)
    }

    @Test
    fun firstName_takesTheFirstWord() {
        assertEquals("Asha", firstName("  Asha  Rao "))
        assertNull(firstName("   "))
        assertNull(firstName(""))
    }

    // ---- facts for the AI
    @Test
    fun focusFacts_needsSixVideos_andQuotesOnlyRealNumbers() {
        assertNull(focusFacts((1..5).map { video("v$it", it * 3.0, 100) }, now))
        val videos = (1..10).map { video("v$it", it * 3.0, 1_000L - it * 50) }

        val facts = focusFacts(videos, now)!!

        // Newest five are v1..v5 (950 to 750, average 850); the five before are v6..v10 (700 to 500, average 600).
        assertTrue("Average views of the last 5 videos: 850" in facts)
        assertTrue("Average views of the 5 videos before them: 600" in facts)
        assertTrue("Best of the last 10 videos: \"Title v1\" with 950 views" in facts)
        assertTrue("Days since the newest upload: 3" in facts)
    }

    @Test
    fun ideaFacts_needsThreeVideos() {
        assertNull(ideaFacts("Ch", 10, listOf(video("a", 3.0, 1), video("b", 4.0, 2))))
        val facts = ideaFacts("Ch", 1_200, (1..5).map { video("v$it", it * 3.0, it * 100L) })!!
        assertTrue("Channel: Ch" in facts && "Subscribers: 1200" in facts && "\"Title v5\": 500 views" in facts)
    }

    @Test
    fun uploadFacts_marksMissingNumbers() {
        val row = UploadRow(video("a", 5.0, 120).copy(likes = null), UploadStatus.POOR, 12)
        val facts = uploadFacts(row, VideoAnalytics("a", ctrPercent = 1.5), now)
        assertTrue("Views: 120 (12% of the channel's usual for recent videos)" in facts)
        assertTrue("Likes: not available" in facts && "Click-through rate: 1.5%" in facts)
        assertTrue("Average percentage viewed: not available" in facts)
        assertTrue("Days since upload: 5" in facts)
    }

    @Test
    fun goalPlanFacts_stateProgressOrItsAbsence() {
        val g = Goal(metric = GoalMetric.SUBSCRIBERS, target = 1_000, startedAt = now - 10 * day, deadline = now + 20 * day, startValue = 0, uploadsPerWeek = 2, desire = "grow")
        val withProgress = goalPlanFacts(g, GoalProgress(400, 1_000, 0.4f, false, null), now)
        assertTrue("20 days left" in withProgress && "40% of the target" in withProgress && "2 times a week" in withProgress && "\"grow\"" in withProgress)
        assertTrue("Progress is not available yet." in goalPlanFacts(g, GoalProgress(null, 1_000, null, false, GoalMissing.NO_DATA), now))
    }

    @Test
    fun categoryForNiche_mapsObviousNiches() {
        assertEquals("20", categoryForNiche("Minecraft gaming"))
        assertEquals("27", categoryForNiche("Study tips and exam prep"))
        assertEquals("28", categoryForNiche("gadget reviews"))
        assertNull(categoryForNiche("street food"))
    }
}
