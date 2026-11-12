package com.creatora.app.domain.publish

import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.account.Plan
import com.creatora.app.domain.account.PlanLimits
import com.creatora.app.domain.account.VideoStats
import com.creatora.app.domain.model.VideoFormat
import com.creatora.app.domain.score.CheckStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PublishDomainTest {
    private val utc = ZoneOffset.UTC

    // ---------------------------------------------------------------- platform specs and fitting
    @Test
    fun specsExistForTheApiPlatforms_andNotForShareOnlyOnes() {
        assertEquals(100, PlatformSpecs.of(ConnectPlatform.YOUTUBE)!!.titleMax)
        assertNull(PlatformSpecs.of(ConnectPlatform.MOJ))
        assertTrue(PlatformSpecs.of(ConnectPlatform.TIKTOK)!!.checkedOn.contains("not re-checked")) // never claims more certainty than it has
    }

    @Test
    fun aVideoThatDoesNotFit_getsOneIssuePerProblem_withThePlatformsLimits() {
        val tiktok = PlatformSpecs.of(ConnectPlatform.TIKTOK)!!
        val issues = fitIssues(tiktok, seconds = 900, aspect = Aspect.LANDSCAPE, title = "", caption = "x".repeat(2300), hashtags = List(31) { "t$it" })
        assertEquals(
            listOf(Fit.TooLong(900, 600), Fit.WrongShape(Aspect.LANDSCAPE, Aspect.PORTRAIT), Fit.TextTooLong(Field.CAPTION, 2300, 2200), Fit.TextTooLong(Field.HASHTAGS, 31, 30)),
            issues,
        )
        assertTrue(fitIssues(tiktok, 30, Aspect.PORTRAIT, "", "short", listOf("a")).isEmpty())
    }

    @Test
    fun youTubeTagsAreCountedInCharacters() {
        val yt = PlatformSpecs.of(ConnectPlatform.YOUTUBE)!!
        val many = List(60) { "tagnumber$it" }
        assertTrue(fitIssues(yt, 60, Aspect.PORTRAIT, "t", "d", emptyList(), many).any { it is Fit.TextTooLong && it.field == Field.TAGS })
    }

    @Test
    fun formatsMapToAShape_andSuggestionsAreCutButUserTextIsNot() {
        assertEquals(Aspect.PORTRAIT, aspectOf(VideoFormat.SHORTS_REELS))
        assertEquals(Aspect.LANDSCAPE, aspectOf(VideoFormat.LONG_VIDEO))
        assertEquals(listOf("a", "b"), fitSuggestion(listOf("a", "b", "c"), 2))
        assertEquals("Hello\n\n#one #two", captionWithTags(" Hello ", listOf("one", "#two")))
    }

    // ---------------------------------------------------------------- checks
    private fun draft(
        rendered: Boolean = true, title: String = "A good clear title", description: String = "A first line that says what the video is about.\nA second line with the link.",
        tags: List<String> = listOf("coding"), thumbnail: Boolean = true, ai: Boolean? = false, kids: Boolean? = false, unlicensed: Int = 0,
    ) = PublishDraft(rendered, title, description, tags, thumbnail, ai, kids, unlicensed)

    @Test
    fun aCompleteDraftPassesEverything() {
        val items = publishChecks(draft())
        assertTrue(items.all { it.status == CheckStatus.PASS })
        assertTrue(canPublish(items))
    }

    @Test
    fun theTwoQuestionsAndTheVideoAreRequired_neverDefaulted() {
        val items = publishChecks(draft(ai = null, kids = null))
        assertEquals(PubFix.AI_QUESTION, items.first { it.check == PubCheck.AI_DISCLOSURE }.fix)
        assertEquals(PubFix.KIDS_QUESTION, items.first { it.check == PubCheck.MADE_FOR_KIDS }.fix)
        assertFalse(canPublish(items))
        assertFalse(canPublish(publishChecks(draft(rendered = false))))
        assertFalse(canPublish(publishChecks(draft(ai = null))))
        assertTrue(canPublish(publishChecks(draft(kids = true, ai = true)))) // "yes" is an answer too
    }

    @Test
    fun warningsAllowPublishing_andEachOneHasAReasonAndAFix() {
        val items = publishChecks(draft(title = "x".repeat(80), description = "short", tags = emptyList(), thumbnail = false, unlicensed = 2))
        assertTrue(canPublish(items))
        val byCheck = items.associateBy { it.check }
        assertEquals(PubReason.TitleShown(80, 70), byCheck.getValue(PubCheck.TITLE).reason)
        assertEquals(PubReason.DescriptionShort(5), byCheck.getValue(PubCheck.DESCRIPTION).reason)
        assertEquals(PubFix.TAGS, byCheck.getValue(PubCheck.TAGS).fix)
        assertEquals(PubFix.THUMBNAIL, byCheck.getValue(PubCheck.THUMBNAIL).fix)
        assertEquals(PubReason.LicencesMissing(2), byCheck.getValue(PubCheck.LICENCES).reason)
        assertTrue(items.filter { it.status != CheckStatus.PASS }.all { it.fix != null })
    }

    @Test
    fun aTooLongOrEmptyTitleBlocks_andStuffedTagsWarn() {
        assertFalse(canPublish(publishChecks(draft(title = "x".repeat(101)))))
        assertFalse(canPublish(publishChecks(draft(title = " "))))
        val stuffed = publishChecks(draft(tags = List(20) { "tag$it" })).first { it.check == PubCheck.TAGS }
        assertEquals(CheckStatus.WARN, stuffed.status)
        assertEquals("line one\nline two", firstTwoLines("\nline one\n\nline two\nline three"))
        assertTrue(suggestedAiAnswer(draft().copy(usedAi = true)))
    }

    // ---------------------------------------------------------------- plan gate
    @Test
    fun freeAllowsOnePlatformAndOnePublishADay_proAllowsMore() {
        val free = PlanLimits.of(Plan.FREE)
        assertEquals(PublishGate.NoDestination, publishGate(free, 0, 0))
        assertEquals(PublishGate.TooManyDestinations(1), publishGate(free, 2, 0))
        assertEquals(PublishGate.DailyLimit(1), publishGate(free, 1, 1))
        assertEquals(PublishGate.Open, publishGate(free, 1, 0))
        assertEquals(PublishGate.Open, publishGate(PlanLimits.of(Plan.PRO), 3, 4))
    }

    // ---------------------------------------------------------------- best time
    private fun video(id: Int, iso: String, views: Long) = VideoStats("v$id", "t$id", iso, views)

    @Test
    fun withFewVideos_theSuggestionIsLabelledGuidance() {
        val best = bestTime(listOf(video(1, "2026-10-10T18:00:00Z", 100)), null, LocalDate.of(2026, 11, 1), VideoFormat.SHORTS_REELS, utc)
        assertEquals(BestBasis.Guidance, best.basis)
        assertEquals(BestBasis.Guidance, guidance(VideoFormat.LONG_VIDEO).basis)
        assertEquals(DayOfWeek.SATURDAY, guidance(VideoFormat.LONG_VIDEO).day)
    }

    @Test
    fun withEnoughVideos_theBestPartOfDayAndWeekdayComeFromTheirViews() {
        // Saturday evenings (2026-10-03, 10, 17, 24) did best; the other uploads were weekday mornings.
        val sat = listOf("2026-10-03", "2026-10-10", "2026-10-17", "2026-10-24").mapIndexed { i, d -> video(i, "${d}T18:30:00Z", 5_000L + i) }
        val mon = listOf("2026-10-05", "2026-10-12", "2026-10-19", "2026-10-26").mapIndexed { i, d -> video(10 + i, "${d}T08:00:00Z", 500L + i) }
        val best = bestTime(sat + mon, null, LocalDate.of(2026, 11, 1), VideoFormat.SHORTS_REELS, utc)
        assertEquals(DayPart.EVENING, best.part)
        assertEquals(DayOfWeek.SATURDAY, best.day)
        assertEquals(BestBasis.OwnData(8, usedWeekdayViews = false), best.basis)
    }

    @Test
    fun theWeekdayPrefersTheAccountsDailyViews_whenThereAre28DaysOrMore() {
        val videos = (0 until 8).map { video(it, "2026-10-${10 + it}T18:00:00Z", 1_000L) }
        val lastDay = LocalDate.of(2026, 11, 28)
        // Sundays get the views.
        val series = (0 until 90).map { i -> if (lastDay.minusDays((89 - i).toLong()).dayOfWeek == DayOfWeek.SUNDAY) 900L else 100L }
        val best = bestTime(videos, series, lastDay, null, utc)
        assertEquals(DayOfWeek.SUNDAY, best.day)
        assertEquals(BestBasis.OwnData(8, usedWeekdayViews = true), best.basis)
    }

    @Test
    fun nextAt_isTheNextMatchingWeekdayAndHour_atLeastAnHourAway() {
        val best = BestTime(DayOfWeek.SATURDAY, DayPart.EVENING, BestBasis.Guidance)
        val friday = LocalDate.of(2026, 11, 6).atTime(10, 0).atZone(utc).toInstant().toEpochMilli()
        val at = best.nextAt(friday, utc)
        assertEquals(LocalDate.of(2026, 11, 7).atTime(19, 0).atZone(utc).toInstant().toEpochMilli(), at)
        // Asked on Saturday at 18:30, 19:00 is under an hour away, so it is next week.
        val late = LocalDate.of(2026, 11, 7).atTime(18, 30).atZone(utc).toInstant().toEpochMilli()
        assertEquals(LocalDate.of(2026, 11, 14).atTime(19, 0).atZone(utc).toInstant().toEpochMilli(), best.nextAt(late, utc))
    }

    // ---------------------------------------------------------------- posts, calendar, status
    private fun post(status: PostStatus, at: Long?, published: Long? = null, remote: String? = null) =
        ScheduledPost("p", "g", 1, "YOUTUBE_UC1", ConnectPlatform.YOUTUBE, "T", at, published, status, remoteId = remote)

    @Test
    fun statusMovesOnlyAlongAllowedSteps() {
        assertTrue(canMove(PostStatus.QUEUED, PostStatus.UPLOADING))
        assertTrue(canMove(PostStatus.UPLOADING, PostStatus.PROCESSING))
        assertTrue(canMove(PostStatus.PROCESSING, PostStatus.LIVE))
        assertTrue(canMove(PostStatus.UPLOADING, PostStatus.AWAITING_PUBLIC)) // YouTube kept it private until Google approves
        assertTrue(canMove(PostStatus.FAILED, PostStatus.QUEUED)) // Retry
        assertFalse(canMove(PostStatus.LIVE, PostStatus.QUEUED))
        assertFalse(canMove(PostStatus.SHARED, PostStatus.FAILED))
        assertTrue(PostStatus.LIVE.isFinished() && !PostStatus.UPLOADING.isFinished())
    }

    @Test
    fun onlyAFuturePostThatHasNotGoneOutCanBeMoved() {
        val now = 1_000L
        assertTrue(canReschedule(post(PostStatus.SCHEDULED, 5_000), now))
        assertFalse(canReschedule(post(PostStatus.SCHEDULED, 500), now))
        assertFalse(canReschedule(post(PostStatus.LIVE, 5_000), now))
        assertFalse(canReschedule(post(PostStatus.FAILED, 5_000), now))
        assertFalse(canReschedule(post(PostStatus.SCHEDULED, null), now))
    }

    @Test
    fun movingAPost_keepsItsTimeOfDay() {
        val original = LocalDate.of(2026, 11, 10).atTime(18, 45).atZone(utc).toInstant().toEpochMilli()
        val moved = movedTo(post(PostStatus.SCHEDULED, original), LocalDate.of(2026, 11, 14), utc)
        assertEquals(LocalDate.of(2026, 11, 14).atTime(18, 45).atZone(utc).toInstant().toEpochMilli(), moved)
    }

    @Test
    fun aMonthGridHasWholeWeeks_startingOnTheChosenDay() {
        val weeks = monthGrid(LocalDate.of(2026, 11, 15), DayOfWeek.MONDAY)
        assertTrue(weeks.all { it.size == 7 && it.first().dayOfWeek == DayOfWeek.MONDAY })
        assertEquals(LocalDate.of(2026, 10, 26), weeks.first().first())
        assertTrue(weeks.last().contains(LocalDate.of(2026, 11, 30)))
        assertEquals(LocalDate.of(2026, 11, 1), monthGrid(LocalDate.of(2026, 11, 15), DayOfWeek.SUNDAY).first().first())
        assertEquals(weekOf(LocalDate.of(2026, 11, 12), DayOfWeek.MONDAY).first(), LocalDate.of(2026, 11, 9))
    }

    @Test
    fun postsAreGroupedByTheirDay_inTimeOrder() {
        val d = LocalDate.of(2026, 11, 10)
        val late = post(PostStatus.SCHEDULED, d.atTime(20, 0).atZone(utc).toInstant().toEpochMilli())
        val early = post(PostStatus.SCHEDULED, d.atTime(9, 0).atZone(utc).toInstant().toEpochMilli()).copy(id = "e")
        val live = post(PostStatus.LIVE, null, published = d.plusDays(1).atTime(12, 0).atZone(utc).toInstant().toEpochMilli()).copy(id = "l")
        val grouped = postsByDay(listOf(late, early, live), utc)
        assertEquals(listOf("e", "p"), grouped.getValue(d).map { it.id })
        assertEquals(listOf("l"), grouped.getValue(d.plusDays(1)).map { it.id })
    }

    @Test
    fun viewsAreReadEveryTwoHours_forTheFirst24Hours_only() {
        val hour = 3_600_000L
        val live = post(PostStatus.LIVE, null, published = 0L, remote = "vid")
        assertTrue(shouldPollViews(live, 3 * hour))
        assertFalse(shouldPollViews(live.copy(viewsAsOf = 2 * hour), 3 * hour)) // read an hour ago
        assertTrue(shouldPollViews(live.copy(viewsAsOf = hour), 3 * hour))
        assertFalse(shouldPollViews(live, 25 * hour)) // past the window
        assertFalse(shouldPollViews(live.copy(remoteId = null), hour))
        assertFalse(shouldPollViews(live.copy(status = PostStatus.FAILED), hour))
    }
}

