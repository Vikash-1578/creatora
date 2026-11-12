package com.creatora.app.ui.research

import com.creatora.app.domain.account.AdapterError
import com.creatora.app.domain.ai.AiTasks
import com.creatora.app.domain.model.AiError
import com.creatora.app.domain.model.AiOutcome
import com.creatora.app.domain.research.Fetched
import com.creatora.app.domain.research.KeywordSearch
import com.creatora.app.domain.research.Level
import com.creatora.app.domain.research.vid
import com.creatora.app.testutil.FakeResearchRepository
import com.creatora.app.testutil.FakeSearchLog
import com.creatora.app.testutil.HomeKit
import com.creatora.app.testutil.MainDispatcherRule
import com.creatora.app.testutil.okReply
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class KeywordsViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val kit = HomeKit()
    private val research = FakeResearchRepository()
    private val log = FakeSearchLog()

    private fun open() = KeywordsViewModel(kit.auth, kit.context, research, log, AiTasks(kit.ai), kit.cache)

    private fun found(count: Int = 10) = KeywordSearch(
        videos = (0 until count).map { vid("k$it", 30, 3_000, channelId = "c$it", title = "best dal recipe", tags = listOf("dal", "food")) },
        channelSubscribers = (0 until count).associate { "c$it" to (if (it < 5) 5_000L else 500L) },
    )

    @Test
    fun aSearch_givesAnEstimateFromTheResults_andIsLoggedOnce() = runTest {
        kit.connect("UC1", subs = 1_000)
        research.keywordResult = Fetched.Ok(found(), cachedAt = 3, fromCache = true)
        val vm = open()
        assertEquals(KeywordState.Idle, vm.state.value) // opening the tab searches nothing

        vm.search("  dal recipe ")

        val ready = vm.state.value as KeywordState.Ready
        assertEquals("dal recipe", ready.query)
        assertEquals(Level.MEDIUM, ready.estimate.demand)
        assertEquals(50, ready.estimate.biggerChannelsPercent) // compared with the account's 1,000 subscribers
        assertEquals(listOf("dal", "food"), ready.estimate.related)
        assertEquals(3L, ready.cachedAt)
        assertTrue(ready.fromCache && !ready.stale)
        assertEquals(listOf("keyword:dal recipe:false"), research.calls)
        assertEquals(listOf("dal recipe"), log.queries)
    }

    @Test
    fun refresh_skipsTheCache_andIsNotLoggedAsANewSearch() = runTest {
        research.keywordResult = Fetched.Ok(found(), 3, false)
        val vm = open()
        vm.search("dal recipe")

        vm.search("dal recipe", force = true)

        assertEquals("keyword:dal recipe:true", research.calls.last())
        assertEquals(listOf("dal recipe"), log.queries)
    }

    @Test
    fun aBlankSearch_doesNothing() = runTest {
        val vm = open()

        vm.search("   ")

        assertEquals(KeywordState.Idle, vm.state.value)
        assertTrue(research.calls.isEmpty() && log.queries.isEmpty())
    }

    @Test
    fun failuresAndThinResults_areSaidPlainly() = runTest {
        val vm = open()
        research.keywordResult = Fetched.Failed(AdapterError.QuotaExceeded)
        vm.search("dal")
        assertEquals(KeywordState.Failed(AdapterError.QuotaExceeded), vm.state.value)

        research.keywordResult = Fetched.Ok(found(4), 1, false)
        vm.search("dal")
        assertEquals(KeywordState.NotEnough(4), vm.state.value)
    }

    @Test
    fun withoutASingleAccount_channelSizesAreNotCompared() = runTest {
        research.keywordResult = Fetched.Ok(found(), 3, false)
        val vm = open()

        vm.search("dal recipe")

        assertEquals(null, (vm.state.value as KeywordState.Ready).estimate.biggerChannelsPercent)
    }

    @Test
    fun questions_areAiSuggestions_askedOnTap_andCachedForTheKeyword() = runTest {
        research.keywordResult = Fetched.Ok(found(), 3, false)
        kit.ai.outcome = okReply("- How long does dal take?\n- Which dal is best?")
        val vm = open()
        vm.suggestQuestions() // no search yet: nothing happens
        assertEquals(QuestionsState.Idle, vm.questions.value)
        vm.search("dal recipe")

        vm.suggestQuestions()

        assertEquals(QuestionsState.Ready(listOf("How long does dal take?", "Which dal is best?")), vm.questions.value)
        assertTrue("Topic: dal recipe" in kit.ai.requests.single().messages.single().text)

        vm.suggestQuestions()
        assertEquals(1, kit.ai.requests.size)
    }

    @Test
    fun aQuestionsFailure_isShown() = runTest {
        research.keywordResult = Fetched.Ok(found(), 3, false)
        kit.ai.outcome = AiOutcome.Failed(listOf(AiError.LimitReached))
        val vm = open()
        vm.search("dal recipe")

        vm.suggestQuestions()

        assertEquals(QuestionsState.Failed(listOf(AiError.LimitReached)), vm.questions.value)
    }

    @Test
    fun aNewSearch_clearsTheOldQuestions() = runTest {
        research.keywordResult = Fetched.Ok(found(), 3, false)
        kit.ai.outcome = okReply("- Q1")
        val vm = open()
        vm.search("dal")
        vm.suggestQuestions()

        vm.search("rice")

        assertEquals(QuestionsState.Idle, vm.questions.value)
    }
}
