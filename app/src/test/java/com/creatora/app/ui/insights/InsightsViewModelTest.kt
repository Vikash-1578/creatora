package com.creatora.app.ui.insights

import com.creatora.app.domain.account.AdapterError
import com.creatora.app.domain.account.AdapterOutcome
import com.creatora.app.domain.account.PlatformRegistry
import com.creatora.app.domain.account.VideoAnalytics
import com.creatora.app.domain.account.VideoStats
import com.creatora.app.domain.account.toAccount
import com.creatora.app.domain.ai.AiTasks
import com.creatora.app.domain.model.AiError
import com.creatora.app.domain.model.AiOutcome
import com.creatora.app.testutil.FakeAiService
import com.creatora.app.testutil.FakeAuthService
import com.creatora.app.testutil.FakeConnectedAccountRepository
import com.creatora.app.testutil.FakePlatformAdapter
import com.creatora.app.testutil.MainDispatcherRule
import com.creatora.app.testutil.TestUser
import com.creatora.app.testutil.okReply
import com.creatora.app.testutil.preview
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class InsightsViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val auth = FakeAuthService(TestUser)
    private val accounts = FakeConnectedAccountRepository()
    private val youtube = FakePlatformAdapter()
    private val ai = FakeAiService(okReply("VIDEO: low\nWHY: Only 10% of the usual views.\nFIX: Change the title."))

    private fun viewModel() = InsightsViewModel(auth, accounts, PlatformRegistry(listOf(youtube)), AiTasks(ai))

    private fun video(id: String, views: Long) = VideoStats(id, "Title $id", "2026-01-01", views, likes = 3, comments = 1)

    private val videos = listOf(video("low", 100), video("a", 1_000), video("b", 1_100), video("c", 900), video("d", 1_000))

    @Test
    fun withoutAnAccount_thereIsNothingToAnalyse() = runTest {
        val vm = viewModel()
        vm.start()
        assertEquals(InsightsUiState.NoAccount, vm.state.value)
        assertTrue(ai.requests.isEmpty())
    }

    @Test
    fun underperformers_getAnExplanationBuiltOnlyFromRealNumbers() = runTest {
        accounts.save("uid1", preview().toAccount())
        youtube.videos = AdapterOutcome.Ok(videos)
        youtube.analyticsResult = AdapterOutcome.Ok(listOf(VideoAnalytics("low", ctrPercent = 1.5)))
        val vm = viewModel()

        vm.start()

        val ready = vm.state.value as InsightsUiState.Ready
        val card = ready.cards.single()
        assertEquals("low", card.item.video.id)
        assertEquals(10, card.item.percentOfMedian)
        assertEquals(1.5, card.analytics?.ctrPercent!!, 0.001)
        assertEquals("Change the title.", card.insight?.fix)
        val prompt = ai.requests.single().messages.single().text
        assertTrue("Views: 100" in prompt && "Click-through rate: 1.5%" in prompt)
        assertTrue("Average percentage viewed: not available" in prompt)
        assertNull(ready.aiErrors)
    }

    @Test
    fun whenTheAiFails_theNumbersStillShow() = runTest {
        accounts.save("uid1", preview().toAccount())
        youtube.videos = AdapterOutcome.Ok(videos)
        ai.outcome = AiOutcome.Failed(listOf(AiError.LimitReached))
        val vm = viewModel()

        vm.start()

        val ready = vm.state.value as InsightsUiState.Ready
        assertNull(ready.cards.single().insight)
        assertEquals(listOf(AiError.LimitReached), ready.aiErrors)
    }

    @Test
    fun missingAnalytics_isSaidSo() = runTest {
        accounts.save("uid1", preview().toAccount())
        youtube.videos = AdapterOutcome.Ok(videos)
        youtube.analyticsResult = AdapterOutcome.Failed(AdapterError.NotAuthorized)
        val vm = viewModel()

        vm.start()

        assertTrue((vm.state.value as InsightsUiState.Ready).analyticsMissing)
    }

    @Test
    fun healthyChannels_notEnoughVideos_andFetchFailures() = runTest {
        accounts.save("uid1", preview().toAccount())
        youtube.videos = AdapterOutcome.Ok((1..5).map { video("v$it", 1_000) })
        val vm = viewModel()
        vm.start()
        assertEquals(InsightsUiState.AllHealthy, vm.state.value)
        assertTrue(ai.requests.isEmpty()) // nothing to explain, so no AI action is spent

        youtube.videos = AdapterOutcome.Ok(listOf(video("a", 1), video("b", 2)))
        vm.refresh()
        assertEquals(InsightsUiState.NotEnoughVideos(2), vm.state.value)

        youtube.videos = AdapterOutcome.Failed(AdapterError.Network)
        vm.refresh()
        assertEquals(InsightsUiState.FetchFailed, vm.state.value)
    }

    @Test
    fun start_runsOnlyOnce() = runTest {
        accounts.save("uid1", preview().toAccount())
        youtube.videos = AdapterOutcome.Ok(videos)
        val vm = viewModel()

        vm.start()
        vm.start()

        assertEquals(1, ai.requests.size)
    }
}