class PublishPlanTest {
    private fun account(platform: ConnectPlatform, link: String? = null, id: String = "1") =
        com.creatora.app.domain.account.ConnectedAccount(platform, id, "Name", link = link)

    @Test
    fun youtubeIsPostedByApi_evenWhenScheduled_andOthersFollowTheirRules() {
        assertEquals(Route.API, routeFor(account(ConnectPlatform.YOUTUBE), scheduled = false))
        assertEquals(Route.API, routeFor(account(ConnectPlatform.YOUTUBE), scheduled = true)) // uploaded now with publishAt
        assertEquals(Route.API, routeFor(account(ConnectPlatform.TIKTOK), scheduled = false))
        assertEquals(Route.SHARE_SHEET, routeFor(account(ConnectPlatform.TIKTOK), scheduled = true)) // TikTok cannot hold a post
        assertEquals(Route.SHARE_SHEET, routeFor(account(ConnectPlatform.INSTAGRAM), scheduled = false))
        assertEquals(Route.SHARE_SHEET, routeFor(account(ConnectPlatform.FACEBOOK), scheduled = false))
        assertEquals(Route.SHARE_SHEET, routeFor(account(ConnectPlatform.YOUTUBE, link = "https://youtube.com/@x"), scheduled = false))
    }

    @Test
    fun oneDestinationPerAccount_shareStatesDependOnTheTime() {
        val now = 1_000L
        val planned = planPosts("g1", 7, "Title", listOf(account(ConnectPlatform.YOUTUBE), account(ConnectPlatform.INSTAGRAM)), scheduledAt = null, makePublic = false, now = now)
        assertEquals(listOf("g1_YOUTUBE_1", "g1_INSTAGRAM_1"), planned.map { it.post.id })
        assertEquals(listOf(Route.API, Route.SHARE_SHEET), planned.map { it.route })
        assertTrue(planned.all { it.post.status == PostStatus.QUEUED && it.post.privacy == "private" && it.post.postId == "g1" })

        val later = planPosts("g2", 7, "Title", listOf(account(ConnectPlatform.INSTAGRAM)), scheduledAt = 9_000L, makePublic = true, now = now).single()
        assertEquals(PostStatus.SCHEDULED, later.post.status) // a reminder is set for share-sheet posts
        assertEquals(9_000L, later.post.scheduledAt)
        assertEquals("public", later.post.privacy)
    }

