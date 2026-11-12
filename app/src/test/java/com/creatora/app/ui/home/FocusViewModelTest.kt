package com.creatora.app.ui.home

import com.creatora.app.domain.account.AdapterError
import com.creatora.app.domain.account.AdapterOutcome
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.account.ConnectedAccount
import com.creatora.app.domain.ai.AiTasks
import com.creatora.app.domain.model.AiError
import com.creatora.app.domain.model.AiOutcome
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

class FocusViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val kit = HomeKit()
    private val projects = FakeProjectRepository()
    private val reply = "TIP: Your last five videos average 4,200 views, below the 5 before them. Post a short this week.\nSOURCE: Average views 4200 vs 5000"

    private fun TestScope.open(): FocusViewModel {
        val vm = FocusViewModel(kit.context, kit.loader, AiTasks(kit.ai), kit.cache, kit.auth, projects)
        keepActive(vm.state)
        return vm
    }

    @Test
    fun withoutAnAccount_orWithSeveral_thereIsNothingOrAPrompt() = runTest {
        assertEquals(FocusState.NoAccount, open().state.value)

        kit.connect("UC1", "One")
        kit.connect("UC2", "Two")
        kit.prefs.selectedKey.value = "all"
        assertEquals(FocusState.PickOne, open().state.value)
    }

    @Test
    fun theTip_isBuiltFromRealNumbers_andShownWithItsSource() = runTest {
        kit.youtube.videos = AdapterOutcome.Ok(sampleVideos(8))
        kit.ai.outcome = okReply(reply)
        kit.connect()

        val state = open().state.value as FocusState.Ready

        assertTrue("Post a short this week" in state.tip.tip)
        assertEquals("Average views 4200 vs 5000", state.tip.source)
        val prompt = kit.ai.requests.single().messages.single().text
        assertTrue("Average views of the last 5 videos" in prompt && "Days since the newest upload" in prompt)
        assertTrue("Never invent" in kit.ai.requests.single().system || "only the numbers given" in kit.ai.requests.single().system)
    }

    @Test
    fun theTipIsMadeOncePerDay_andReusedFromTheCache() = runTest {
        kit.youtube.videos = AdapterOutcome.Ok(sampleVideos(8))
        kit.ai.outcome = okReply(reply)
        kit.connect()
        open()
        val again = open()

        assertTrue(again.state.value is FocusState.Ready)
        assertEquals(1, kit.ai.requests.size)
    }

    @Test
    fun tooFewVideos_meansNoTipAndNoAiAction() = runTest {
        kit.youtube.videos = AdapterOutcome.Ok(sampleVideos(3))
        kit.connect()

        assertEquals(FocusState.NotEnoughData, open().state.value)
        assertTrue(kit.ai.requests.isEmpty())
    }

    @Test
    fun later_hidesTheTipUntilTomorrow() = runTest {
        kit.youtube.videos = AdapterOutcome.Ok(sampleVideos(8))
        kit.ai.outcome = okReply(reply)
        kit.connect()
        val vm = open()

        vm.later()

        assertEquals(FocusState.Later, vm.state.value)
        assertEquals(FocusState.Later, open().state.value) // a new screen still hides it today
    }

    @Test
    fun fixIt_startsAProjectFromTheTip() = runTest {
        kit.youtube.videos = AdapterOutcome.Ok(sampleVideos(8))
        kit.ai.outcome = okReply(reply)
        kit.connect()
        val vm = open()
        var created = 0L

        vm.fixIt { created = it }

        val project = projects.items.value.single()
        assertEquals(created, project.id)
        assertTrue(project.brief.startsWith("Your last five videos"))
        assertTrue(project.title.length <= 60)
    }

    @Test
    fun problems_areShownHonestly() = runTest {
        kit.youtube.videos = AdapterOutcome.Ok(sampleVideos(8))
        kit.ai.outcome = AiOutcome.Failed(listOf(AiError.LimitReached))
        kit.connect()
        assertEquals(FocusState.AiFailed(listOf(AiError.LimitReached)), open().state.value)

        kit.accounts.byUser.value = emptyMap()
        kit.accounts.save("uid1", ConnectedAccount(ConnectPlatform.INSTAGRAM, "ig", "Insta"))
        assertEquals(FocusState.Unavailable(AdapterError.ComingSoon), open().state.value)
    }

    @Test
    fun aChannelThatCannotBeRead_isUnavailable() = runTest {
        kit.youtube.videos = AdapterOutcome.Failed(AdapterError.Network)
        kit.connect()
        assertEquals(FocusState.Unavailable(AdapterError.Network), open().state.value)
    }
}
