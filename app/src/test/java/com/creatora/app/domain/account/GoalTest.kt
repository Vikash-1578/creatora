package com.creatora.app.domain.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalTest {
    private val now = 1_000_000L
    private fun youtube(subs: Long?, id: String = "UC1") = ConnectedAccount(ConnectPlatform.YOUTUBE, id, "T", subscribers = subs)

    @Test
    fun newGoal_runsThirtyDays_andNeedsAPositiveTarget() {
        val goal = newGoal(GoalMetric.VIEWS, 50_000, " more views ", 2, emptyList(), now)!!
        assertEquals(now + 30L * 24 * 60 * 60 * 1000, goal.deadline)
        assertEquals("more views", goal.desire)
        assertEquals(2, goal.uploadsPerWeek)
        assertTrue(goal.primary)
        assertNull(newGoal(GoalMetric.VIEWS, 0, "", null, emptyList(), now))
        assertNull(newGoal(GoalMetric.VIEWS, -5, "", null, emptyList(), now))
    }

    @Test
    fun subscriberGoals_startFromTheRealTotal_orNotAtAll() {
        assertEquals(1_500L, newGoal(GoalMetric.SUBSCRIBERS, 1, "", null, listOf(youtube(1_000), youtube(500, "UC2")), now)?.startValue)
        assertNull(newGoal(GoalMetric.SUBSCRIBERS, 1, "", null, listOf(youtube(1_000), youtube(null, "UC2")), now)?.startValue)
        assertNull(newGoal(GoalMetric.SUBSCRIBERS, 1, "", null, emptyList(), now)?.startValue)
        assertNull(newGoal(GoalMetric.VIEWS, 1, "", null, listOf(youtube(1_000)), now)?.startValue)
        // A link-only account has no number, and does not block a start value from the connected channel.
        val moj = ConnectedAccount(ConnectPlatform.MOJ, "x", "T", link = "https://mojapp.in/@x")
        assertEquals(1_000L, newGoal(GoalMetric.SUBSCRIBERS, 1, "", null, listOf(youtube(1_000), moj), now)?.startValue)
    }

    @Test
    fun uploadsPerWeek_mustBeBetweenOneAndSeven_andTheDesireIsCapped() {
        assertNull(newGoal(GoalMetric.UPLOADS, 8, "", 12, emptyList(), now)?.uploadsPerWeek)
        assertEquals(500, newGoal(GoalMetric.UPLOADS, 8, "x".repeat(900), null, emptyList(), now)?.desire?.length)
    }

    @Test
    fun aimText_isWordedAsAnAim() {
        val goal = Goal(metric = GoalMetric.SUBSCRIBERS, target = 1_000, startedAt = 0, deadline = 1, startValue = 400)
        assertEquals("reach 1000 subscribers in total (starting from 400)", goal.aimText())
        assertEquals("publish 8 videos", Goal(metric = GoalMetric.UPLOADS, target = 8, startedAt = 0, deadline = 1).aimText())
    }
}
