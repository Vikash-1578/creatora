package com.creatora.app.ui.research

import androidx.lifecycle.SavedStateHandle
import com.creatora.app.domain.account.AdapterError
import com.creatora.app.domain.account.ChannelPreview
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.ai.AiTasks
import com.creatora.app.domain.home.IdeaStatus
import com.creatora.app.domain.home.dayKey
import com.creatora.app.domain.model.AiError
import com.creatora.app.domain.model.AiOutcome
import com.creatora.app.domain.research.CompetitorSnapshot
import com.creatora.app.domain.research.Fetched
import com.creatora.app.domain.research.Growth
import com.creatora.app.domain.research.IdeaActions
import com.creatora.app.domain.research.ViralConcept
import com.creatora.app.domain.research.vid
import com.creatora.app.testutil.FakeCompetitorRepository
import com.creatora.app.testutil.FakeCreatorIdeaRepository
import com.creatora.app.testutil.FakeProjectRepository
import com.creatora.app.testutil.FakeResearchRepository
import com.creatora.app.testutil.HomeKit
import com.creatora.app.testutil.MainDispatcherRule
import com.creatora.app.testutil.keepActive
import com.creatora.app.testutil.okReply
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CompetitorDetailViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val kit = HomeKit()
    private val research = FakeResearchRepository()
    private val competitors = FakeCompetitorRepository()
    private val ideas = FakeCreatorIdeaRepository()
    private val projects = FakeProjectRepository()
    private val info = ChannelPreview(ConnectPlatform.YOUTUBE, "UC9", "Rival", subscribers = 50_000, videoCount = 80, viewCount = 2_000_000)

    private val videos = listOf(
        vid("a", 40, 1_000), vid("b", 50, 1_000), vid("c", 60, 1_000), vid("d", 70, 1_000),
        vid("hit", 3, 9_000, title = "Dal in 10 minutes"),
    )

    private fun TestScope.open(): CompetitorDetailViewModel {
        research.info = Fetched.Ok(info, cachedAt = 11, fromCache = false)
        research.videos = Fetched.Ok(videos, cachedAt = 12, fromCache = true)
        val vm = CompetitorDetailViewModel(SavedStateHandle(mapOf("channelId" to "UC9")), kit.auth, competitors, research, AiTasks(kit.ai), kit.cache, IdeaActions(ideas, projects, AiTasks(kit.ai)))
        keepActive(vm.state)
        return vm
    }

    private fun ready(vm: CompetitorDetailViewModel) = (vm.state.value as DetailState.Ready).detail

    @Test
    fun theDetail_isBuiltFromRealNumbers() = runTest {
        val detail = ready(open())

        assertEquals(info, detail.info)
        assertNotNull(detail.uploadsPerWeek)
        assertEquals(2_600L, detail.averageViews) // (1000 x 4 + 9000) / 5
        assertEquals("hit", detail.topVideos.first().id)
        assertEquals(listOf("hit"), detail.viral.map { it.video.id })
        assertNull(detail.videosError)
        assertEquals(11L, detail.updatedAt)
        assertEquals(listOf("info:UC9:false", "videos:UC9:false"), research.calls)
    }

    @Test
    fun growth_saysTrackingSince_untilASecondDayExists() = runTest {
        val today = dayKey(System.currentTimeMillis())
        val first = ready(open())

        assertEquals(Growth.Tracking(today), first.growth)
        assertEquals(today, competitors.snapshotsByChannel["UC9"]!!.single().day) // today's snapshot was saved
        assertEquals(50_000L, competitors.snapshotsByChannel["UC9"]!!.single().subscribers)

        competitors.snapshotsByChannel["UC9"] = listOf(CompetitorSnapshot("20200101", 40_000, 1_500_000, 70), CompetitorSnapshot(today, 50_000, 2_000_000, 80))
        val later = ready(open())
        assertTrue(later.growth is Growth.Known)
        assertEquals(10_000L, (later.growth as Growth.Known).subscribers)
        assertEquals(500_000L, (later.growth as Growth.Known).views)
        assertEquals(1, competitors.snapshotsByChannel["UC9"]!!.count { it.day == today }) // at most one snapshot a day
    }

    @Test
    fun aChannelThatCannotBeLoaded_isAnError_andMissingVideosKeepTheNumbers() = runTest {
        val failing = open()
        research.info = Fetched.Failed(AdapterError.NotFound)
        failing.refresh()
        assertEquals(DetailState.Failed(AdapterError.NotFound), failing.state.value)

        research.info = Fetched.Ok(info, 11, false)
        research.videos = Fetched.Failed(AdapterError.QuotaExceeded)
        failing.refresh()
        val detail = ready(failing)
        assertEquals(AdapterError.QuotaExceeded, detail.videosError)
        assertTrue(detail.topVideos.isEmpty() && detail.viral.isEmpty())
        assertNull(detail.uploadsPerWeek)
        assertEquals(50_000L, detail.info.subscribers)
    }

    @Test
    fun refresh_asksForFreshData() = runTest {
        val vm = open()
        vm.refresh()
        assertTrue("info:UC9:true" in research.calls && "videos:UC9:true" in research.calls)
    }

    @Test
    fun whyAVideoWentViral_usesTheTopComments_andIsCached() = runTest {
        research.commentsResult = Fetched.Ok(listOf("Loved the time limit", "More please"), 1, false)
        kit.ai.outcome = okReply("CONCEPT: A quick dish with a clock\nWHY: The title promises 10 minutes. Comments praise the time limit.")
        val vm = open()
        val item = ready(vm).viral.single()

        vm.explain(item)

        val state = vm.viralNotes.value["hit"] as ViralState.Ready
        assertEquals("A quick dish with a clock", state.concept.concept)
        val facts = kit.ai.requests.single().messages.single().text
        assertTrue("Top comments (only these 2)" in facts && "- Loved the time limit" in facts)
        assertTrue("Retention and thumbnail are not available" in facts)

        val again = open()
        again.explain(ready(again).viral.single())
        assertEquals(1, kit.ai.requests.size) // cached for the day
        assertTrue(again.viralNotes.value["hit"] is ViralState.Ready)
    }

    @Test
    fun withoutComments_theAiIsToldSo_andFailuresCanBeRetried() = runTest {
        research.commentsResult = Fetched.Failed(AdapterError.NotAvailable("comments are off"))
        kit.ai.outcome = AiOutcome.Failed(listOf(AiError.LimitReached))
        val vm = open()
        val item = ready(vm).viral.single()

        vm.explain(item)
        assertEquals(ViralState.Failed(listOf(AiError.LimitReached)), vm.viralNotes.value["hit"])
        assertTrue("Top comments: not available" in kit.ai.requests.single().messages.single().text)

        kit.ai.outcome = okReply("no markers")
        vm.explain(item)
        assertEquals(ViralState.Failed(emptyList()), vm.viralNotes.value["hit"]) // unreadable reply
    }

    @Test
    fun useThisConcept_startsAProjectWithTheConceptNotTheOriginalTitle() = runTest {
        val vm = open()
        var opened: Pair<Long, Boolean>? = null

        vm.use(ViralConcept("A quick dish with a clock", "It promises a time limit."), letAi = false) { id, failed -> opened = id to failed }

        val project = projects.items.value.single()
        assertEquals("A quick dish with a clock", project.title)
        assertEquals("A quick dish with a clock\nIt promises a time limit.", project.brief)
        assertEquals(project.id to false, opened)
        assertTrue(kit.ai.requests.isEmpty())
        val idea = ideas.ideas.value.single()
        assertEquals("competitor", idea.source)
        assertEquals(IdeaStatus.PLANNED, idea.status)
        assertEquals(project.id, idea.projectId)
    }

    @Test
    fun letAiMakeIt_alsoDraftsTheScript() = runTest {
        kit.ai.outcome = okReply("HOOK: Ten minutes on the clock\nSTRUCTURE: Boil, temper, serve\nCTA: Try it tonight")
        val vm = open()
        var failed: Boolean? = null

        vm.use(ViralConcept("A quick dish with a clock", "Time limit."), letAi = true) { _, draftFailed -> failed = draftFailed }

        assertEquals("Ten minutes on the clock", projects.items.value.single().hook)
        assertEquals(false, failed)
    }
}
