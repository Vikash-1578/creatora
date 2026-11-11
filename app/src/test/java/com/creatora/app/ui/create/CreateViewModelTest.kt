package com.creatora.app.ui.create

import com.creatora.app.domain.model.VideoFormat
import com.creatora.app.testutil.FakeProjectRepository
import com.creatora.app.testutil.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class CreateViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val projects = FakeProjectRepository()

    @Test
    fun start_createsAProjectInTheChosenFormatAndReturnsItsId() {
        var createdId = 0L

        CreateViewModel(projects).start(VideoFormat.CARTOON_STORY, "  Untitled Cartoon story ", onCreated = { createdId = it })

        val project = projects.items.value.single()
        assertEquals(createdId, project.id)
        assertEquals("Untitled Cartoon story", project.title)
        assertEquals(VideoFormat.CARTOON_STORY, project.format)
        assertEquals(0, project.step)
        assertFalse(project.isReady)
        assertFalse(project.isTemplate)
    }
}
