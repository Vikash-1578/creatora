package com.creatora.app.domain.grow

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GrowDomainTest {
    // ---------------------------------------------------------------- periods
    @Test
    fun periodSums_giveTheLatestPeriodFirst_andOnlyWholePeriods() {
        val daily = (1..10).map { it.toLong() } // 1..10
        assertEquals(listOf(49L), periodSums(daily, 7)) // the last 7 days (4..10); the 3 days left over are not a period
        assertEquals(listOf(19L, 15L, 11L, 7L, 3L), periodSums(daily, 2)) // 9+10, 7+8, ...
        assertTrue(periodSums(daily, 0).isEmpty())
        assertTrue(periodSums(daily, 11).isEmpty())
    }

    @Test
    fun usual_isTheMedianOfThePreviousPeriods_andNeedsAtLeastThree() {
        assertNull(usual(listOf(100L, 80L, 90L)))
        assertEquals(90.0, usual(listOf(500L, 80L, 90L, 100L))!!, 0.0)
        assertEquals(95.0, usual(listOf(500L, 80L, 90L, 100L, 110L))!!, 0.0)
        // Only six previous periods count.
        assertEquals(10.0, usual(listOf(1L, 10L, 10L, 10L, 10L, 10L, 10L, 9_999L))!!, 0.0)
    }

    @Test
    fun changeAndTrend_comeFromTheLastTwoPeriods() {
        assertEquals(25.0, changePercent(listOf(125L, 100L))!!, 0.001)
        assertNull(changePercent(listOf(5L)))
        assertNull(changePercent(listOf(5L, 0L))) // a percent of zero means nothing
        assertEquals(Trend.NoComparison, trendOf(listOf(5L)))
        assertEquals(Trend.Up(25.0), trendOf(listOf(125L, 100L)))
        assertEquals(Trend.Down(50.0), trendOf(listOf(50L, 100L)))
        assertEquals(Trend.Flat, trendOf(listOf(100L, 100L)))
    }

    @Test
    fun thinData_isAdmittedWithTheRealNumbers() {
        assertTrue(notEnoughData(99, 28))
        assertTrue(notEnoughData(5_000, 6))
        assertFalse(notEnoughData(100, 7))
        assertEquals(LocalDate.of(2026, 11, 10), dataAsOf(LocalDate.of(2026, 11, 12)))
    }

    @Test
    fun shares_arePercentsOfTheWhole_withTheRestAsOther() {
        val result = shares(mapOf("search" to 50.0, "suggested" to 30.0, "browse" to 15.0, "ext" to 5.0), take = 2)
        assertEquals(listOf("search", "suggested", "other"), result.map { it.label })
        assertEquals(listOf(50.0, 30.0, 20.0), result.map { it.percent })
        assertTrue(shares(emptyMap()).isEmpty())
        assertTrue(shares(mapOf("a" to 0.0)).isEmpty())
        assertEquals(listOf("a", "b"), shares(mapOf("a" to 1.0, "b" to 1.0), take = 5).map { it.label }) // no "other" when nothing is left
    }

    @Test
    fun rpm_needsBothNumbers() {
        assertEquals(2.5, rpm(25.0, 10_000)!!, 0.0001)
        assertNull(rpm(null, 10_000))
        assertNull(rpm(5.0, 0))
    }

    // ---------------------------------------------------------------- monetization journey
    private fun inputs(subs: Long? = 600, uploads: Int? = 4, hours: Double? = 2_310.0, shorts: Long? = 1_000_000) = MonetizationInputs(subs, uploads, hours, shorts)

    @Test
    fun theWatchHoursPathAndTheShortsPathAreCountedSeparately() {
        val tier1 = monetizationJourney(inputs()).first { it.tier == 1 }
        assertFalse(tier1.met) // 2,310 of 3,000 hours and 1M of 3M Shorts views
        val watch = tier1.paths.first { it.kind == PathKind.WATCH_HOURS }
        assertEquals(listOf(Requirement.SUBSCRIBERS, Requirement.UPLOADS_90, Requirement.WATCH_HOURS_365), watch.items.map { it.requirement })
        assertTrue(watch.items[0].met && watch.items[1].met && !watch.items[2].met)
        assertEquals(690.0, watch.items[2].remaining!!, 0.0001)
        assertEquals(PathKind.WATCH_HOURS, tier1.closest!!.kind) // closest through watch hours
    }

    @Test
    fun aTierIsMetByEitherPath() {
        assertTrue(monetizationJourney(inputs(hours = 3_500.0)).first { it.tier == 1 }.met)
        assertTrue(monetizationJourney(inputs(hours = 100.0, shorts = 3_100_000)).first { it.tier == 1 }.met)
        val tier2 = monetizationJourney(inputs(subs = 1_200, hours = 4_100.0)).first { it.tier == 2 }
        assertTrue(tier2.met)
        assertEquals(listOf(Requirement.SUBSCRIBERS, Requirement.WATCH_HOURS_365), tier2.paths.first { it.kind == PathKind.WATCH_HOURS }.items.map { it.requirement }) // tier 2 has no uploads rule
    }

    @Test
    fun unknownNumbersStayUnknown_notZero() {
        val unknown = monetizationJourney(MonetizationInputs(null, null, null, null)).first()
        assertTrue(unknown.paths.flatMap { it.items }.all { it.have == null && it.fraction == null && !it.met })
        assertNull(unknown.closest)
        val partial = monetizationJourney(MonetizationInputs(600, null, 1_500.0, null)).first()
        assertEquals(PathKind.WATCH_HOURS, partial.closest!!.kind)
        assertNull(partial.paths.first { it.kind == PathKind.WATCH_HOURS }.items[1].fraction) // the uploads count is unknown
    }

    @Test
    fun theRulesCarryTheDateTheyWereChecked() {
        assertEquals(500, PartnerProgram.tier1.subscribers)
        assertEquals(3_000_000L, PartnerProgram.tier1.shortsViews90)
        assertEquals(4_000, PartnerProgram.tier2.watchHours365)
        assertTrue(PartnerProgram.LAST_CHECKED.isNotBlank())
    }
}
