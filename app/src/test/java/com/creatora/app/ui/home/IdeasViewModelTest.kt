package com.creatora.app.ui.home

import com.creatora.app.domain.account.AdapterError
import com.creatora.app.domain.account.AdapterOutcome
import com.creatora.app.domain.ai.AiTasks
import com.creatora.app.domain.home.IdeaStatus
import com.creatora.app.domain.model.AiError
import com.creatora.app.domain.model.AiOutcome
import com.creatora.app.domain.repository.BillingState
import com.creatora.app.testutil.FakeCreatorIdeaRepository
import com.creatora.app.testutil.FakeProjectRepository
import com.creatora.app.testutil.HomeKit
import com.creatora.app.testutil.MainDispatcherRule
import com.creatora.app.testutil.keepActive
import com.creatora.app.testutil.okReply
import com.creatora.app.testutil.sampleVideos
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class IdeasViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val kit = HomeKit()
    private val ideas = FakeCreatorIdeaRepository()
    private val projects = FakeProjectRepository()

    private fun lines(count: Int) = (1..count).joinToString("\n") { "IDEA: Idea number $it | SCORE: ${50 + it} | WHY: Your best video had 5000 views." }

    private fun TestScope.open(): IdeasViewModel {
        val vm = IdeasViewModel(kit.context, kit.loader, AiTasks(kit.ai), ideas, kit.cache, kit.auth, kit.plans, projects)
        keepActive(vm.state)
        return vm
    }

    private suspend fun ready() {
        kit.youtube.videos = AdapterOutcome.Ok(sampleVideos(8))
        kit.connect()
    }

    @Test
    fun theFreePlan_getsThreeIdeasADay_madeByOneAiAction() = runTest {
        ready()
        kit.ai.outcome = okReply(lines(5))

        val state = open().state.value as IdeasState.Ready

        assertEquals(3, state.limit)
        assertEquals(listOf("Idea number 1", "Idea number 2", "Idea number 3"), state.ideas.map { it.title })
        assertEquals(51, state.ideas.first().score)
        assertEquals(1, kit.ai.requests.size)
        assertTrue("exactly 3 ideas" in kit.ai.requests.single().system)
        assertTrue("Most viewed of the recent videos" in kit.ai.requests.single().messages.single().text)
    }

    @Test
    fun pro_getsTen() = runTest {
        ready()
        kit.billing.current.value = BillingState(available = true, hasPro = true)
        kit.ai.outcome = okReply(lines(12))

        val state = open().state.value as IdeasState.Ready

        assertEquals(10, state.limit)
        assertEquals(10, state.ideas.size)
    }

    @Test
    fun theBatchIsMadeOnce_aDay_evenAfterEveryIdeaWasDismissed() = runTest {
        ready()
        kit.ai.outcome = okReply(lines(3))
        val vm = open()
        (vm.state.value as IdeasState.Ready).ideas.forEach { vm.dismiss(it) }

        assertEquals(emptyList<Any>(), (vm.state.value as IdeasState.Ready).ideas)
        val reopened = open()

        assertEquals(emptyList<Any>(), (reopened.state.value as IdeasState.Ready).ideas)
        assertEquals(1, kit.ai.requests.size)
    }

    @Test
    fun save_dismiss_andCreate_changeTheStatus() = runTest {
        ready()
        kit.ai.outcome = okReply(lines(3))
        val vm = open()
        val (first, second, third) = (vm.state.value as IdeasState.Ready).ideas
        var projectId = 0L

        vm.save(first)
        vm.dismiss(second)
        vm.create(third) { projectId = it }

        val byTitle = ideas.ideas.value.associateBy { it.title }
        assertEquals(IdeaStatus.SAVED, byTitle.getValue("Idea number 1").status)
        assertEquals(IdeaStatus.DISMISSED, byTitle.getValue("Idea number 2").status)
        assertEquals(IdeaStatus.PLANNED, byTitle.getValue("Idea number 3").status)
        assertEquals(projectId, byTitle.getValue("Idea number 3").projectId)
        val project = projects.items.value.single()
        assertEquals("Idea number 3", project.title)
        assertTrue("Your best video had 5000 views." in project.brief)
        assertEquals(emptyList<Any>(), (vm.state.value as IdeasState.Ready).ideas) // none is waiting any more
    }

    @Test
    fun ideasAreKeptPerAccount() = runTest {
        ready()
        kit.ai.outcome = okReply(lines(3))
        open()
        kit.connect("UC2", "Two")
        kit.prefs.selectedKey.value = "YOUTUBE_UC2"

        val second = open().state.value as IdeasState.Ready

        assertEquals(3, second.ideas.size)
        assertEquals(2, kit.ai.requests.size)
        assertEquals(setOf("YOUTUBE_UC1", "YOUTUBE_UC2"), ideas.ideas.value.map { it.accountKey }.toSet())
    }

    @Test
    fun problemsAreShown_withNoIdeasMadeUp() = runTest {
        kit.youtube.videos = AdapterOutcome.Ok(sampleVideos(2))
        kit.connect()
        assertEquals(IdeasState.NotEnoughData, open().state.value)
        assertTrue(kit.ai.requests.isEmpty())

        kit.youtube.videos = AdapterOutcome.Ok(sampleVideos(8))
        kit.loader.overview(kit.accounts.byUser.value.getValue("uid1").first(), force = true)
        kit.ai.outcome = AiOutcome.Failed(listOf(AiError.LimitReached))
        assertEquals(IdeasState.AiFailed(listOf(AiError.LimitReached)), open().state.value)

        kit.ai.outcome = okReply("no idea lines here")
        assertEquals(IdeasState.AiFailed(emptyList()), open().state.value)
        assertTrue(ideas.ideas.value.isEmpty())
    }

    @Test
    fun noAccount_severalAccounts_andUnreadableChannel() = runTest {
        assertEquals(IdeasState.NoAccount, open().state.value)

        kit.youtube.videos = AdapterOutcome.Failed(AdapterError.Network)
        kit.connect()
        assertEquals(IdeasState.Unavailable(AdapterError.Network), open().state.value)

        kit.connect("UC2", "Two")
        kit.prefs.selectedKey.value = "all"
        assertEquals(IdeasState.PickOne, open().state.value)
    }
}