    @Test
    fun theNoteUnderADestinationNeverHidesTheShareSheetOrTheReview() {
        assertEquals(DestinationNote.ShareSheet, noteFor(account(ConnectPlatform.INSTAGRAM), scheduled = false))
        assertEquals(DestinationNote.ReminderOnly, noteFor(account(ConnectPlatform.INSTAGRAM), scheduled = true))
        assertEquals(DestinationNote.PrivateUntilApproved, noteFor(account(ConnectPlatform.YOUTUBE), scheduled = false))
    }

    @Test
    fun titleScores_useOnlyMeasuredThings() {
        val good = scoreTitle("5 Python tricks every beginner needs", "python")
        assertEquals(100, good.points)
        assertTrue(TitleNote.HAS_KEYWORD in good.reasons)
        val shouting = scoreTitle("PYTHON TRICKS EVERYONE NEEDS NOW", "python")
        assertTrue(TitleNote.ALL_CAPS in shouting.reasons)
        assertTrue(shouting.points < good.points)
        assertTrue(TitleNote.TOO_SHORT in scoreTitle("Hi", null).reasons)
        assertTrue(TitleNote.TOO_LONG_FOR_SEARCH in scoreTitle("x".repeat(90), null).reasons)
        assertTrue(TitleNote.NO_KEYWORD in scoreTitle("A long enough title about nothing", "python").reasons)
    }
}
