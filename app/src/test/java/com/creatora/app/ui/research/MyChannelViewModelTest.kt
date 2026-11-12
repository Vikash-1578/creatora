package com.creatora.app.ui.research

import com.creatora.app.domain.account.AdapterError
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.account.ConnectedAccount
import com.creatora.app.domain.account.VideoAnalytics
import com.creatora.app.domain.ai.AiTasks
import com.creatora.app.domain.home.IdeaStatus
import com.creatora.app.domain.model.AiError
import com.creatora.app.domain.model.AiOutcome
import com.creatora.app.domain.research.Fetched
import com.creatora.app.domain.research.IdeaActions
import com.creatora.app.domain.research.vid
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MyChannelViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val kit = HomeKit()
    private val research = FakeResearchRepository()
    private val ideas = FakeCreatorIdeaRepository()
    private val projects = FakeProjectRepository()

    private val channel = listOf(
        vid("v1", 10, 1_000), vid("v2", 15, 1_000), vid("v3", 20, 1_000), vid("v4", 25, 1_000),
        vid("win", 12, 5_000, title = "Dal in 10 minutes"), vid("old", 200, 100, title = "Kitchen tour"),
    )

    private fun TestScope.open(): MyChannelViewModel {
        val vm = MyChannelViewModel(kit.context, research, AiTasks(kit.ai), kit.cache, kit.auth, IdeaActions(ideas, projects, AiTasks(kit.ai)))
        keepActive(vm.state)
        return vm
    }

    private suspend fun ready(videos: List<com.creatora.app.domain.account.VideoStats> = channel) {
        research.videos = Fetched.Ok(videos, cachedAt = 5, fromCache = true)
        kit.connect()
    }

    @Test
    fun theTabSaysWhyThereIsNothingToShow() = runTest {
        assertEquals(MyChannelState.NoAccount, open().state.value)
    }

    @Test
    fun withSeveralAccountsSelected_oneMustBePicked_andOtherPlatformsAreNotAvailable() = runTest {
        kit.connect("UC1")
        kit.connect("UC2", "Second")
        kit.prefs.selectedKey.value = "all" // by default the first account is selected; "All accounts" has to be chosen
        assertEquals(MyChannelState.PickOne, open().state.value)

        val other = HomeKit()
        other.accounts.save("uid1", ConnectedAccount(ConnectPlatform.INSTAGRAM, "ig", "Insta"))
        val vm = MyChannelViewModel(other.context, research, AiTasks(other.ai), other.cache, other.auth, IdeaActions(ideas, projects, AiTasks(other.ai)))
        keepActive(vm.state)
        assertEquals(MyChannelState.Unsupported, vm.state.value)
    }

    @Test
    fun ready_listsTheWinnersAndTheWeakest_andSaysWhenItWasUpdated() = runTest {
        ready()

        val state = open().state.value as MyChannelState.Ready

        assertEquals(listOf("win"), state.winners.map { it.video.id })
        assertEquals(listOf("old"), state.weakest.map { it.video.id })
        assertEquals(5L, state.cachedAt)
        assertEquals(listOf("videos:UC1:false"), research.calls)
    }

    @Test
    fun tooFewVideos_andErrors_areShownAsTheyAre() = runTest {
        ready(channel.take(3))
        assertEquals(MyChannelState.NotEnough(3), open().state.value)

        research.videos = Fetched.Failed(AdapterError.Network)
        val vm = open()
        assertEquals(MyChannelState.Failed(AdapterError.Network), vm.state.value)
    }

    @Test
    fun refresh_asksForFreshData() = runTest {
        ready()
        val vm = open()

        vm.refresh()

        assertEquals(listOf("videos:UC1:false", "videos:UC1:true"), research.calls)
    }

    @Test
    fun whyAWinnerDidWell_usesItsRealNumbers_andIsCached() = runTest {
        ready()
        research.analytics = VideoAnalytics("win", ctrPercent = 4.2, averageViewPercent = 51.0)
        kit.ai.outcome = okReply("It got 5 times your usual. The click rate was 4.2%.")
        val vm = open()
        val winner = (vm.state.value as MyChannelState.Ready).winners.single()

        vm.explain(winner, winner = true)

        assertEquals(NoteState.Text("It got 5 times your usual. The click rate was 4.2%."), vm.notes.value["win"])
        val request = kit.ai.requests.single()
        assertTrue("Click-through rate: 4.2%" in request.messages.single().text)
        assertTrue("Thumbnail: not analysed" in request.messages.single().text)

        vm.explain(winner, winner = true) // nothing new is asked
        val reopened = open()
        reopened.explain((reopened.state.value as MyChannelState.Ready).winners.single(), winner = true)
        assertEquals(1, kit.ai.requests.size) // the day's cache answered the second open
        assertTrue(reopened.notes.value["win"] is NoteState.Text)
    }

    @Test
    fun whatIsNotWorking_asksForFixes_andAFailureCanBeRetried() = runTest {
        ready()
        kit.ai.outcome = AiOutcome.Failed(listOf(AiError.LimitReached))
        val vm = open()
        val weak = (vm.state.value as MyChannelState.Ready).weakest.single()

        vm.explain(weak, winner = false)
        assertEquals(NoteState.Failed(listOf(AiError.LimitReached)), vm.notes.value["old"])

        kit.ai.outcome = okReply("Fewer people clicked. Try a clearer title.")
        vm.explain(weak, winner = false)

        assertEquals(NoteState.Text("Fewer people clicked. Try a clearer title."), vm.notes.value["old"])
        assertTrue("fewer views" in kit.ai.requests.last().system)
    }

    @Test
    fun makeMoreLikeThis_givesIdeasThatCanBeSavedOrStarted() = runTest {
        ready()
        kit.ai.outcome = okReply("IDEA: Dal in 10 minutes, part 2 | SCORE: 70 | WHY: Your winner had 5000 views.\nIDEA: Rice in 10 minutes | SCORE: 55 | WHY: Same pattern.")
        val vm = open()
        val winner = (vm.state.value as MyChannelState.Ready).winners.single()

        vm.makeMore(winner)

        val more = vm.more.value["win"] as MoreState.Ready
        assertEquals(listOf("Dal in 10 minutes, part 2", "Rice in 10 minutes"), more.ideas.map { it.title })
        assertTrue("exactly 4" in kit.ai.requests.single().system)

        vm.save(more.ideas[0])
        val saved = ideas.ideas.value.single()
        assertEquals(IdeaStatus.SAVED, saved.status)
        assertEquals("research", saved.source)
        assertEquals("YOUTUBE_UC1", saved.accountKey)
        assertEquals(70, saved.score)
        assertEquals(setOf("Dal in 10 minutes, part 2"), vm.saved.value)

        var opened = 0L
        vm.create(more.ideas[1]) { opened = it }
        assertEquals(projects.items.value.single().id, opened)
        assertEquals(IdeaStatus.PLANNED, ideas.ideas.value.single { it.title == "Rice in 10 minutes" }.status)
    }

    @Test
    fun makeMore_saysWhenTheReplyCouldNotBeRead() = runTest {
        ready()
        kit.ai.outcome = okReply("Sorry, I cannot do that.")
        val vm = open()

        vm.makeMore((vm.state.value as MyChannelState.Ready).winners.single())

        assertEquals(MoreState.Failed(emptyList()), vm.more.value["win"])
    }
}
