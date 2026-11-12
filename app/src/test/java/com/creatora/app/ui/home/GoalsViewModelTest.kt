package com.creatora.app.ui.home

import com.creatora.app.domain.account.AdapterOutcome
import com.creatora.app.domain.account.Goal
import com.creatora.app.domain.account.GoalMetric
import com.creatora.app.domain.account.PeriodMetrics
import com.creatora.app.domain.ai.AiTasks
import com.creatora.app.domain.home.GoalMissing
import com.creatora.app.domain.model.AiError
import com.creatora.app.domain.model.AiOutcome
import com.creatora.app.testutil.FakeGoalRepository
import com.creatora.app.testutil.HomeKit
import com.creatora.app.testutil.MainDispatcherRule
import com.creatora.app.testutil.keepActive
import com.creatora.app.testutil.okReply
import com.creatora.app.testutil.preview
import com.creatora.app.testutil.sampleVideos
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GoalsViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val kit = HomeKit()
    private val goals = FakeGoalRepository()
    private val day = 24L * 60 * 60 * 1000

    private fun TestScope.open(): GoalsViewModel {
        val vm = GoalsViewModel(kit.context, goals, kit.loader, kit.registry, AiTasks(kit.ai), kit.cache, kit.auth)
        keepActive(vm.state)
        return vm
    }

    private suspend fun channel(subs: Long = 1_400) {
        kit.youtube.lookupResult = AdapterOutcome.Ok(preview("UC1", subs = subs))
        kit.youtube.videos = AdapterOutcome.Ok(sampleVideos(8))
        kit.connect()
    }

    private fun goal(metric: GoalMetric = GoalMetric.SUBSCRIBERS, target: Long = 2_000, start: Long? = 1_000, key: String? = null, startedAt: Long = System.currentTimeMillis() - 10 * day) =
        Goal(metric = metric, target = target, startedAt = startedAt, deadline = startedAt + 30 * day, startValue = start, accountKey = key, primary = true)

    @Test
    fun aSubscriberGoal_showsRealProgress_fromTheStartingCount() = runTest {
        channel(subs = 1_400)
        goals.save("uid1", goal())

        val item = open().state.value.goals.single()

        assertEquals(1_400L, item.progress!!.current) // today's total, from the starting 1,000 toward 2,000
        assertEquals(0.4f, item.progress!!.fraction!!, 0.001f)
        assertEquals(false, item.progress!!.reached)
    }

    @Test
    fun aViewsGoal_usesTheViewsSinceItStarted_andAnUploadsGoalCountsRealUploads() = runTest {
        channel()
        kit.youtube.metrics = AdapterOutcome.Ok(PeriodMetrics(1_200, 0, 0, 0))
        goals.save("uid1", goal(GoalMetric.VIEWS, 2_000, null))
        goals.save("uid1", goal(GoalMetric.UPLOADS, 5, null))

        val items = open().state.value.goals.associateBy { it.goal.metric }

        assertEquals(1_200L, items.getValue(GoalMetric.VIEWS).progress!!.current)
        assertEquals(3L, items.getValue(GoalMetric.UPLOADS).progress!!.current) // uploads 3, 6 and 9 days ago are inside the last 10 days
    }

    @Test
    fun withoutAStartingCount_orWithoutData_noBarIsMadeUp() = runTest {
        channel()
        goals.save("uid1", goal(start = null))
        goals.save("uid1", goal(GoalMetric.EARNINGS, 100, null)) // revenue is not available

        val items = open().state.value.goals.associateBy { it.goal.metric }

        assertEquals(1_400L, items.getValue(GoalMetric.SUBSCRIBERS).progress!!.current) // no start value: still today's real total
        assertNull(items.getValue(GoalMetric.SUBSCRIBERS).progress!!.missing)
        assertEquals(GoalMissing.NO_DATA, items.getValue(GoalMetric.EARNINGS).progress!!.missing)
    }

    @Test
    fun aGoalReached_isCelebratedOnce_andRemembered() = runTest {
        channel(subs = 1_400)
        goals.save("uid1", goal(target = 300))
        val vm = open()

        assertEquals(300L, vm.state.value.celebrating!!.target)
        assertNotNull(goals.byUser.value.getValue("uid1").single().reachedAt)

        vm.dismissCelebration()
        assertNull(vm.state.value.celebrating)
        val reopened = open()
        assertNull(reopened.state.value.celebrating) // it was already marked as reached
    }

    @Test
    fun goalsFollowTheSelectedAccount_andGoalsForAllAccountsAlwaysShow() = runTest {
        channel()
        kit.connect("UC2", "Two")
        goals.save("uid1", goal(key = "YOUTUBE_UC1"))
        goals.save("uid1", goal(GoalMetric.VIEWS, 10, null, key = "YOUTUBE_UC2"))
        goals.save("uid1", goal(GoalMetric.UPLOADS, 4, null, key = null))
        val vm = open()

        assertEquals(setOf(GoalMetric.SUBSCRIBERS, GoalMetric.UPLOADS), vm.state.value.goals.map { it.goal.metric }.toSet())

        kit.prefs.selectedKey.value = "YOUTUBE_UC2"
        assertEquals(setOf(GoalMetric.VIEWS, GoalMetric.UPLOADS), vm.state.value.goals.map { it.goal.metric }.toSet())

        kit.prefs.selectedKey.value = "all"
        assertEquals(3, vm.state.value.goals.size)
    }

    @Test
    fun addEditAndDelete() = runTest {
        channel()
        val vm = open()

        vm.add(GoalMetric.VIEWS, 5_000, days = 7, forAll = false, startValue = null)
        val added = goals.byUser.value.getValue("uid1").single()
        assertEquals(5_000L, added.target)
        assertEquals(7 * day, added.deadline - added.startedAt)
        assertEquals("YOUTUBE_UC1", added.accountKey)
        assertEquals(false, added.primary)

        vm.add(GoalMetric.UPLOADS, 0, days = 7, forAll = true, startValue = null) // a zero target is ignored
        assertEquals(1, goals.byUser.value.getValue("uid1").size)

        vm.edit(added, target = 8_000, days = 30)
        val edited = goals.byUser.value.getValue("uid1").single()
        assertEquals(8_000L, edited.target)
        assertEquals(30 * day, edited.deadline - edited.startedAt)

        vm.delete(edited)
        assertTrue(goals.byUser.value.getValue("uid1").isEmpty())
    }

    @Test
    fun theWeeklyPlan_isMadeFromRealProgress_andOncePerWeek() = runTest {
        channel(subs = 1_400)
        goals.save("uid1", goal())
        kit.ai.outcome = okReply("Publish two videos and test one new title.")
        val vm = open()
        val item = vm.state.value.goals.single()

        vm.plan(item)

        val plan = vm.state.value.plan as PlanState.Ready
        assertEquals("Publish two videos and test one new title.", plan.text)
        val request = kit.ai.requests.single()
        assertTrue("40% of the target" in request.messages.single().text && "reach 2000 subscribers in total" in request.messages.single().text)
        assertTrue("never promise" in request.system)

        vm.plan(item)
        assertEquals(1, kit.ai.requests.size) // cached for this week

        vm.plan(item, refresh = true)
        assertEquals(2, kit.ai.requests.size)
    }

    @Test
    fun aPlanThatFails_isShown() = runTest {
        channel()
        goals.save("uid1", goal())
        kit.ai.outcome = AiOutcome.Failed(listOf(AiError.LimitReached))
        val vm = open()

        vm.plan(vm.state.value.goals.single())

        assertEquals(PlanState.Failed(listOf(AiError.LimitReached)), vm.state.value.plan)
    }

    @Test
    fun anOutOfReachGoal_isNotSavedUntilTheUserChooses_andComesWithAKindNote() = runTest {
        channel(subs = 6)
        kit.youtube.metrics = AdapterOutcome.Ok(PeriodMetrics(100, 0, 3, 1)) // +2 subscribers in 28 days
        kit.ai.outcome = okReply("That is ambitious for 30 days. 16 is a realistic first step.")
        val vm = open()

        vm.add(GoalMetric.SUBSCRIBERS, 10_000_000, days = 30, forAll = false, startValue = null)

        assertTrue(goals.byUser.value["uid1"].isNullOrEmpty())
        val advice = vm.state.value.advice!!
        assertEquals(16L, advice.suggested) // 6 now plus 10
        assertEquals("That is ambitious for 30 days. 16 is a realistic first step.", advice.message)
        assertEquals(6L, advice.startValue) // starts from the real count
        assertTrue("Now: 6" in kit.ai.requests.single().messages.single().text)

        vm.answerAdvice(useSuggested = true)

        val saved = goals.byUser.value.getValue("uid1").single()
        assertEquals(16L, saved.target)
        assertEquals(6L, saved.startValue)
        assertNull(vm.state.value.advice)
    }

    @Test
    fun theUserCanKeepTheirOwnAmbitiousTarget() = runTest {
        channel(subs = 6)
        kit.youtube.metrics = AdapterOutcome.Ok(PeriodMetrics(100, 0, 3, 1))
        val vm = open()
        vm.add(GoalMetric.SUBSCRIBERS, 10_000_000, days = 30, forAll = false, startValue = null)

        vm.answerAdvice(useSuggested = false)

        assertEquals(10_000_000L, goals.byUser.value.getValue("uid1").single().target)
    }

    @Test
    fun aReachableGoal_orOneWithoutData_isSavedAtOnce() = runTest {
        channel(subs = 1_000)
        kit.youtube.metrics = AdapterOutcome.Ok(PeriodMetrics(100, 0, 300, 0)) // +300 a month
        val vm = open()

        vm.add(GoalMetric.SUBSCRIBERS, 1_500, days = 30, forAll = false, startValue = null)

        assertNull(vm.state.value.advice)
        assertEquals(1_000L, goals.byUser.value.getValue("uid1").single().startValue)
    }
}
