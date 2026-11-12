package com.creatora.app.ui.research

import com.creatora.app.domain.account.CreatorProfile
import com.creatora.app.domain.ai.AiTasks
import com.creatora.app.domain.home.IdeaStatus
import com.creatora.app.domain.model.AiError
import com.creatora.app.domain.model.AiOutcome
import com.creatora.app.domain.repository.Competitor
import com.creatora.app.domain.repository.TrendingResult
import com.creatora.app.domain.research.IdeaActions
import com.creatora.app.domain.research.vid
import com.creatora.app.testutil.FakeCompetitorRepository
import com.creatora.app.testutil.FakeCreatorIdeaRepository
import com.creatora.app.testutil.FakeProjectRepository
import com.creatora.app.testutil.FakeResearchRepository
import com.creatora.app.testutil.FakeTrendingRepository
import com.creatora.app.testutil.HomeKit
import com.creatora.app.testutil.MainDispatcherRule
import com.creatora.app.testutil.okReply
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TrendsViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val kit = HomeKit()
    private val research = FakeResearchRepository()
    private val competitors = FakeCompetitorRepository()
    private val trending = FakeTrendingRepository()
    private val ideas = FakeCreatorIdeaRepository()
    private val projects = FakeProjectRepository()

    private fun open() = TrendsViewModel(kit.auth, competitors, research, trending, kit.users, kit.prefs, AiTasks(kit.ai), kit.cache, IdeaActions(ideas, projects, AiTasks(kit.ai)))

    // Four older videos at 1,000 views and one video from 3 days ago at 9,000: a recent outlier.
    private val rivalVideos = listOf(vid("a", 40, 1_000), vid("b", 50, 1_000), vid("c", 60, 1_000), vid("d", 70, 1_000), vid("hit", 3, 9_000, title = "Dal in 10 minutes"))

    private val three = "IDEA: One | SCORE: 70 | WHY: Trending.\nIDEA: Two | SCORE: 60 | WHY: Rival.\nIDEA: Three | SCORE: 50 | WHY: Both."

    @Test
    fun theCompetitorOutliersOfTheLastSevenDays_comeFromTheCacheOnly() = runTest {
        competitors.list.value = listOf(Competitor("UC9", "Rival"), Competitor("UC8", "Not cached"))
        research.peeked["UC9"] = rivalVideos

        val ui = open().ui.value

        assertEquals(true, ui.hasCompetitors)
        val outlier = ui.outliers!!.single()
        assertEquals("hit", outlier.video.id)
        assertEquals("Rival", outlier.competitor)
        assertEquals(9.0, outlier.score, 0.001)
        assertTrue(research.calls.isEmpty()) // nothing was fetched: no quota
    }

    @Test
    fun olderOutliers_andNoCompetitors_giveAnEmptyListNotAFakeOne() = runTest {
        competitors.list.value = listOf(Competitor("UC9", "Rival"))
        research.peeked["UC9"] = rivalVideos.dropLast(1) + vid("hit", 20, 9_000)

        assertTrue(open().ui.value.outliers!!.isEmpty()) // a hit 20 days ago is not "this week"

        competitors.list.value = emptyList()
        val none = open().ui.value
        assertEquals(false, none.hasCompetitors)
        assertTrue(none.outliers!!.isEmpty())
    }

    @Test
    fun uploadThisNext_isBuiltFromTrendingAndOutliers_andIsOneAiActionADay() = runTest {
        competitors.list.value = listOf(Competitor("UC9", "Rival"))
        research.peeked["UC9"] = rivalVideos
        kit.users.put(kit.users.docs.value.getValue("uid1").copy(creatorProfile = CreatorProfile("street food")))
        trending.result = TrendingResult.Ok(listOf(com.creatora.app.domain.account.VideoStats("t1", "Monsoon chaat", "2026-01-01T00:00:00Z", 900_000)), 1, fromCache = true)
        kit.ai.outcome = okReply(three)
        val vm = open()

        vm.suggest()

        val ready = vm.ui.value.next as NextState.Ready
        assertEquals(listOf("One", "Two", "Three"), ready.ideas.map { it.title })
        val request = kit.ai.requests.single()
        assertTrue("exactly 3" in request.system)
        val facts = request.messages.single().text
        assertTrue("Niche: street food" in facts && "- Monsoon chaat" in facts && "- Dal in 10 minutes (Rival)" in facts)

        val again = open()
        again.suggest()
        assertEquals(1, kit.ai.requests.size) // the same day's answer comes from the cache
        assertTrue(again.ui.value.next is NextState.Ready)
    }

    @Test
    fun withNothingToStandOn_noAiActionIsSpent() = runTest {
        val vm = open()

        vm.suggest()

        assertEquals(NextState.NoSources, vm.ui.value.next)
        assertTrue(kit.ai.requests.isEmpty())
    }

    @Test
    fun aFailedOrUnreadableAnswer_canBeRetried() = runTest {
        trending.result = TrendingResult.Ok(listOf(com.creatora.app.domain.account.VideoStats("t1", "Monsoon chaat", "2026-01-01T00:00:00Z", 900_000)), 1, fromCache = true)
        kit.ai.outcome = AiOutcome.Failed(listOf(AiError.LimitReached))
        val vm = open()

        vm.suggest()
        assertEquals(NextState.Failed(listOf(AiError.LimitReached)), vm.ui.value.next)

        kit.ai.outcome = okReply("I cannot say.")
        vm.suggest()
        assertEquals(NextState.Failed(emptyList()), vm.ui.value.next)

        kit.ai.outcome = okReply(three)
        vm.suggest()
        assertTrue(vm.ui.value.next is NextState.Ready) // the unreadable reply was not cached
        assertEquals(3, kit.ai.requests.size)
    }

    @Test
    fun suggestions_canBeSavedOrStarted() = runTest {
        trending.result = TrendingResult.Ok(listOf(com.creatora.app.domain.account.VideoStats("t1", "Monsoon chaat", "2026-01-01T00:00:00Z", 900_000)), 1, fromCache = true)
        kit.ai.outcome = okReply(three)
        val vm = open()
        vm.suggest()
        val ideasShown = (vm.ui.value.next as NextState.Ready).ideas

        vm.save(ideasShown[0])
        var started = 0L
        vm.create(ideasShown[1]) { started = it }

        val saved = ideas.ideas.value.single { it.title == "One" }
        assertEquals(IdeaStatus.SAVED, saved.status)
        assertEquals("trends", saved.source)
        assertEquals(70, saved.score)
        assertEquals(setOf("One"), vm.ui.value.saved)
        assertEquals(projects.items.value.single().id, started)
        assertEquals(IdeaStatus.PLANNED, ideas.ideas.value.single { it.title == "Two" }.status)
    }

    @Test
    fun theNextState_startsIdle_soOpeningTheTabSpendsNothing() = runTest {
        assertEquals(NextState.Idle, open().ui.value.next)
        assertTrue(kit.ai.requests.isEmpty())
    }
}
