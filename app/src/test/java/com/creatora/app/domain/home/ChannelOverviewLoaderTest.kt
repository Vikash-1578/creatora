package com.creatora.app.domain.home

import com.creatora.app.domain.account.AdapterError
import com.creatora.app.domain.account.AdapterOutcome
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.account.ConnectedAccount
import com.creatora.app.domain.account.PeriodMetrics
import com.creatora.app.domain.account.PlatformRegistry
import com.creatora.app.testutil.FakePlatformAdapter
import com.creatora.app.testutil.preview
import com.creatora.app.testutil.sampleVideos
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelOverviewLoaderTest {
    private val youtube = FakePlatformAdapter()
    private var clock = 1_000_000L
    private val loader = ChannelOverviewLoader(PlatformRegistry(listOf(youtube))) { clock }
    private val account = ConnectedAccount(ConnectPlatform.YOUTUBE, "UC1", "My channel")

    @Test
    fun overview_gathersTheChannel_itsVideos_andBothPeriods() = runTest {
        youtube.lookupResult = AdapterOutcome.Ok(preview("UC1", subs = 1_500))
        youtube.videos = AdapterOutcome.Ok(sampleVideos(6))
        youtube.metrics = AdapterOutcome.Ok(PeriodMetrics(1_000, 600, 20, 5))
        youtube.previousMetrics = AdapterOutcome.Ok(PeriodMetrics(800, 500, 10, 5))

        val overview = loader.overview(account)

        assertEquals(1_500L, overview.channel?.subscribers)
        assertEquals(6, overview.videos.size)
        assertEquals(1_000L, overview.current?.views)
        assertEquals(800L, overview.previous?.views)
        assertNull(overview.error)
        // The current period is the last 28 days, the previous one the 28 days before it.
        val (currentStart, currentEnd) = youtube.metricCalls[0]
        val (previousStart, previousEnd) = youtube.metricCalls[1]
        assertEquals(27L, java.time.temporal.ChronoUnit.DAYS.between(currentStart, currentEnd))
        assertEquals(1L, java.time.temporal.ChronoUnit.DAYS.between(previousEnd, currentStart))
        assertEquals(27L, java.time.temporal.ChronoUnit.DAYS.between(previousStart, previousEnd))
    }

    @Test
    fun overview_isKeptForFifteenMinutes_soTheCardsShareOneSetOfCalls() = runTest {
        youtube.videos = AdapterOutcome.Ok(sampleVideos(4))
        loader.overview(account)
        loader.overview(account)
        assertEquals(1, youtube.lookups.size)

        clock += 14 * 60 * 1000
        loader.overview(account)
        assertEquals(1, youtube.lookups.size)

        clock += 2 * 60 * 1000
        loader.overview(account)
        assertEquals(2, youtube.lookups.size)

        loader.overview(account, force = true)
        assertEquals(3, youtube.lookups.size)
    }

    @Test
    fun aVideoFailure_isReported_andAnAnalyticsFailureKeepsTheRest() = runTest {
        youtube.videos = AdapterOutcome.Failed(AdapterError.QuotaExceeded)
        assertEquals(AdapterError.QuotaExceeded, loader.overview(account, force = true).error)

        youtube.videos = AdapterOutcome.Ok(sampleVideos(4))
        youtube.metrics = AdapterOutcome.Failed(AdapterError.NotAuthorized)
        val partial = loader.overview(account, force = true)
        assertNull(partial.error)
        assertEquals(AdapterError.NotAuthorized, partial.metricsError)
        assertNull(partial.current)
        assertEquals(4, partial.videos.size)
    }

    @Test
    fun accountsOfOtherPlatforms_areSaidToBeUnavailable_withoutAnyCalls() = runTest {
        val instagram = ConnectedAccount(ConnectPlatform.INSTAGRAM, "x", "Insta")
        val link = ConnectedAccount(ConnectPlatform.MOJ, "y", "Moj", link = "https://mojapp.in/@y")

        assertTrue(loader.overview(instagram).error is AdapterError.NotAvailable)
        assertTrue(loader.overview(link).error is AdapterError.NotAvailable)
        assertTrue(youtube.lookups.isEmpty())
    }

    @Test
    fun uploadMetrics_areAskedForOnce_perSetOfVideos() = runTest {
        youtube.videoMetricsResult = AdapterOutcome.Ok(listOf(com.creatora.app.domain.account.VideoAnalytics("v0", ctrPercent = 2.0)))
        assertEquals(2.0, loader.uploadMetrics(account, listOf("v0")).single().ctrPercent!!, 0.001)
        loader.uploadMetrics(account, listOf("v0"))
        assertEquals(emptyList<Any>(), loader.uploadMetrics(ConnectedAccount(ConnectPlatform.INSTAGRAM, "x", "I"), listOf("v0")))
    }
}
