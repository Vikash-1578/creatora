package com.creatora.app.ui.research

import com.creatora.app.domain.research.Competition
import com.creatora.app.domain.research.Competitor
import com.creatora.app.domain.research.VideoSearch
import com.creatora.app.domain.research.YtChannel
import com.creatora.app.domain.research.YtError
import com.creatora.app.domain.research.YtOutcome
import com.creatora.app.domain.research.YtVideo
import com.creatora.app.testutil.FakeCompetitorRepository
import com.creatora.app.testutil.FakeIdeaRepository
import com.creatora.app.testutil.FakeSwipeRepository
import com.creatora.app.testutil.FakeYouTubeApi
import com.creatora.app.testutil.FakeYouTubeKeyStore
import com.creatora.app.testutil.MainDispatcherRule
import com.creatora.app.testutil.keepActive
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ResearchViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val api = FakeYouTubeApi()
    private val keys = FakeYouTubeKeyStore("key")
    private val competitors = FakeCompetitorRepository()
    private val swipe = FakeSwipeRepository()
    private val ideas = FakeIdeaRepository()

    private fun video(id: String, channel: String = "c1", views: Long = 100) = YtVideo(id, "Title $id", channel, "Chan $channel", views, null)
    private fun channel(id: String, views: Long = 1_000, videos: Long = 10) = YtChannel(id, "Chan $id", 500, views, videos, null)

    private fun TestScope.open(): ResearchViewModel {
        val viewModel = ResearchViewModel(api, keys, competitors, swipe, ideas)
        keepActive(viewModel.uiState)
        return viewModel
    }

    @Test
    fun reportsWhetherAKeyIsSaved() = runTest {
        val noKey = ResearchViewModel(api, FakeYouTubeKeyStore(null), competitors, swipe, ideas)
        keepActive(noKey.uiState)
        assertFalse(noKey.uiState.value.hasKey)
        assertTrue(open().uiState.value.hasKey)
    }

    @Test
    fun keywordSearch_showsMatchCountCompetitionAndVideos() = runTest {
        api.search = YtOutcome.Ok(VideoSearch(54_000, listOf(video("a"), video("b"))))
        val viewModel = open()

        viewModel.setQuery("  camera tips ")
        viewModel.search()

        val result = viewModel.uiState.value.keywords!!
        assertEquals("camera tips", result.query)
        assertEquals(Competition.MEDIUM, result.competition)
        assertEquals(listOf("a", "b"), result.videos.map { it.id })
        assertFalse(viewModel.uiState.value.loading)
        assertEquals(listOf("search:camera tips"), api.calls)
    }

    @Test
    fun blankQuery_doesNothing() = runTest {
        val viewModel = open()
        viewModel.setQuery("   ")
        viewModel.search()
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun outlierSearch_comparesViewsWithEachChannelsAverage() = runTest {
        api.search = YtOutcome.Ok(VideoSearch(10, listOf(video("hit", "c1", views = 5_000), video("normal", "c2", views = 100))))
        api.channelList = YtOutcome.Ok(listOf(channel("c1", views = 1_000, videos = 10), channel("c2", views = 1_000, videos = 10)))
        val viewModel = open()
        viewModel.selectTab(ResearchTab.OUTLIERS)

        viewModel.setQuery("topic")
        viewModel.search()

        val outliers = viewModel.uiState.value.outliers!!
        assertEquals(listOf("hit"), outliers.map { it.video.id })
        assertEquals(50.0, outliers.single().multiple, 0.0001)
        assertEquals(listOf("search:topic", "channels:c1,c2"), api.calls)
    }

    @Test
    fun apiErrors_areShownAndClearedOnTheNextSearch() = runTest {
        api.search = YtOutcome.Failed(YtError.QuotaExceeded)
        val viewModel = open()
        viewModel.setQuery("x")
        viewModel.search()
        assertEquals(YtError.QuotaExceeded, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.loading)

        api.search = YtOutcome.Ok(VideoSearch(1, emptyList()))
        viewModel.search()
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun rivals_findATrackedChannelOnce() = runTest {
        api.channel = YtOutcome.Ok(channel("c9"))
        val viewModel = open()
        viewModel.selectTab(ResearchTab.RIVALS)
        viewModel.setQuery("@someone")
        viewModel.search()
        val found = viewModel.uiState.value.foundChannel!!
        assertTrue(viewModel.uiState.value.channelSearched)

        viewModel.track(found)
        viewModel.track(found)

        assertEquals(1, viewModel.uiState.value.competitors.size)
        assertEquals("c9", viewModel.uiState.value.competitors.single().channelId)
        assertEquals(ResearchNotice.ALREADY_TRACKED, viewModel.uiState.value.notice)
        viewModel.noticeShown()
        assertNull(viewModel.uiState.value.notice)
    }

    @Test
    fun rivals_aSearchWithNoChannelIsMarkedAsSearched() = runTest {
        val viewModel = open()
        viewModel.selectTab(ResearchTab.RIVALS)
        viewModel.setQuery("nobody")
        viewModel.search()

        assertNull(viewModel.uiState.value.foundChannel)
        assertTrue(viewModel.uiState.value.channelSearched)
    }

    @Test
    fun refreshCompetitors_updatesTheirNumbersWithOneCall() = runTest {
        competitors.save(Competitor(channelId = "c1", title = "Old", subscribers = 1, views = 1, videos = 1))
        competitors.save(Competitor(channelId = "c2", title = "Two", subscribers = 1, views = 1, videos = 1))
        api.channelList = YtOutcome.Ok(listOf(channel("c1", views = 9_000, videos = 3)))
        val viewModel = open()

        viewModel.refreshCompetitors()

        val updated = competitors.items.value.single { it.channelId == "c1" }
        assertEquals("Chan c1", updated.title)
        assertEquals(9_000L, updated.views)
        assertEquals(listOf("channels:c1,c2"), api.calls)
    }

    @Test
    fun untrack_removesTheCompetitor() = runTest {
        val id = competitors.save(Competitor(channelId = "c1", title = "T", subscribers = null, views = 0, videos = 0))
        val viewModel = open()

        viewModel.untrack(competitors.items.value.single { it.id == id })

        assertTrue(viewModel.uiState.value.competitors.isEmpty())
    }

    @Test
    fun trends_loadOnceWhenTheTabOpens() = runTest {
        api.trendingList = YtOutcome.Ok(listOf(video("t1")))
        val viewModel = open()

        viewModel.selectTab(ResearchTab.TRENDS)
        viewModel.selectTab(ResearchTab.KEYWORDS)
        viewModel.selectTab(ResearchTab.TRENDS)

        assertEquals(listOf("t1"), viewModel.uiState.value.trending?.map { it.id })
        assertEquals(1, api.calls.count { it == "trending" })
    }

    @Test
    fun savingAVideo_goesToTheSwipeFileOrTheIdeaBank() = runTest {
        val viewModel = open()
        val video = video("abc")

        viewModel.saveToSwipeFile(video)
        assertEquals("https://youtu.be/abc", swipe.items.value.single().url)
        assertEquals(ResearchNotice.SAVED_TO_SWIPE, viewModel.uiState.value.notice)

        viewModel.saveAsIdea(video)
        assertEquals("Title abc", ideas.items.value.single().title)
        assertEquals("https://youtu.be/abc", ideas.items.value.single().notes)
        assertEquals(ResearchNotice.SAVED_AS_IDEA, viewModel.uiState.value.notice)
    }
}
