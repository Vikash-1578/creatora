package com.creatora.app.ui.home

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeFormatTest {
    private val us = Locale.US

    @Test
    fun compactCount_keepsSmallNumbersWhole_andShortensBigOnes() {
        assertEquals("950", compactCount(950, us))
        assertEquals("9,999", compactCount(9_999, us))
        assertEquals("12K", compactCount(12_000, us))
        assertEquals("1.5M", compactCount(1_500_000, us))
        assertEquals("2B", compactCount(2_000_000_000, us))
    }

    @Test
    fun signedCompact_andPercent() {
        assertEquals("+950", signedCompact(950, us))
        assertEquals("-300", signedCompact(-300, us))
        assertEquals("0", signedCompact(0, us))
        assertEquals("+12%", percentText(12.0, us))
        assertEquals("-8%", percentText(-8.0, us))
    }
}
