package com.creatora.app.data.youtube

import android.app.Application
import com.creatora.app.data.ai.FakeTransport
import com.creatora.app.data.ai.HttpResponse
import com.creatora.app.domain.account.AdapterError
import com.creatora.app.domain.account.AdapterOutcome
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.account.PlatformRef
import com.creatora.app.domain.repository.AuthorizationOutcome
import com.creatora.app.testutil.FakeYouTubeAuthorizer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class YouTubeAdapterTest {
    private val channels = """{"items":[{"id":"UC1","snippet":{"title":"My channel","description":"Street food","customUrl":"@mine",
        "thumbnails":{"medium":{"url":"https://img/m.jpg"}}},"statistics":{"subscriberCount":"1200","videoCount":"20","viewCount":"50000"}},
        {"id":"UC2","snippet":{"title":"Hidden"},"statistics":{"hiddenSubscriberCount":true,"videoCount":"3","viewCount":"9"}}]}"""
    private val playlist = """{"items":[{"contentDetails":{"videoId":"v1"}},{"contentDetails":{"videoId":"v2"}}]}"""
    private val videos = """{"items":[{"id":"v1","snippet":{"title":"One","publishedAt":"2026-01-01T00:00:00Z"},"statistics":{"viewCount":"100","likeCount":"7","commentCount":"2"}},
        {"id":"v2","snippet":{"title":"Two","publishedAt":"2026-01-02T00:00:00Z"},"statistics":{"viewCount":"40"}}]}"""

    private val transport = FakeTransport()
    private val authorizer = FakeYouTubeAuthorizer()
    private val adapter = YouTubeAdapter(transport, authorizer, com.creatora.app.testutil.FakeErrorReporter())

    @Test
    fun ownChannels_readsEveryChannel_andSendsTheToken() = runTest {
        transport.getResponses["channels?"] = HttpResponse(200, channels)

        val result = (adapter.ownChannels() as AdapterOutcome.Ok).value

        assertEquals(listOf("My channel", "Hidden"), result.map { it.title })
        assertEquals(1_200L, result[0].subscribers)
        assertEquals("@mine", result[0].handle)
        assertEquals("https://img/m.jpg", result[0].photoUrl)
        assertNull(result[1].subscribers) // hidden counts stay unknown, never 0
        assertTrue("mine=true" in transport.getUrls.single())
        assertEquals("Bearer token", transport.getHeaders["Authorization"])
    }

    @Test
    fun withoutPermission_nothingIsFetched() = runTest {
        authorizer.outcome = AuthorizationOutcome.Denied

        assertEquals(AdapterOutcome.Failed(AdapterError.NotAuthorized), adapter.ownChannels())
        assertTrue(transport.getUrls.isEmpty())
    }

    @Test
    fun lookup_usesTheRightSelector() = runTest {
        transport.getResponses["channels?"] = HttpResponse(200, channels)

        adapter.lookup(PlatformRef(ConnectPlatform.YOUTUBE, "@mine", null))
        adapter.lookup(PlatformRef(ConnectPlatform.YOUTUBE, "UCabcdefghijklmnopqrstuv", null))
        adapter.lookup(PlatformRef(ConnectPlatform.YOUTUBE, "plainname", null))

        assertTrue("forHandle=%40mine" in transport.getUrls[0])
        assertTrue("id=UCabcdefghijklmnopqrstuv" in transport.getUrls[1])
        assertTrue("forHandle=%40plainname" in transport.getUrls[2])
    }

    @Test
    fun lookup_withNoItems_isNotFound() = runTest {
        transport.getResponses["channels?"] = HttpResponse(200, """{"items":[]}""")
        assertEquals(AdapterOutcome.Failed(AdapterError.NotFound), adapter.lookup(PlatformRef(ConnectPlatform.YOUTUBE, "@nobody", null)))
    }

    @Test
    fun recentVideos_usesTheUploadsPlaylist_andKeepsMissingCountsNull() = runTest {
        transport.getResponses["playlistItems"] = HttpResponse(200, playlist)
        transport.getResponses["videos?"] = HttpResponse(200, videos)

        val result = (adapter.recentVideos("UCabc", 10) as AdapterOutcome.Ok).value

        assertTrue("playlistId=UUabc" in transport.getUrls[0])
        assertTrue("id=v1,v2" in transport.getUrls[1])
        assertEquals(listOf(100L, 40L), result.map { it.views })
        assertEquals(7L, result[0].likes)
        assertNull(result[1].likes)
        assertNull(result[1].comments)
    }

    @Test
    fun analytics_mergesMetrics_andKeepsGoingWhenRevenueIsRefused() = runTest {
        transport.getResponses["metrics=views,averageViewDuration"] = HttpResponse(
            200,
            """{"columnHeaders":[{"name":"video"},{"name":"views"},{"name":"averageViewDuration"},{"name":"averageViewPercentage"},{"name":"videoThumbnailImpressions"},{"name":"videoThumbnailImpressionsClickRate"}],
                "rows":[["v1",100,45.5,30.2,5000,2.4]]}""",
        )
        transport.getResponses["metrics=videoThumbnailImpressions"] = HttpResponse(
            200, """{"columnHeaders":[{"name":"video"},{"name":"videoThumbnailImpressions"},{"name":"videoThumbnailImpressionsClickRate"}],"rows":[["v1",5000,2.4]]}""",
        )
        transport.getResponses["metrics=estimatedRevenue"] = HttpResponse(403, """{"error":{"message":"Forbidden"}}""")
        transport.getResponses["insightTrafficSourceType"] = HttpResponse(
            200, """{"columnHeaders":[{"name":"insightTrafficSourceType"},{"name":"views"}],"rows":[["YT_SEARCH",60],["SUGGESTED",40]]}""",
        )

        val result = (adapter.analytics("UC1", listOf("v1")) as AdapterOutcome.Ok).value.single()

        assertEquals(2.4, result.ctrPercent!!, 0.001)
        assertEquals(30.2, result.averageViewPercent!!, 0.001)
        assertEquals(45.5, result.averageViewSeconds!!, 0.001)
        assertNull(result.estimatedRevenue) // not monetized or no access: unknown, never zero
        assertEquals(mapOf("YT_SEARCH" to 60L, "SUGGESTED" to 40L), result.trafficSources)
    }

    @Test
    fun errors_areMappedHonestly() = runTest {
        transport.getResponses["channels?"] = HttpResponse(403, """{"error":{"errors":[{"reason":"quotaExceeded"}],"message":"The request cannot be completed because you have exceeded your quota."}}""")
        assertEquals(AdapterOutcome.Failed(AdapterError.QuotaExceeded), adapter.ownChannels())

        transport.getResponses["channels?"] = HttpResponse(401, "")
        assertEquals(AdapterOutcome.Failed(AdapterError.NotAuthorized), adapter.ownChannels())

        transport.getResponses["channels?"] = HttpResponse(200, "not json")
        assertTrue((adapter.ownChannels() as AdapterOutcome.Failed).error is AdapterError.NotAvailable)

        transport.offline = true
        assertEquals(AdapterOutcome.Failed(AdapterError.Network), adapter.ownChannels())
    }

    @Test
    fun channelMetrics_readsTheTotals_andLeavesRevenueUnknownWhenRefused() = runTest {
        transport.getResponses["estimatedRevenue"] = HttpResponse(403, """{"error":{"message":"Forbidden"}}""")
        transport.getResponses["metrics=views,estimatedMinutesWatched"] = HttpResponse(
            200,
            """{"columnHeaders":[{"name":"views"},{"name":"estimatedMinutesWatched"},{"name":"subscribersGained"},{"name":"subscribersLost"}],"rows":[[1200,3600,40,5]]}""",
        )
        val start = java.time.LocalDate.of(2026, 1, 1)
        val end = java.time.LocalDate.of(2026, 1, 28)

        val metrics = (adapter.channelMetrics("UC1", start, end) as AdapterOutcome.Ok).value

        assertEquals(listOf(1_200L, 3_600L, 40L, 5L), listOf(metrics.views, metrics.watchMinutes, metrics.subscribersGained, metrics.subscribersLost))
        assertEquals(35L, metrics.netSubscribers)
        assertNull(metrics.revenueUsd)
        assertTrue("ids=channel==UC1" in transport.getUrls[0] && "startDate=2026-01-01&endDate=2026-01-28" in transport.getUrls[0])
    }

    @Test
    fun channelMetrics_addsRevenueWhenReturned_andIsNotAvailableWithoutRows() = runTest {
        transport.getResponses["metrics=estimatedRevenue"] = HttpResponse(200, """{"columnHeaders":[{"name":"estimatedRevenue"}],"rows":[[12.5]]}""")
        transport.getResponses["metrics=views,estimatedMinutesWatched"] = HttpResponse(
            200, """{"columnHeaders":[{"name":"views"},{"name":"estimatedMinutesWatched"},{"name":"subscribersGained"},{"name":"subscribersLost"}],"rows":[[1,2,3,4]]}""",
        )
        val day = java.time.LocalDate.of(2026, 1, 1)
        assertEquals(12.5, (adapter.channelMetrics("UC1", day, day) as AdapterOutcome.Ok).value.revenueUsd!!, 0.001)

        transport.getResponses["metrics=views,estimatedMinutesWatched"] = HttpResponse(200, """{"columnHeaders":[],"rows":[]}""")
        assertTrue((adapter.channelMetrics("UC1", day, day) as AdapterOutcome.Failed).error is AdapterError.NotAvailable)
    }

    @Test
    fun trending_asksForTheRegionAndCategory_andKeepsTheChannelAndPicture() = runTest {
        transport.getResponses["chart=mostPopular"] = HttpResponse(
            200,
            """{"items":[{"id":"t1","snippet":{"title":"Hot","publishedAt":"2026-01-01T00:00:00Z","channelTitle":"Big channel","thumbnails":{"medium":{"url":"https://img/t.jpg"}}},"statistics":{"viewCount":"900"}}]}""",
        )

        val items = (adapter.trending("IN", "20", 5) as AdapterOutcome.Ok).value

        assertTrue("regionCode=IN" in transport.getUrls[0] && "videoCategoryId=20" in transport.getUrls[0] && "maxResults=5" in transport.getUrls[0])
        assertEquals("Big channel", items.single().channelTitle)
        assertEquals("https://img/t.jpg", items.single().thumbnailUrl)
        adapter.trending("US", null, 5)
        assertTrue("videoCategoryId" !in transport.getUrls[1])
    }

    @Test
    fun searchVideos_findsIdsThenLoadsTheirNumbers() = runTest {
        transport.getResponses["/search?"] = HttpResponse(200, """{"items":[{"id":{"videoId":"s1"}},{"id":{"videoId":"s2"}}]}""")
        transport.getResponses["/videos?"] = HttpResponse(200, videos)

        val result = (adapter.searchVideos("street food", 10) as AdapterOutcome.Ok).value

        assertTrue("q=street+food" in transport.getUrls[0] && "order=viewCount" in transport.getUrls[0] && "publishedAfter=" in transport.getUrls[0])
        assertTrue("id=s1,s2" in transport.getUrls[1])
        assertEquals(2, result.size)

        transport.getResponses["/search?"] = HttpResponse(200, """{"items":[]}""")
        assertEquals(emptyList<com.creatora.app.domain.account.VideoStats>(), (adapter.searchVideos("nothing", 10) as AdapterOutcome.Ok).value)
    }

    @Test
    fun videoMetrics_returnsOnlyTheCoreNumbers() = runTest {
        transport.getResponses["metrics=views,averageViewDuration"] = HttpResponse(
            200,
            """{"columnHeaders":[{"name":"video"},{"name":"views"},{"name":"averageViewDuration"},{"name":"averageViewPercentage"},{"name":"videoThumbnailImpressions"},{"name":"videoThumbnailImpressionsClickRate"}],"rows":[["v1",10,30.0,40.0,100,3.1]]}""",
        )

        val result = (adapter.videoMetrics("UC1", listOf("v1")) as AdapterOutcome.Ok).value.single()

        assertEquals(3.1, result.ctrPercent!!, 0.001)
        assertEquals(1, transport.getUrls.size) // no revenue or traffic calls
        assertTrue(result.trafficSources.isEmpty())
    }
}
