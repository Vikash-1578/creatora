package com.creatora.app.data.research

import android.app.Application
import com.creatora.app.domain.account.AdapterError
import com.creatora.app.domain.account.AdapterOutcome
import com.creatora.app.domain.account.ChannelPreview
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.account.PlatformRegistry
import com.creatora.app.domain.account.VideoAnalytics
import com.creatora.app.domain.account.VideoStats
import com.creatora.app.domain.research.Fetched
import com.creatora.app.domain.research.KeywordSearch
import com.creatora.app.testutil.FakeCacheRepository
import com.creatora.app.testutil.FakePlatformAdapter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val HOUR = 60L * 60 * 1000

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CachedResearchRepositoryTest {
    private val youtube = FakePlatformAdapter()
    private val cache = FakeCacheRepository()
    private var clock = 100 * HOUR
    private val repository = CachedResearchRepository(PlatformRegistry(listOf(youtube)), cache) { clock }

    private fun video(id: String, channelId: String = "c1", tags: List<String> = listOf("a", "b")) =
        VideoStats(id, "Video $id", "2026-01-01T00:00:00Z", 1_000, likes = 5, comments = null, channelTitle = "Chan", thumbnailUrl = "https://img/$id", channelId = channelId, tags = tags)

    private fun channel(id: String, subs: Long? = 100) = ChannelPreview(ConnectPlatform.YOUTUBE, id, "Channel $id", handle = "@$id", description = "d", photoUrl = "https://img/$id", subscribers = subs, videoCount = 10, viewCount = 999)

    @Test
    fun uploads_areFetchedOnce_thenServedFromTheCache_withAllTheirFields() = runTest {
        youtube.videos = AdapterOutcome.Ok(listOf(video("v1"), video("v2", tags = emptyList())))

        val first = repository.channelVideos("uid1", "UC1") as Fetched.Ok
        val second = repository.channelVideos("uid1", "UC1") as Fetched.Ok

        assertFalse(first.fromCache)
        assertTrue(second.fromCache)
        assertEquals(1, youtube.videoCalls.size) // the second open cost no quota
        assertEquals(first.value, second.value) // channel id, tags, null comments and thumbnail survive the round trip
        assertEquals(first.cachedAt, second.cachedAt)
    }

    @Test
    fun anEntryOlderThanItsTimeToLive_isFetchedAgain_andRefreshForcesIt() = runTest {
        youtube.videos = AdapterOutcome.Ok(listOf(video("v1")))
        repository.channelVideos("uid1", "UC1")

        clock += 23 * HOUR
        assertTrue((repository.channelVideos("uid1", "UC1") as Fetched.Ok).fromCache)
        clock += 2 * HOUR // 25 hours old: past the 24 hour time to live
        assertFalse((repository.channelVideos("uid1", "UC1") as Fetched.Ok).fromCache)
        assertEquals(2, youtube.videoCalls.size)

        repository.channelVideos("uid1", "UC1", force = true)
        assertEquals(3, youtube.videoCalls.size)
    }

    @Test
    fun whenYouTubeFails_anOlderCacheIsShownAsStale_andWithoutOneTheErrorIsReturned() = runTest {
        youtube.videos = AdapterOutcome.Failed(AdapterError.QuotaExceeded)
        assertEquals(Fetched.Failed(AdapterError.QuotaExceeded), repository.channelVideos("uid1", "UC1"))

        youtube.videos = AdapterOutcome.Ok(listOf(video("v1")))
        val saved = repository.channelVideos("uid1", "UC1") as Fetched.Ok
        clock += 48 * HOUR
        youtube.videos = AdapterOutcome.Failed(AdapterError.QuotaExceeded)

        val stale = repository.channelVideos("uid1", "UC1") as Fetched.Ok
        assertTrue(stale.stale && stale.fromCache)
        assertEquals(saved.cachedAt, stale.cachedAt) // "updated" shows how old the data really is
        assertEquals(saved.value, stale.value)
    }

    @Test
    fun peek_readsTheCacheOnly_evenWhenItIsOld() = runTest {
        assertNull(repository.peekChannelVideos("uid1", "UC1"))
        youtube.videos = AdapterOutcome.Ok(listOf(video("v1")))
        repository.channelVideos("uid1", "UC1")
        clock += 90 * HOUR

        assertEquals(listOf("v1"), repository.peekChannelVideos("uid1", "UC1")!!.map { it.id })
        assertEquals(1, youtube.videoCalls.size)
    }

    @Test
    fun channelInfo_isCached_andAnUnknownChannelIsNotFound() = runTest {
        assertEquals(Fetched.Failed(AdapterError.NotFound), repository.channelInfo("uid1", "UCx"))

        youtube.channelsResult = AdapterOutcome.Ok(listOf(channel("UC1")))
        val info = repository.channelInfo("uid1", "UC1") as Fetched.Ok
        assertEquals(channel("UC1"), info.value)
        assertTrue((repository.channelInfo("uid1", "UC1") as Fetched.Ok).fromCache)
        assertEquals(listOf(listOf("UCx"), listOf("UC1")), youtube.channelsCalls)
    }

    @Test
    fun discovery_searchesTheNiche_addsFeaturedChannels_andKeepsItForAWeek() = runTest {
        youtube.searchChannelsResult = AdapterOutcome.Ok(listOf(channel("A"), channel("B")))
        youtube.featuredResult = AdapterOutcome.Ok(listOf(channel("B"), channel("C")))

        val found = repository.discover("uid1", "street food", listOf("S1", "S2")) as Fetched.Ok

        assertEquals(listOf("A", "B", "C"), found.value.map { it.id }) // no duplicates
        assertEquals(listOf("street food"), youtube.channelSearchCalls)
        assertEquals(listOf("S1", "S2"), youtube.featuredCalls)

        clock += 6 * 24 * HOUR
        assertTrue((repository.discover("uid1", "street food", listOf("S1", "S2")) as Fetched.Ok).fromCache)
        clock += 2 * 24 * HOUR
        assertFalse((repository.discover("uid1", "street food", listOf("S1", "S2")) as Fetched.Ok).fromCache)
        assertEquals(2, youtube.channelSearchCalls.size)
    }

    @Test
    fun discovery_failsWithoutSpendingTheFeaturedCalls_whenTheSearchFails() = runTest {
        youtube.searchChannelsResult = AdapterOutcome.Failed(AdapterError.QuotaExceeded)

        assertEquals(Fetched.Failed(AdapterError.QuotaExceeded), repository.discover("uid1", "food", listOf("S1")))
        assertTrue(youtube.featuredCalls.isEmpty())
    }

    @Test
    fun keywords_carryTheResultChannelsSizes_andKeepFor12Hours() = runTest {
        youtube.keywordResult = AdapterOutcome.Ok(listOf(video("v1", "c1"), video("v2", "c2"), video("v3", "c1")))
        youtube.channelsResult = AdapterOutcome.Ok(listOf(channel("c1", 5_000), channel("c2", null)))

        val found = repository.keyword("uid1", "Dal Recipe") as Fetched.Ok

        assertEquals(listOf("c1", "c2"), youtube.channelsCalls.single()) // one call for all the channels
        assertEquals(5_000L, found.value.channelSubscribers["c1"])
        assertNull(found.value.channelSubscribers["c2"]) // hidden stays unknown
        assertEquals(3, found.value.videos.size)

        clock += 11 * HOUR
        assertTrue((repository.keyword("uid1", "dal recipe") as Fetched.Ok).fromCache) // same words, any case
        clock += 2 * HOUR
        assertFalse((repository.keyword("uid1", "dal recipe") as Fetched.Ok).fromCache)
    }

    @Test
    fun keywords_stillWork_whenTheChannelSizesCannotBeRead() = runTest {
        youtube.keywordResult = AdapterOutcome.Ok(listOf(video("v1", "c1")))
        youtube.channelsResult = AdapterOutcome.Failed(AdapterError.Network)

        val found = repository.keyword("uid1", "dal") as Fetched.Ok

        assertTrue(found.value.channelSubscribers.isEmpty())
    }

    @Test
    fun aKeywordSearchThatFails_isAnError_notAnEmptyResult() = runTest {
        youtube.keywordResult = AdapterOutcome.Failed(AdapterError.QuotaExceeded)

        assertEquals(Fetched.Failed(AdapterError.QuotaExceeded), repository.keyword("uid1", "other"))
        assertTrue(youtube.channelsCalls.isEmpty())
    }

    @Test
    fun comments_areCached_andCommentsThatAreOffAreAnError() = runTest {
        youtube.commentsResult = AdapterOutcome.Failed(AdapterError.NotAvailable("comments are off"))
        assertEquals(Fetched.Failed(AdapterError.NotAvailable("comments are off")), repository.comments("uid1", "v1"))

        youtube.commentsResult = AdapterOutcome.Ok(listOf("nice", "too long"))
        repository.comments("uid1", "v1")
        assertEquals(listOf("nice", "too long"), (repository.comments("uid1", "v1") as Fetched.Ok).value)
        assertEquals(2, youtube.commentCalls.size)
    }

    @Test
    fun ownAnalytics_isTheFirstRow_ornullWhenNotReturned() = runTest {
        assertNull(repository.ownAnalytics("UC1", "v1"))
        youtube.analyticsResult = AdapterOutcome.Ok(listOf(VideoAnalytics("v1", ctrPercent = 4.0)))
        assertEquals(4.0, repository.ownAnalytics("UC1", "v1")!!.ctrPercent!!, 0.0)
        youtube.analyticsResult = AdapterOutcome.Failed(AdapterError.NotAuthorized)
        assertNull(repository.ownAnalytics("UC1", "v1"))
    }

    @Test
    fun theCodecs_roundTripAndRejectGarbage() {
        val search = KeywordSearch(listOf(video("v1")), mapOf("c1" to 5L))

        assertEquals(search, decodeKeywordSearch(encodeKeywordSearch(search)))
        assertEquals(listOf(channel("a", null)), decodeChannels(encodeChannels(listOf(channel("a", null)))))
        assertEquals(listOf("q1", "q2"), decodeStrings(encodeStrings(listOf("q1", "q2"))))
        assertNull(decodeVideos("not json"))
        assertNull(decodeChannel("{}"))
        assertNotNull(decodeVideos("[]"))
    }
}
