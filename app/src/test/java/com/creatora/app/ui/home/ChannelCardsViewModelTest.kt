package com.creatora.app.ui.home

import com.creatora.app.domain.account.AdapterError
import com.creatora.app.domain.account.AdapterOutcome
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.account.ConnectedAccount
import com.creatora.app.domain.account.PeriodMetrics
import com.creatora.app.domain.account.VideoAnalytics
import com.creatora.app.domain.ai.AiTasks
import com.creatora.app.domain.home.AccountSelection
import com.creatora.app.domain.home.StreakUnit
import com.creatora.app.domain.home.UploadStatus
import com.creatora.app.domain.model.AiError
import com.creatora.app.domain.model.AiOutcome
import com.creatora.app.testutil.HomeKit
import com.creatora.app.testutil.MainDispatcherRule
import com.creatora.app.testutil.TestUser
import com.creatora.app.testutil.keepActive
import com.creatora.app.testutil.okReply
import com.creatora.app.testutil.preview
import com.creatora.app.testutil.sampleVideos
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChannelCardsViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val kit = HomeKit()

    private fun TestScope.open(): ChannelCardsViewModel {
        val vm = ChannelCardsViewModel(kit.context, kit.loader, kit.prefs, kit.auth, AiTasks(kit.ai), kit.cache)
        keepActive(vm.state)
        return vm
    }

    private fun feed(views: Long = 1_000, previousViews: Long = 800, revenue: Double? = null, previousRevenue: Double? = null) {
        kit.youtube.lookupResult = AdapterOutcome.Ok(preview("UC1", subs = 5_000))
        kit.youtube.videos = AdapterOutcome.Ok(sampleVideos(8))
        kit.youtube.metrics = AdapterOutcome.Ok(PeriodMetrics(views, 600, 30, 10, revenue))
        kit.youtube.previousMetrics = AdapterOutcome.Ok(PeriodMetrics(previousViews, 300, 20, 10, previousRevenue))
    }

    @Test
    fun withoutAnAccount_thereIsNothingToShow() = runTest {
        val vm = open()
        assertEquals(CardsPhase.NO_ACCOUNT, vm.state.value.phase)
        assertNull(vm.state.value.snapshot)
    }

    @Test
    fun theSnapshot_hasRealNumbers_andTheirChange() = runTest {
        feed(views = 1_000, previousViews = 800, revenue = 40.0, previousRevenue = 20.0)
        kit.connect()
        val vm = open()

        val state = vm.state.value
        assertEquals(CardsPhase.READY, state.phase)
        val snapshot = state.snapshot!!
        assertEquals(5_000L, snapshot.subscribers)
        assertEquals(1_000.0, snapshot.views!!.current, 0.001)
        assertEquals(200.0, snapshot.views!!.delta!!, 0.001)
        assertEquals(10.0, snapshot.watchHours!!.current, 0.001) // 600 minutes
        assertEquals(20.0, snapshot.subscribersNet!!.current, 0.001)
        assertEquals(40.0, snapshot.earningsUsd!!.current, 0.001)
    }

    @Test
    fun earnings_areLeftOut_whenTheChannelIsNotMonetized() = runTest {
        feed(revenue = null)
        kit.connect()
        assertNull(open().state.value.snapshot!!.earningsUsd)
    }

    @Test
    fun allAccounts_sumsTheNumbers_andNeverAveragesCtrOrRetention() = runTest {
        feed(views = 1_000, revenue = 10.0)
        kit.connect("UC1", "One")
        kit.connect("UC2", "Two")
        kit.youtube.lookupResult = AdapterOutcome.Ok(preview("UC1", subs = 5_000))
        val vm = open()
        vm.state.value // single account selected first (the first account)
        kit.prefs.selectedKey.value = "all"

        val all = vm.state.value
        assertEquals(2, all.accountCount)
        assertEquals(2_000.0, all.snapshot!!.views!!.current, 0.001)
        assertEquals(10_000L, all.snapshot!!.subscribers)
        assertTrue(all.uploadMetrics.isEmpty()) // CTR and retention need one account
    }

    @Test
    fun recentUploads_haveAStatus_andCtrAndRetentionForASingleAccount() = runTest {
        feed()
        kit.youtube.videoMetricsResult = AdapterOutcome.Ok(listOf(VideoAnalytics("v0", ctrPercent = 4.2, averageViewPercent = 51.0)))
        kit.connect()
        val vm = open()

        val rows = vm.state.value.uploads
        assertEquals(5, rows.size)
        assertEquals("v0", rows.first().video.id)
        assertTrue(rows.all { it.status != UploadStatus.UNKNOWN })
        assertEquals(4.2, vm.state.value.uploadMetrics.getValue("v0").ctrPercent!!, 0.001)
    }

    @Test
    fun theStreak_followsTheChosenUnit_andTheChoiceIsSaved() = runTest {
        feed()
        kit.connect()
        val vm = open()
        assertEquals(StreakUnit.WEEKS, vm.state.value.streakUnit)
        assertNotNull(vm.state.value.streak)

        vm.setStreakUnit(StreakUnit.DAYS)

        assertEquals(StreakUnit.DAYS, kit.prefs.streakUnit.value)
        assertEquals(StreakUnit.DAYS, vm.state.value.streak!!.unit)
    }

    @Test
    fun hidingAmounts_isRemembered() = runTest {
        feed(revenue = 10.0)
        kit.connect()
        val vm = open()
        assertFalse(vm.state.value.hideAmounts)

        vm.setHideAmounts(true)

        assertTrue(kit.prefs.hideAmounts.value)
        assertTrue(vm.state.value.hideAmounts)
    }

    @Test
    fun aFailedChannel_isShownAsFailed_andRetryLoadsAgain() = runTest {
        kit.youtube.videos = AdapterOutcome.Failed(AdapterError.QuotaExceeded)
        kit.connect()
        val vm = open()
        assertEquals(CardsPhase.FAILED, vm.state.value.phase)
        assertEquals(AdapterError.QuotaExceeded, vm.state.value.error)

        feed()
        vm.refresh()

        assertEquals(CardsPhase.READY, vm.state.value.phase)
    }

    @Test
    fun accountsOfOtherPlatforms_saySo() = runTest {
        kit.accounts.save("uid1", ConnectedAccount(ConnectPlatform.INSTAGRAM, "ig", "Insta"))
        val vm = open()
        assertEquals(CardsPhase.UNSUPPORTED, vm.state.value.phase)
        assertEquals(AdapterError.ComingSoon, vm.state.value.error)
    }

    @Test
    fun missingAnalytics_keepsTheRestOfTheSnapshot() = runTest {
        feed()
        kit.youtube.metrics = AdapterOutcome.Failed(AdapterError.NotAuthorized)
        kit.connect()

        val state = open().state.value

        assertEquals(CardsPhase.READY, state.phase)
        assertEquals(AdapterError.NotAuthorized, state.metricsError)
        assertNull(state.snapshot!!.views)
        assertEquals(5_000L, state.snapshot!!.subscribers)
    }

    @Test
    fun explainingAnUpload_usesItsRealNumbers_andIsCachedForTheVideo() = runTest {
        feed()
        kit.youtube.videoMetricsResult = AdapterOutcome.Ok(listOf(VideoAnalytics("v0", ctrPercent = 4.2)))
        kit.ai.outcome = okReply("It did well because the title was clear.")
        kit.connect()
        val vm = open()
        val row = vm.state.value.uploads.first()

        vm.explain(row)

        assertEquals(WhyState.Ready(row, "It did well because the title was clear."), vm.state.value.why)
        val prompt = kit.ai.requests.single().messages.single().text
        assertTrue("Click-through rate: 4.2%" in prompt && "Views: ${row.video.views}" in prompt)
        assertTrue("Average percentage viewed: not available" in prompt)

        vm.closeWhy()
        vm.explain(row)
        assertEquals(1, kit.ai.requests.size) // the second time it comes from the cache
        assertEquals(1, kit.cache.puts)
        assertEquals(TestUser.uid, "uid1")
    }

    @Test
    fun anAiFailure_isShown_andNothingIsCached() = runTest {
        feed()
        kit.ai.outcome = AiOutcome.Failed(listOf(AiError.LimitReached))
        kit.connect()
        val vm = open()
        val row = vm.state.value.uploads.first()

        vm.explain(row)

        assertEquals(WhyState.Failed(row, listOf(AiError.LimitReached)), vm.state.value.why)
        assertEquals(0, kit.cache.puts)
    }
}
