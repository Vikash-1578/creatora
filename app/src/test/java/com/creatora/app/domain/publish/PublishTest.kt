package com.creatora.app.domain.publish

import com.creatora.app.domain.model.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PublishTest {
    private val ready = Project(
        title = "p", renderUri = "file:///r.mp4", captionsIncluded = true, publishTitle = "A good title",
        thumbnailUri = "file:///t.png", aiContent = true, madeForKids = false,
    )

    private fun done(project: Project) = publishChecklist(project).associate { it.item to it.done }

    @Test
    fun aFinishedProject_passesEveryCheck() {
        assertTrue(publishChecklist(ready).allDone())
        assertEquals(ChecklistItem.entries, publishChecklist(ready).map { it.item })
    }

    @Test
    fun aNewProject_failsEveryCheck() {
        assertTrue(publishChecklist(Project(title = "new")).none { it.done })
    }

    @Test
    fun eachCheckDependsOnItsOwnField() {
        assertFalse(done(ready.copy(renderUri = null)).getValue(ChecklistItem.RENDERED))
        assertFalse(done(ready.copy(publishTitle = "  ")).getValue(ChecklistItem.TITLE))
        assertFalse(done(ready.copy(thumbnailUri = null)).getValue(ChecklistItem.THUMBNAIL))
        assertFalse(done(ready.copy(captionsIncluded = false)).getValue(ChecklistItem.CAPTIONS))
        assertFalse(done(ready.copy(aiContent = null)).getValue(ChecklistItem.AI_LABEL))
        assertFalse(done(ready.copy(madeForKids = null)).getValue(ChecklistItem.MADE_FOR_KIDS))
    }

    @Test
    fun answeringNoCountsAsAnswered() {
        val answeredNo = done(ready.copy(aiContent = false, madeForKids = false))
        assertTrue(answeredNo.getValue(ChecklistItem.AI_LABEL) && answeredNo.getValue(ChecklistItem.MADE_FOR_KIDS))
    }

    @Test
    fun titleMustFitYouTubesLimit() {
        assertTrue(done(ready.copy(publishTitle = "a".repeat(MAX_TITLE_LENGTH))).getValue(ChecklistItem.TITLE))
        assertFalse(done(ready.copy(publishTitle = "a".repeat(MAX_TITLE_LENGTH + 1))).getValue(ChecklistItem.TITLE))
    }

    @Test
    fun postText_isTheTitleThenTheDescription() {
        assertEquals("Title\n\nBody #tag", postText(Project(title = "x", publishTitle = " Title ", publishDescription = " Body #tag ")))
        assertEquals("Title", postText(Project(title = "x", publishTitle = "Title")))
        assertEquals("", postText(Project(title = "x")))
    }

    @Test
    fun reminderDelay_isOnlyForTheFuture() {
        assertEquals(5_000L, reminderDelayMillis(15_000, 10_000))
        assertNull(reminderDelayMillis(10_000, 10_000))
        assertNull(reminderDelayMillis(9_000, 10_000))
    }
}
