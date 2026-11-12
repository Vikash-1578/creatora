package com.creatora.app.ui.research

import com.creatora.app.domain.account.AdapterError
import com.creatora.app.domain.account.AdapterOutcome
import com.creatora.app.domain.account.ChannelPreview
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.account.CreatorProfile
import com.creatora.app.domain.account.Plan
import com.creatora.app.domain.home.dayKey
import com.creatora.app.domain.repository.Competitor
import com.creatora.app.domain.research.Fetched
import com.creatora.app.testutil.FakeCompetitorRepository
import com.creatora.app.testutil.FakeResearchRepository
import com.creatora.app.testutil.HomeKit
import com.creatora.app.testutil.MainDispatcherRule
import com.creatora.app.testutil.keepActive
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CompetitorsViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val kit = HomeKit()
    private val research = FakeResearchRepository()
    private val competitors = FakeCompetitorRepository()

    private fun channel(id: String, subs: Long? = 1_000) = ChannelPreview(ConnectPlatform.YOUTUBE, id, "Channel $id", photoUrl = "https://img/$id", subscribers = subs, videoCount = 12, viewCount = 34_000)

    private fun TestScope.open(): CompetitorsViewModel {
        val vm = CompetitorsViewModel(kit.auth, competitors, research, kit.plans, kit.users, kit.context)
        keepActive(vm.ui)
        return vm
    }

    private fun setPlan(plan: Plan) = kit.users.put(kit.users.docs.value.getValue("uid1").copy(plan = plan))

    @Test
    fun addingByLink_followsTheChannel_andStartsItsGrowthHistory() = runTest {
        research.lookupResult = AdapterOutcome.Ok(channel("UC9"))
        val vm = open()

        vm.addByLink("https://www.youtube.com/@rival")

        val competitor = vm.ui.value.competitors.single()
        assertEquals("UC9", competitor.channelId)
        assertEquals("manual", competitor.source)
        assertEquals(1_000L, competitor.subscribers)
        assertNull(vm.ui.value.problem)
        assertEquals(listOf("lookup:@rival"), research.calls)
        val snapshot = competitors.snapshotsByChannel["UC9"]!!.single()
        assertEquals(dayKey(System.currentTimeMillis()), snapshot.day) // "tracking since" starts now
        assertEquals(34_000L, snapshot.views)
    }

    @Test
    fun aLinkThatIsNotAYouTubeChannel_isRefusedWithoutAnyRequest() = runTest {
        val vm = open()

        vm.addByLink("hello world")
        assertEquals(AddProblem.InvalidLink, vm.ui.value.problem)
        vm.addByLink("https://www.instagram.com/someone")
        assertEquals(AddProblem.NotYouTube, vm.ui.value.problem)

        assertTrue(research.calls.isEmpty())
        assertTrue(vm.ui.value.competitors.isEmpty())
    }

    @Test
    fun aFailedLookup_isShown_andNothingIsAdded() = runTest {
        research.lookupResult = AdapterOutcome.Failed(AdapterError.NotFound)
        val vm = open()

        vm.addByLink("@nobody")

        assertEquals(AddProblem.Failed(AdapterError.NotFound), vm.ui.value.problem)
        assertTrue(vm.ui.value.competitors.isEmpty())
        assertEquals(false, vm.ui.value.adding)
    }

    @Test
    fun theFreePlanFollowsThree_andProFollowsTwenty() = runTest {
        val free = open()
        assertEquals(3, free.ui.value.limit)
        (1..3).forEach { research.lookupResult = AdapterOutcome.Ok(channel("UC$it")); free.addByLink("@c$it") }
        research.lookupResult = AdapterOutcome.Ok(channel("UC4"))

        free.addByLink("@c4")

        assertEquals(AddProblem.LimitReached, free.ui.value.problem)
        assertEquals(3, free.ui.value.competitors.size)

        setPlan(Plan.PRO)
        val pro = open()
        assertEquals(20, pro.ui.value.limit)
        pro.addByLink("@c4")
        assertNull(pro.ui.value.problem)
        assertEquals(4, pro.ui.value.competitors.size)
    }

    @Test
    fun duplicates_andTheUsersOwnChannel_arePolitelyRefused() = runTest {
        kit.connect("UC1")
        val vm = open()

        research.lookupResult = AdapterOutcome.Ok(channel("UC1"))
        vm.addByLink("@mine")
        assertEquals(AddProblem.OwnChannel, vm.ui.value.problem)

        research.lookupResult = AdapterOutcome.Ok(channel("UC9"))
        vm.addByLink("@rival")
        vm.addByLink("@rival")
        assertEquals(AddProblem.Duplicate, vm.ui.value.problem)
        assertEquals(1, vm.ui.value.competitors.size)
    }

    @Test
    fun removing_stopsFollowing() = runTest {
        val vm = open()
        research.lookupResult = AdapterOutcome.Ok(channel("UC9"))
        vm.addByLink("@rival")

        vm.remove(vm.ui.value.competitors.single())

        assertTrue(vm.ui.value.competitors.isEmpty())
    }

    private fun withNiche(niche: String?) = kit.users.put(kit.users.docs.value.getValue("uid1").copy(creatorProfile = niche?.let { CreatorProfile(it) }))

    @Test
    fun discovery_runsOnlyWhenAsked_andNeedsTheNiche() = runTest {
        val vm = open()
        assertEquals(Suggestions.Idle, vm.ui.value.suggestions)
        assertTrue(research.calls.isEmpty()) // opening the tab costs no quota

        vm.discover()

        assertEquals(Suggestions.NeedsProfile, vm.ui.value.suggestions)
        assertTrue(research.calls.isEmpty())
    }

    @Test
    fun discovery_suggestsNewChannels_usingOwnAndFollowedChannelsAsSources() = runTest {
        withNiche("street food")
        kit.connect("UC1")
        competitors.list.value = listOf(Competitor("UC5", "Followed"))
        research.discoverResult = Fetched.Ok(listOf(channel("UC1"), channel("UC5"), channel("A"), channel("B")), cachedAt = 7, fromCache = true)
        val vm = open()

        vm.discover()

        val ready = vm.ui.value.suggestions as Suggestions.Ready
        assertEquals(listOf("A", "B"), ready.channels.map { it.id }) // not the user's own channel, not one already followed
        assertEquals(7L, ready.cachedAt)
        assertEquals(listOf("discover:street food:UC1,UC5:false"), research.calls)

        vm.discover(force = true)
        assertEquals("discover:street food:UC1,UC5:true", research.calls.last())
    }

    @Test
    fun followingASuggestion_removesItFromTheList() = runTest {
        withNiche("street food")
        research.discoverResult = Fetched.Ok(listOf(channel("A"), channel("B")), cachedAt = 7, fromCache = false)
        val vm = open()
        vm.discover()

        vm.addSuggestion((vm.ui.value.suggestions as Suggestions.Ready).channels.first())

        assertEquals("auto", vm.ui.value.competitors.single().source)
        assertEquals(listOf("B"), (vm.ui.value.suggestions as Suggestions.Ready).channels.map { it.id })
    }

    @Test
    fun discoveryOutOfQuota_isAMessage_notAFakeList() = runTest {
        withNiche("street food")
        research.discoverResult = Fetched.Failed(AdapterError.QuotaExceeded)
        val vm = open()

        vm.discover()

        assertEquals(Suggestions.Failed(AdapterError.QuotaExceeded), vm.ui.value.suggestions)
    }
}
