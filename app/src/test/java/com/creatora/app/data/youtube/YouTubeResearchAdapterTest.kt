package com.creatora.app.data.youtube

import android.app.Application
import com.creatora.app.data.ai.FakeTransport
import com.creatora.app.data.ai.HttpResponse
import com.creatora.app.domain.account.AdapterError
import com.creatora.app.domain.account.AdapterOutcome
import com.creatora.app.testutil.FakeYouTubeAuthorizer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Data API calls Research uses: keyword search, channel discovery, featured channels, channel batches and comments. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class YouTubeResearchAdapterTest {
    private val transport = FakeTransport()
    private val adapter = YouTubeAdapter(transport, FakeYouTubeAuthorizer())

    private val channels = """{"items":[
        {"id":"UC1","snippet":{"title":"One"},"statistics":{"subscriberCount":"10","videoCount":"2","viewCount":"30"}},
        {"id":"UC2","snippet":{"title":"Two"},"statistics":{"hiddenSubscriberCount":true,"videoCount":"3","viewCount":"9"}}]}"""

    @Test
    fun keywordSearch_asksForTheTopResultsByRelevance_andReadsChannelsAndTags() = runTest {
        transport.getResponses["search?"] = HttpResponse(200, """{"items":[{"id":{"videoId":"v1"}},{"id":{"videoId":"v2"}}]}""")
        transport.getResponses["videos?"] = HttpResponse(
            200,
            """{"items":[{"id":"v1","snippet":{"title":"Dal","publishedAt":"2026-01-01T00:00:00Z","channelId":"UCx","tags":["dal","food"]},"statistics":{"viewCount":"500"}},
                {"id":"v2","snippet":{"title":"Rice","publishedAt":"2026-01-02T00:00:00Z","channelId":"UCy"},"statistics":{"viewCount":"40"}}]}""",
        )

        val result = (adapter.keywordSearch("dal recipe", 25) as AdapterOutcome.Ok).value

        assertTrue("order=relevance" in transport.getUrls[0] && "q=dal+recipe" in transport.getUrls[0] && "maxResults=25" in transport.getUrls[0])
        assertFalse("publishedAfter" in transport.getUrls[0]) // any age: the share of recent results is itself a signal
        assertTrue("id=v1,v2" in transport.getUrls[1])
        assertEquals("UCx", result[0].channelId)
        assertEquals(listOf("dal", "food"), result[0].tags)
        assertTrue(result[1].tags.isEmpty())
    }

    @Test
    fun searchChannels_thenReadsTheirPublicNumbers() = runTest {
        transport.getResponses["search?"] = HttpResponse(200, """{"items":[{"id":{"channelId":"UC1"}},{"id":{"channelId":"UC2"}}]}""")
        transport.getResponses["channels?"] = HttpResponse(200, channels)

        val result = (adapter.searchChannels("street food", 10) as AdapterOutcome.Ok).value

        assertTrue("type=channel" in transport.getUrls[0])
        assertTrue("id=UC1,UC2" in transport.getUrls[1])
        assertEquals(listOf("One", "Two"), result.map { it.title })
        assertEquals(null, result[1].subscribers)
    }

    @Test
    fun featuredChannels_readTheChannelSections_withoutDuplicates() = runTest {
        transport.getResponses["channelSections"] = HttpResponse(
            200,
            """{"items":[{"contentDetails":{"channels":["UC1","UC2"]}},{"contentDetails":{"channels":["UC2"]}},{"snippet":{"type":"allPlaylists"}}]}""",
        )
        transport.getResponses["channels?"] = HttpResponse(200, channels)

        val result = (adapter.featuredChannels("UCme") as AdapterOutcome.Ok).value

        assertTrue("channelId=UCme" in transport.getUrls[0])
        assertTrue("id=UC1,UC2" in transport.getUrls[1])
        assertEquals(2, result.size)
    }

    @Test
    fun aSearchThatFindsNothing_makesNoChannelCall() = runTest {
        transport.getResponses["search?"] = HttpResponse(200, """{"items":[]}""")

        assertEquals(AdapterOutcome.Ok(emptyList<Any>()), adapter.searchChannels("nothing", 5))
        assertEquals(1, transport.getUrls.size)
        assertEquals(AdapterOutcome.Ok(emptyList<Any>()), adapter.channels(emptyList()))
        assertEquals(1, transport.getUrls.size)
    }

    @Test
    fun topComments_arePlainText_andOffCommentsAreNotAvailable() = runTest {
        transport.getResponses["commentThreads"] = HttpResponse(
            200,
            """{"items":[{"snippet":{"topLevelComment":{"snippet":{"textDisplay":"Great tip"}}}},{"snippet":{"topLevelComment":{"snippet":{"textDisplay":" "}}}},
                {"snippet":{"topLevelComment":{"snippet":{"textDisplay":"Too long"}}}}]}""",
        )

        assertEquals(listOf("Great tip", "Too long"), (adapter.topComments("v1", 50) as AdapterOutcome.Ok).value)
        assertTrue("videoId=v1" in transport.getUrls[0] && "textFormat=plainText" in transport.getUrls[0] && "order=relevance" in transport.getUrls[0])

        transport.getResponses["commentThreads"] = HttpResponse(403, """{"error":{"message":"The video has disabled comments."}}""")
        assertEquals(AdapterOutcome.Failed(AdapterError.NotAvailable("The video has disabled comments.")), adapter.topComments("v1", 50))
    }

    @Test
    fun aSearchThatRunsOutOfQuota_isReportedAsSuch() = runTest {
        transport.getResponses["search?"] = HttpResponse(403, """{"error":{"errors":[{"reason":"quotaExceeded"}],"message":"quota"}}""")

        assertEquals(AdapterOutcome.Failed(AdapterError.QuotaExceeded), adapter.keywordSearch("dal", 25))
    }

    @Test
    fun theParsers_ignoreRowsWithoutTheirId() {
        assertEquals(listOf("UC1"), parseSearchChannelIds("""{"items":[{"id":{"channelId":"UC1"}},{"id":{"videoId":"v"}},{"id":{}}]}"""))
        assertEquals(emptyList<String>(), parseFeaturedChannelIds("""{"items":[]}"""))
        assertEquals(emptyList<String>(), parseComments("""{}"""))
    }
}
