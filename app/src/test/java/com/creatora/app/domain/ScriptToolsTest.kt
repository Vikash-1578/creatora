package com.creatora.app.domain

import com.creatora.app.domain.model.Project
import com.creatora.app.domain.model.applyPronunciations
import com.creatora.app.domain.model.estimateRender
import com.creatora.app.domain.model.readingSeconds
import com.creatora.app.domain.model.wordCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptToolsTest {
    @Test
    fun wordCount_countsWordsAcrossSpacesAndNewlines() {
        assertEquals(0, wordCount(""))
        assertEquals(0, wordCount("  \n "))
        assertEquals(5, wordCount("One two  three\nfour\tfive "))
    }

    @Test
    fun readingSeconds_uses150WordsPerMinute() {
        assertEquals(0, readingSeconds(0))
        assertEquals(60, readingSeconds(150))
        assertEquals(2, readingSeconds(5))
        assertEquals(120, readingSeconds(300))
    }

    @Test
    fun applyPronunciations_replacesWholeWordsIgnoringCase() {
        val notes = "Creatora = kree-uh-TOR-uh\nGIF = jif"
        assertEquals(
            "Try kree-uh-TOR-uh, a jif maker. Creatoras stays.",
            applyPronunciations("Try creatora, a GIF maker. Creatoras stays.", notes),
        )
    }

    @Test
    fun applyPronunciations_skipsBadLinesAndSpecialCharacters() {
        assertEquals("a.b and a+b", applyPronunciations("a.b and a+b", "no equals sign\n= empty word\nword =\n"))
        assertEquals("a dot b", applyPronunciations("a.b", "a.b = a dot b"))
        assertEquals("unchanged", applyPronunciations("unchanged", ""))
    }

    @Test
    fun estimateRender_scalesWithLength() {
        val none = estimateRender(0)
        assertEquals(0, none.approxSizeMb)
        assertEquals(0, none.approxRenderMinutes)

        val short = estimateRender(10)
        assertEquals(10, short.approxSizeMb)
        assertEquals(1, short.approxRenderMinutes)

        val long = estimateRender(600)
        assertEquals(600, long.approxSizeMb)
        assertEquals(15, long.approxRenderMinutes)
    }

    @Test
    fun project_scriptJoinsNonBlankSectionsAndTracksProgress() {
        val project = Project(title = "p", hook = "Hook", structure = " ", callToAction = "Subscribe")
        assertEquals("Hook\n\nSubscribe", project.script)
        assertFalse(project.isInProgress)
        assertTrue(project.copy(format = com.creatora.app.domain.model.VideoFormat.LONG_VIDEO).isInProgress)
        assertFalse(project.copy(format = com.creatora.app.domain.model.VideoFormat.LONG_VIDEO, isReady = true).isInProgress)
        assertFalse(project.copy(format = com.creatora.app.domain.model.VideoFormat.LONG_VIDEO, isTemplate = true).isInProgress)
    }
}
