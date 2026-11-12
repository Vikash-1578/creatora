package com.creatora.app.domain.grow

import com.creatora.app.domain.account.AdapterError
import com.creatora.app.domain.account.AdapterOutcome
import com.creatora.app.domain.account.ChannelPreview
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.account.ConnectedAccount
import com.creatora.app.domain.account.PlatformRegistry
import com.creatora.app.domain.account.VideoStats
import com.creatora.app.domain.home.ChannelOverviewLoader
import com.creatora.app.testutil.FakePlatformAdapter
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GrowLoaderTest {
    private val today = LocalDate.of(2026, 11, 12)
    private val asOf = LocalDate.of(2026, 11, 10)
    private var now = Instant.parse("2026-11-12T10:00:00Z").toEpochMilli()
    private val youtube = FakePlatformAdapter()
    private val registry = PlatformRegistry(listOf(youtube))
    private val loader = GrowLoader(registry, ChannelOverviewLoader(registry) { now }, { today }, { now })
    private val account = ConnectedAccount(ConnectPlatform.YOUTUBE, "UC1", "Asha", subscribers = 600)

    private fun dayRow(day: LocalDate, views: Long, minutes: Long = 0, gained: Long = 0, lost: Long = 0) =
        mapOf<String, Any?>("day" to day.toString(), "views" to views.toDouble(), "estimatedMinutesWatched" to minutes.toDouble(), "subscribersGained" to gained.toDouble(), "subscribersLost" to lost.toDouble())

    @Test
    fun overview_endsTwoDaysBack_andCoversSevenPeriods_forTheUsual() = runTest {
        // 49 days: 10 views a day, 20 a day in the latest week.
        youtube.reports[Reports.daily] = AdapterOutcome.Ok((0 until 49).map { i -> dayRow(asOf.minusDays(48L - i), if (i >= 42) 20 else 10, minutes = 60, gained = 3, lost = 1) })

        val overview = (loader.overview(account, Period.WEEK) as AdapterOutcome.Ok).value

        assertEquals(asOf, overview.asOf) // Analytics runs one to two days behind
        assertEquals(Triple(Reports.daily, asOf.minusDays(48), asOf), youtube.reportCalls.single())
        assertEquals(140L, overview.views.total)
        assertEquals(listOf(140L, 70L, 70L, 70L, 70L, 70L, 70L), overview.views.sums)
        assertEquals(7, overview.views.daily.size)
        assertEquals(14L, overview.subscribersNet.total) // gained minus lost
        assertEquals(70.0, usual(overview.views.sums)!!, 0.0)
        assertEquals(7, overview.daysOfData)
    }

    @Test
    fun reportsAreKeptInMemory_andRefreshAsksAgain() = runTest {
        youtube.reports[Reports.daily] = AdapterOutcome.Ok(listOf(dayRow(asOf, 5)))
        loader.overview(account, Period.WEEK)
        loader.overview(account, Period.WEEK)
        assertEquals(1, youtube.reportCalls.size)

        loader.overview(account, Period.WEEK, force = true)
        assertEquals(2, youtube.reportCalls.size)

        now += 7L * 60 * 60 * 1000 // older than six hours
        loader.overview(account, Period.WEEK)
        assertEquals(3, youtube.reportCalls.size)
    }

    @Test
    fun aRefusedReport_isPassedOnAsItIs() = runTest {
        youtube.reports[Reports.daily] = AdapterOutcome.Failed(AdapterError.PermissionMissing)
        assertEquals(AdapterOutcome.Failed(AdapterError.PermissionMissing), loader.overview(account, Period.MONTH))
        assertTrue(loader.content(account, Period.MONTH) is AdapterOutcome.Failed)
        assertTrue(loader.audience(account, Period.MONTH) is AdapterOutcome.Failed)
    }

    @Test
    fun content_givesSharesOfViews_andLeavesRefusedReportsEmpty() = runTest {
        youtube.reports[Reports.traffic] = AdapterOutcome.Ok(listOf(
            mapOf("insightTrafficSourceType" to "SHORTS", "views" to 600.0), mapOf("insightTrafficSourceType" to "YT_SEARCH", "views" to 400.0),
        ))
        youtube.videos = AdapterOutcome.Ok(listOf(VideoStats("v1", "Python tricks", "2026-11-01T10:00:00Z", 900)))
        youtube.reports[Reports.topVideos] = AdapterOutcome.Ok(listOf(mapOf("video" to "v1", "views" to 500.0), mapOf("video" to "v9", "views" to 100.0)))

        val content = (loader.content(account, Period.MONTH) as AdapterOutcome.Ok).value

        assertEquals(listOf(Share("SHORTS", 60.0), Share("YT_SEARCH", 40.0)), content.traffic)
        assertEquals(listOf("Python tricks" to 500L, "v9" to 100L), content.topVideos) // an unknown video keeps its id
        assertTrue(content.searchTerms.isEmpty()) // refused: the card says so, nothing is invented
    }

    @Test
    fun audience_readsAgeGenderAndWeekdays() = runTest {
        youtube.reports[Reports.ageGender] = AdapterOutcome.Ok(listOf(
            mapOf("ageGroup" to "age18-24", "gender" to "male", "viewerPercentage" to 60.0), mapOf("ageGroup" to "age25-34", "gender" to "female", "viewerPercentage" to 40.0),
        ))
        // The week ending 2026-11-10 (a Tuesday): all the views were on that Tuesday.
        youtube.reports[Reports.daily] = AdapterOutcome.Ok(listOf(dayRow(asOf, 100)))

        val audience = (loader.audience(account, Period.WEEK) as AdapterOutcome.Ok).value

        assertEquals(listOf(Share("age18-24 male", 60.0), Share("age25-34 female", 40.0)), audience.ageGender)
        assertEquals(Share("TUESDAY", 100.0), audience.weekdays.first())
        assertTrue(audience.countries.isEmpty())
    }

    @Test
    fun earnings_addUpTheDays_compareWithThePeriodBefore_andNeverShowZeroWhenRefused() = runTest {
        assertTrue(loader.earnings(account, Period.WEEK) is AdapterOutcome.Failed) // not monetized: YouTube refuses the report

        youtube.reports[Reports.dailyRevenue] = AdapterOutcome.Ok((0 until 14).map { i -> mapOf<String, Any?>("day" to asOf.minusDays(13L - i).toString(), "estimatedRevenue" to if (i >= 7) 2.0 else 1.0) })
        youtube.reports[Reports.daily] = AdapterOutcome.Ok((0 until 7).map { dayRow(asOf.minusDays(6L - it), 1_000) })

        val earnings = (GrowLoader(registry, ChannelOverviewLoader(registry) { now }, { today }, { now }).earnings(account, Period.WEEK) as AdapterOutcome.Ok).value

        assertEquals(14.0, earnings.revenue, 0.0001)
        assertEquals(7.0, earnings.previousRevenue!!, 0.0001)
        assertEquals(7_000L, earnings.views)
        assertEquals(2.0, earnings.rpm!!, 0.0001)

        youtube.reports[Reports.dailyRevenue] = AdapterOutcome.Ok(emptyList())
        assertTrue(GrowLoader(registry, ChannelOverviewLoader(registry) { now }, { today }, { now }).earnings(account, Period.WEEK) is AdapterOutcome.Failed)
    }

    @Test
    fun monetizationInputs_comeFromTheApi_andStayUnknownWhenRefused() = runTest {
        youtube.lookupResult = AdapterOutcome.Ok(ChannelPreview(ConnectPlatform.YOUTUBE, "UC1", "Asha", subscribers = 640))
        youtube.videos = AdapterOutcome.Ok(listOf(
            VideoStats("a", "A", Instant.ofEpochMilli(now).minusSeconds(10L * 86_400).toString(), 10),
            VideoStats("b", "B", Instant.ofEpochMilli(now).minusSeconds(200L * 86_400).toString(), 10),
        ))
        youtube.reports[Reports.watchTime] = AdapterOutcome.Ok(listOf(mapOf("estimatedMinutesWatched" to 138_600.0)))
        youtube.reports[Reports.contentType] = AdapterOutcome.Ok(listOf(mapOf("creatorContentType" to "SHORTS", "views" to 1_200_000.0), mapOf("creatorContentType" to "VIDEO_ON_DEMAND", "views" to 5.0)))

        val inputs = loader.monetization(account)

        assertEquals(MonetizationInputs(640, 1, 2_310.0, 1_200_000), inputs)

        val bare = FakePlatformAdapter()
        val bareRegistry = PlatformRegistry(listOf(bare))
        val unknown = GrowLoader(bareRegistry, ChannelOverviewLoader(bareRegistry) { now }, { today }, { now }).monetization(account)
        assertNull(unknown.watchHours365)
        assertNull(unknown.shortsViews90)
        assertEquals(600L, unknown.subscribers) // the count saved with the account
    }

    @Test
    fun reportHelpers_fillMissingDays_andSkipBrokenRows() {
        val rows = listOf(mapOf<String, Any?>("day" to "2026-11-02", "views" to 7.0), mapOf("day" to "nonsense", "views" to 1.0))
        assertEquals(listOf(0L, 7L, 0L), dailyColumn(rows, "views", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3)))
        assertEquals(mapOf("a" to 1.0), labelled(listOf(mapOf("k" to "a", "v" to 1.0), mapOf("k" to "b"), mapOf("v" to 2.0)), listOf("k"), "v"))
    }
}
