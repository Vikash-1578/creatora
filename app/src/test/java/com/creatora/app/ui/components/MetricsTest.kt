package com.creatora.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricsTest {
    @Test
    fun sparkPoints_scaleFromLowestToHighest() {
        assertEquals(listOf(0f, 0.5f, 1f), sparkPoints(listOf(10, 15, 20)))
        assertEquals(listOf(1f, 0f, 0.5f), sparkPoints(listOf(200, 100, 150)))
    }

    @Test
    fun sparkPoints_drawNothingForTooFewPoints_andSitInTheMiddleWhenFlat() {
        assertTrue(sparkPoints(emptyList()).isEmpty())
        assertTrue(sparkPoints(listOf(5)).isEmpty())
        assertEquals(listOf(0.5f, 0.5f, 0.5f), sparkPoints(listOf(7, 7, 7)))
    }
}
