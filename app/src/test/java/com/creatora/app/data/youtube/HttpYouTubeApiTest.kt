package com.creatora.app.data.youtube

import android.app.Application
import com.creatora.app.data.ai.FakeTransport
import com.creatora.app.data.ai.HttpResponse
import com.creatora.app.domain.research.ChannelQuery
import com.creatora.app.domain.research.YtError
import com.creatora.app.domain.research.YtOutcome
import com.creatora.app.testutil.FakeYouTubeKeyStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val SEARCH = """{"pageInfo":{"totalResults":54000},"items":[{"id":{"videoId":"v1"}},{"id":{"videoId":"v2"}},{"id":{"kind":"x"}}]}"""
private const val VIDEOS = """{"items":[
  {"id":"v1","snippet":{"title":"First","channelId":"c1","channelTitle":"Chan","description":"About it","thumbnails":{"medium":{"url":"https://img/1.jpg"}}},"statistics":{"viewCount":"12345"}},
  {"id":"v2","snippet":{"title":"Second","channelId":"c2","channelTitle":"Other","thumbnails":{"default":{"url":"https://img/2.jpg"}}},"statistics":{}},
  {"snippet":{"title":"No id"}}
]}"""
private const val CHANNELS = """{"items":[
  {"id":"c1","snippet":{"title":"Chan","thumbnails":{"default":{"url":"u"}}},"statistics":{"subscriberCount":"2500","viewCount":"900000","videoCount":"30"}},
  {"id":"c2","snippet":{"title":"Hidden"},"statistics":{"hiddenSubscriberCount":true,"subscriberCount":"0","viewCount":"10","videoCount":"2"}}
]}"""

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class HttpYouTubeApiTest {
    private val keys = FakeYouTubeKeyStore("MYKEY")

    private fun api(transport: FakeTransport, keyStore: FakeYouTubeKeyStore = keys) = HttpYouTubeApi(transport, keyStore)

    private fun <T> YtOutcome<T>.value(): T = (this as YtOutcome.Ok<T>).value

    @Test
    fun searchParsesCountAndLooksUpViews_withTheKeyOnlyInAHeader() = runTest {
        val urls = mutableListOf<String>()
        val transport = object : com.creatora.app.data.ai.HttpTransport {
            var headers = emptyMap<String, String>()
            override suspend fun post(url: String, headers: Map<String, String>, body: String) = error("unused")
            override suspend fun get(url: String, headers: Map<String, String>): HttpResponse {
                urls += url
                this.headers = headers
                return HttpResponse(200, if ("/search?" in url) SEARCH else VIDEOS)
            }
        }

        val result = HttpYouTubeApi(transport, keys).searchVideos("camera tips & more").value()

        assertEquals(54_000L, result.totalResults)
        assertEquals(listOf("First", "Second"), result.videos.map { it.title })
        assertEquals(12_345L, result.videos[0].views)
        assertEquals("https://img/1.jpg", result.videos[0].thumbnailUrl)
        assertEquals("About it", result.videos[0].description)
        assertEquals(0L, result.videos[1].views)
        assertEquals("https://img/2.jpg", result.videos[1].thumbnailUrl)
        assertTrue("q=camera+tips+%26+more" in urls[0])
        assertTrue("id=v1%2Cv2" in urls[1])
        assertEquals("MYKEY", transport.headers["X-Goog-Api-Key"])
        assertTrue(urls.none { "MYKEY" in it })
    }

    @Test
    fun noMatches_skipsTheSecondCall() = runTest {
        val transport = FakeTransport(HttpResponse(200, """{"pageInfo":{"totalResults":0},"items":[]}"""))

        val result = api(transport).searchVideos("nothing").value()

        assertEquals(0, result.videos.size)
        assertTrue("/search?" in transport.url)
    }

    @Test
    fun channelsParseStatisticsAndHiddenSubscribers() = runTest {
        val channels = api(FakeTransport(HttpResponse(200, CHANNELS))).channels(listOf("c1", "c2")).value()

        assertEquals(2_500L, channels[0].subscribers)
        assertEquals(30_000L, channels[0].averageViews)
        assertNull(channels[1].subscribers)
    }

    @Test
    fun findChannel_byHandleUsesForHandle_byIdUsesId() = runTest {
        val transport = FakeTransport(HttpResponse(200, CHANNELS))
        val api = api(transport)

        assertEquals("Chan", api.findChannel(ChannelQuery.Handle("@chan")).value()?.title)
        assertTrue("forHandle=%40chan" in transport.url)

        api.findChannel(ChannelQuery.Id("UCx"))
        assertTrue("id=UCx" in transport.url)
    }

    @Test
    fun findChannel_byNameSearchesThenLooksTheChannelUp() = runTest {
        val urls = mutableListOf<String>()
        val transport = object : com.creatora.app.data.ai.HttpTransport {
            override suspend fun post(url: String, headers: Map<String, String>, body: String) = error("unused")
            override suspend fun get(url: String, headers: Map<String, String>): HttpResponse {
                urls += url
                return HttpResponse(200, if ("/search?" in url) """{"items":[{"id":{"channelId":"c1"}}]}""" else CHANNELS)
            }
        }

        val channel = HttpYouTubeApi(transport, keys).findChannel(ChannelQuery.Name("Chan")).value()

        assertEquals("c1", channel?.id)
        assertTrue("type=channel" in urls[0] && "id=c1" in urls[1])
    }

    @Test
    fun videoAndTrending_parseVideos() = runTest {
        val transport = FakeTransport(HttpResponse(200, VIDEOS))
        val api = api(transport)

        assertEquals("First", api.video("v1").value()?.title)
        assertEquals(2, api.trending("IN").value().size)
        assertTrue("chart=mostPopular" in transport.url && "regionCode=IN" in transport.url)
    }

    @Test
    fun withoutAKey_nothingIsSent() = runTest {
        val transport = FakeTransport(HttpResponse(200, "{}"))

        val result = api(transport, FakeYouTubeKeyStore(null)).trending("US")

        assertEquals(YtOutcome.Failed(YtError.NoKey), result)
        assertEquals("", transport.url)
    }

    @Test
    fun httpStatusesBecomeTypedErrors() = runTest {
        val cases = listOf(
            Triple(400, """{"error":{"message":"API key not valid. Please pass a valid API key."}}""", YtError.InvalidKey),
            Triple(401, "", YtError.InvalidKey),
            Triple(403, """{"error":{"errors":[{"reason":"quotaExceeded"}]}}""", YtError.QuotaExceeded),
            Triple(403, """{"error":{"errors":[{"reason":"forbidden"}]}}""", YtError.InvalidKey),
            Triple(429, "", YtError.QuotaExceeded),
            Triple(503, "", YtError.Server(503)),
            Triple(400, "bad stuff", YtError.BadRequest("bad stuff")),
        )
        for ((code, body, expected) in cases) {
            val result = api(FakeTransport(HttpResponse(code, body))).trending("US")
            assertEquals("$code $body", YtOutcome.Failed(expected), result)
        }
    }

    @Test
    fun networkAndGarbageReplies_areReported() = runTest {
        assertEquals(YtOutcome.Failed(YtError.Network), api(FakeTransport(offline = true)).trending("US"))
        assertEquals(YtOutcome.Failed(YtError.BadResponse), api(FakeTransport(HttpResponse(200, "not json"))).trending("US"))
    }
}
