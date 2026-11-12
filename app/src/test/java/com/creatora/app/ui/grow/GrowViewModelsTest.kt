package com.creatora.app.ui.grow

import com.creatora.app.domain.account.AdapterError
import com.creatora.app.domain.account.AdapterOutcome
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.account.ConnectedAccount
import com.creatora.app.domain.ai.AiTasks
import com.creatora.app.domain.grow.GrowLoader
import com.creatora.app.domain.grow.Period
import com.creatora.app.domain.grow.Reports
import com.creatora.app.domain.home.AccountSelection
import com.creatora.app.domain.model.AiError
import com.creatora.app.domain.model.AiOutcome
import com.creatora.app.testutil.HomeKit
import com.creatora.app.testutil.MainDispatcherRule
import com.creatora.app.testutil.okReply
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GrowViewModelsTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val kit = HomeKit()
    private fun loader() = GrowLoader(kit.registry, kit.loader)
    private fun analytics() = AnalyticsViewModel(kit.context, loader(), AiTasks(kit.ai))
    private fun earn() = EarnViewModel(kit.context, loader(), AiTasks(kit.ai))

    private fun dailyRows(views: Long) = AdapterOutcome.Ok((0 until 196).map { i ->
        mapOf<String, Any?>("day" to java.time.LocalDate.now().minusDays(2).minusDays(195L - i).toString(), "views" to views.toDouble(), "estimatedMinutesWatched" to 30.0, "subscribersGained" to 1.0, "subscribersLost" to 0.0)
    })

    @Test
    fun withoutAnAccount_orWithAllAccounts_nothingIsFetched() = runTest {
        val none = analytics()
        advanceUntilIdle()
        assertEquals(Load.NoAccount, none.state.value.overview)

        kit.connect("UC1")
        kit.connect("UC2")
        kit.context.select(AccountSelection.All)
        val all = analytics()
        advanceUntilIdle()
        assertEquals(Load.PickOne, all.state.value.overview)
        assertTrue(kit.youtube.reportCalls.isEmpty())
    }

    @Test
    fun otherPlatforms_sayThatReportsAreYouTubeOnly() = runTest {
        kit.accounts.save("uid1", ConnectedAccount(ConnectPlatform.INSTAGRAM, "ig1", "Maya"))
        val vm = analytics()
        advanceUntilIdle()
        assertTrue((vm.state.value.overview as Load.Failed).error is AdapterError.NotAvailable)
    }

    @Test
    fun onlyTheOpenTabIsLoaded_andThePeriodChangesTheReport() = runTest {
        kit.connect()
        kit.youtube.reports[Reports.daily] = dailyRows(10)
        val vm = analytics()
        advanceUntilIdle()

        val overview = (vm.state.value.overview as Load.Ready).value
        assertEquals(280L, overview.views.total) // 28 days by default
        assertEquals(setOf(Reports.daily), kit.youtube.reportCalls.map { it.first }.toSet()) // no content or audience reports yet

        vm.setPeriod(Period.WEEK)
        advanceUntilIdle()
        assertEquals(70L, (vm.state.value.overview as Load.Ready).value.views.total)

        vm.setTab(AnalyticsTab.CONTENT)
        advanceUntilIdle()
        assertTrue(vm.state.value.content is Load.Failed) // the traffic report is refused in this test, and the screen says so
        assertTrue(kit.youtube.reportCalls.any { it.first == Reports.traffic })
    }

    @Test
    fun explain_isOneAiAction_fromTheNumbersOnScreen() = runTest {
        kit.connect()
        kit.youtube.reports[Reports.daily] = dailyRows(10)
        kit.ai.outcome = okReply("Views are steady at 280.")
        val vm = analytics()
        advanceUntilIdle()

        vm.explain()
        advanceUntilIdle()

        assertEquals("Views are steady at 280.", vm.state.value.explain.text)
        assertTrue(kit.ai.requests.last().messages.last().text.contains("Views in the last 28 days: 280"))

        kit.ai.outcome = AiOutcome.Failed(listOf(AiError.LimitReached))
        vm.explain()
        advanceUntilIdle()
        assertEquals(listOf(AiError.LimitReached), vm.state.value.explain.errors)
    }

    @Test
    fun nothingToExplain_beforeTheNumbersAreThere() {
        assertNull(factsOf(AnalyticsUiState()))
    }

    @Test
    fun earn_showsTheJourneyEvenWhenRevenueIsRefused() = runTest {
        kit.connect(subs = 640)
        val vm = earn()
        advanceUntilIdle()

        assertTrue(vm.state.value.earnings is Load.Failed) // not monetized: never a zero
        val tiers = (vm.state.value.journey as Load.Ready).value
        assertEquals(listOf(1, 2), tiers.map { it.tier })
        assertEquals(640.0, tiers.first().paths.first().items.first().have!!, 0.0)
    }

    @Test
    fun thePlan_isOneAiAction_fromTheRealGaps() = runTest {
        kit.connect(subs = 640)
        kit.ai.outcome = okReply("Publish two Shorts this week.")
        val vm = earn()
        advanceUntilIdle()

        vm.makePlan()
        advanceUntilIdle()

        assertEquals("Publish two Shorts this week.", vm.state.value.plan.text)
        val sent = kit.ai.requests.last().messages.last().text
        assertTrue(sent.contains("subscribers 640 of 500"))
        assertTrue(sent.contains("not available")) // unknown numbers are passed as unknown
    }
}
