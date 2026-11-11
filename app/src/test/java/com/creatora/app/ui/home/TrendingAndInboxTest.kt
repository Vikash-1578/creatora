package com.creatora.app.ui.home

import com.creatora.app.domain.account.AdapterError
import com.creatora.app.domain.account.CreatorProfile
import com.creatora.app.domain.account.NotificationCategory
import com.creatora.app.domain.account.Plan
import com.creatora.app.domain.account.VideoStats
import com.creatora.app.domain.home.IdeaStatus
import com.creatora.app.domain.repository.BillingState
import com.creatora.app.domain.repository.TrendingResult
import com.creatora.app.testutil.FakeCreatorIdeaRepository
import com.creatora.app.testutil.FakeNotificationInbox
import com.creatora.app.testutil.FakeTrendingRepository
import com.creatora.app.testutil.HomeKit
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

class TrendingAndInboxTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val kit = HomeKit()
    private val trending = FakeTrendingRepository()
    private val ideas = FakeCreatorIdeaRepository()
    private val video = VideoStats("t1", "A hot video", "2026-01-01T00:00:00Z", 9_000, channelTitle = "Big channel")

    private fun open(): TrendingViewModel = TrendingViewModel(trending, kit.auth, kit.users, kit.prefs, ideas)

    @Test
    fun trending_usesTheNicheFromTheCreatorProfile_andTheChosenRegion() = runTest {
        kit.users.put(kit.users.docs.value.getValue("uid1").copy(creatorProfile = CreatorProfile("street food")))
        kit.prefs.region.value = "IN"
        trending.result = TrendingResult.Ok(listOf(video), cachedAt = 5, fromCache = true)

        val state = open().state.value as TrendingState.Ready

        assertEquals(listOf(Triple("IN", "street food", false)), trending.calls)
        assertEquals("street food", state.niche)
        assertEquals("IN", state.region)
        assertTrue(state.fromCache && !state.stale)
        assertEquals("A hot video", state.items.single().title)
    }

    @Test
    fun withoutAProfile_theListIsGeneralForTheRegion() = runTest {
        kit.prefs.region.value = "US"
        trending.result = TrendingResult.Ok(listOf(video), 0, fromCache = false)

        val state = open().state.value as TrendingState.Ready

        assertNull(state.niche)
        assertNull(trending.calls.single().second)
    }

    @Test
    fun refresh_asksForFreshData_andAFailureIsShown() = runTest {
        kit.prefs.region.value = "IN"
        val vm = open()

        trending.result = TrendingResult.Failed(AdapterError.QuotaExceeded)
        vm.refresh()

        assertTrue(trending.calls.last().third) // force
        assertEquals(TrendingState.Failed(AdapterError.QuotaExceeded), vm.state.value)
    }

    @Test
    fun useAsIdea_addsASavedIdeaToTheBank() = runTest {
        kit.prefs.region.value = "IN"
        open().useAsIdea(video)

        val idea = ideas.ideas.value.single()
        assertEquals(IdeaStatus.SAVED, idea.status)
        assertEquals("trending", idea.source)
        assertEquals("A hot video", idea.title)
        assertEquals("Big channel", idea.reason)
    }

    @Test
    fun theBell_showsTheNotificationsTheAppSent_andCanClearThem() = runTest {
        val inbox = FakeNotificationInbox()
        val vm = NotificationInboxViewModel(inbox)
        keepActive(vm.entries)
        assertTrue(vm.entries.value!!.isEmpty())

        inbox.add(NotificationCategory.VIEW_MILESTONES, "10,000 views", "My channel passed it", 1)
        inbox.add(NotificationCategory.NEW_COMMENTS, "2 new comments", "Open Creatora", 2)
        assertEquals(listOf("2 new comments", "10,000 views"), vm.entries.value!!.map { it.title })

        vm.clear()
        assertTrue(vm.entries.value!!.isEmpty())
    }

    @Test
    fun theResolvedPlan_isProWhenPlayHasAPurchase_otherwiseTheDocument() = runTest {
        assertEquals(Plan.FREE, kit.plans.plan("uid1"))
        assertEquals(Plan.FREE, kit.plans.plan("someone-without-a-document"))

        kit.users.put(kit.users.docs.value.getValue("uid1").copy(plan = Plan.PRO))
        assertEquals(Plan.PRO, kit.plans.plan("uid1"))

        kit.users.put(kit.users.docs.value.getValue("uid1").copy(plan = Plan.FREE))
        kit.billing.current.value = BillingState(hasPro = true)
        assertEquals(Plan.PRO, kit.plans.plan("uid1"))
        assertFalse(Plan.FREE.dailyIdeas >= Plan.PRO.dailyIdeas)
        assertEquals(listOf(3, 10), listOf(Plan.FREE.dailyIdeas, Plan.PRO.dailyIdeas))
    }
}
