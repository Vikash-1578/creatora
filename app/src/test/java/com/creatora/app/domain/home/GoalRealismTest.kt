package com.creatora.app.domain.home

import com.creatora.app.domain.account.GoalMetric
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalRealismTest {
    @Test
    fun tenMillionFromSix_isOutOfReach_andASmallerTargetIsSuggested() {
        val r = goalRealism(GoalMetric.SUBSCRIBERS, 10_000_000, current = 6, recent28 = 2, days = 30)!!

        assertTrue(r.unrealistic)
        assertEquals(16L, r.suggested) // 6 now plus a stretch of 10
    }

    @Test
    fun aGoalNearThePace_isRealistic() {
        val r = goalRealism(GoalMetric.SUBSCRIBERS, 1_500, current = 1_000, recent28 = 300, days = 30)!!
        assertFalse(r.unrealistic)
        assertEquals(1_500L, r.suggested)
    }

    @Test
    fun otherMeasures_useTheirOwnPace() {
        assertTrue(goalRealism(GoalMetric.VIEWS, 5_000_000, null, recent28 = 1_000, days = 30)!!.unrealistic)
        assertFalse(goalRealism(GoalMetric.VIEWS, 2_000, null, recent28 = 1_000, days = 30)!!.unrealistic)
        assertTrue(goalRealism(GoalMetric.UPLOADS, 40, null, recent28 = 4, days = 30)!!.unrealistic)
    }

    @Test
    fun withoutNumbers_nothingIsJudged() {
        assertNull(goalRealism(GoalMetric.SUBSCRIBERS, 1_000_000, current = 6, recent28 = null, days = 30))
        assertNull(goalRealism(GoalMetric.SUBSCRIBERS, 1_000_000, current = null, recent28 = 5, days = 30))
        assertNull(goalRealism(GoalMetric.VIEWS, 1_000_000, null, recent28 = 5, days = 0))
    }

    @Test
    fun niceNumbers_roundUpToOneTwoOrFive() {
        assertEquals(1L, niceNumber(0.4))
        assertEquals(10L, niceNumber(10.0))
        assertEquals(20L, niceNumber(11.0))
        assertEquals(50L, niceNumber(33.0))
        assertEquals(100L, niceNumber(51.0))
        assertEquals(2_000L, niceNumber(1_200.0))
    }

    @Test
    fun largeNumbers_areGrouped_andNamedInLakhAndCrore() {
        assertEquals("10,000,000", goalNumber(10_000_000, Locale.US, "", ""))
        assertEquals("10,000,000 (1 crore)", goalNumber(10_000_000, Locale.US, "lakh", "crore"))
        assertEquals("500,000 (5 lakh)", goalNumber(500_000, Locale.US, "lakh", "crore"))
        assertEquals("12,345", goalNumber(12_345, Locale.US, "lakh", "crore"))
        assertEquals("6", goalNumber(6, Locale.US, "lakh", "crore"))
    }
}
