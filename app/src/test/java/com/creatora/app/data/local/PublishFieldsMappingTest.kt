package com.creatora.app.data.local

import android.app.Application
import com.creatora.app.domain.model.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PublishFieldsMappingTest {
    @Test
    fun thePublishFields_surviveTheRoundTripToTheDatabaseRow() {
        val project = Project(
            id = 9, title = "Dal", publishTitle = "Dal in 10 minutes", publishDescription = "Line one.\nLine two.", publishHashtags = listOf("food", "dal"),
            publishTags = listOf("cooking", "easy dal"), thumbnailUri = "file:///t.png", aiContent = true, madeForKids = false,
            captions = mapOf("INSTAGRAM" to "Short caption", "TIKTOK" to "Another"),
        )

        assertEquals(project, project.toEntity().toModel())
    }

    @Test
    fun unansweredQuestionsStayUnanswered_andAnOldRowReadsAsEmpty() {
        val old = Project(title = "Old").toEntity().copy(publishTitle = null, publishHashtags = null, captions = null, aiContent = null, madeForKids = null).toModel()

        assertEquals("", old.publishTitle)
        assertEquals(emptyList<String>(), old.publishHashtags)
        assertEquals(emptyMap<String, String>(), old.captions)
        assertNull(old.aiContent) // never defaulted to "no"
        assertNull(old.madeForKids)
    }
}
