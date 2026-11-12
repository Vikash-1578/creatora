package com.creatora.app.domain.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IdeasLogicTest {
    @Test
    fun parseDailyIdeas_readsTitleScoreAndReason_andCapsTheList() {
        val text = """
            IDEA: Ten chaat spots in Delhi | SCORE: 82 | WHY: Your food videos average 5,000 views.
            - IDEA: A day at the market | SCORE: 140 | WHY: Short videos did well.
            IDEA: Missing parts
            not an idea line
            IDEA:   | SCORE: 50 | WHY: no title
            IDEA: ten chaat spots in delhi | SCORE: 10 | WHY: a duplicate
            IDEA: Fourth | SCORE: 5% | WHY: x
        """.trimIndent()

        val ideas = parseDailyIdeas(text, max = 3)

        assertEquals(listOf("Ten chaat spots in Delhi", "A day at the market", "Missing parts"), ideas.map { it.title })
        assertEquals(82, ideas[0].score)
        assertEquals("Your food videos average 5,000 views.", ideas[0].reason)
        assertEquals(100, ideas[1].score) // capped
        assertNull(ideas[2].score)
        assertEquals("", ideas[2].reason)
    }

    @Test
    fun parseFocus_needsATip() {
        val focus = parseFocus("TIP: Post a short this week. Your last five average 850 views.\nSOURCE: Average views 850 vs 600")
        assertEquals("Post a short this week. Your last five average 850 views.", focus!!.tip)
        assertEquals("Average views 850 vs 600", focus.source)
        assertNull(parseFocus("SOURCE: only a source"))
        assertNull(parseFocus("no markers at all"))
    }

    @Test
    fun ideaStatus_covers_theLifecycle() {
        assertTrue(IdeaStatus.entries.map { it.name }.containsAll(listOf("NEW", "SAVED", "DISMISSED", "PLANNED", "MADE")))
    }
}
