package com.creatora.app.ui.saved

import com.creatora.app.domain.model.Idea
import com.creatora.app.domain.model.VideoFormat
import com.creatora.app.domain.research.SwipeItem
import com.creatora.app.testutil.FakeIdeaRepository
import com.creatora.app.testutil.FakeProjectRepository
import com.creatora.app.testutil.FakeSwipeRepository
import com.creatora.app.testutil.MainDispatcherRule
import com.creatora.app.testutil.keepActive
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SavedViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val ideas = FakeIdeaRepository()
    private val swipe = FakeSwipeRepository()
    private val projects = FakeProjectRepository()

    @Test
    fun addIdea_trimsAndIgnoresBlank() = runTest {
        val viewModel = SavedViewModel(ideas, swipe, projects)
        keepActive(viewModel.uiState)

        viewModel.addIdea("  Great idea ", " notes ")
        viewModel.addIdea("   ", "x")

        val idea = viewModel.uiState.value.ideas.single()
        assertEquals("Great idea", idea.title)
        assertEquals("notes", idea.notes)
    }

    @Test
    fun useIdea_startsAProjectWithTheIdeaAsItsBriefAndLinksThem() = runTest {
        val id = ideas.save(Idea(title = "Cheap gear", notes = "from https://youtu.be/x"))
        val viewModel = SavedViewModel(ideas, swipe, projects)
        keepActive(viewModel.uiState)
        var startedId = 0L

        viewModel.useIdea(ideas.items.value.single { it.id == id }) { startedId = it }

        val project = projects.items.value.single()
        assertEquals(startedId, project.id)
        assertEquals("Cheap gear", project.title)
        assertEquals(VideoFormat.LONG_VIDEO, project.format)
        assertEquals("Cheap gear\nfrom https://youtu.be/x", project.brief)
        assertEquals(project.id, ideas.items.value.single().projectId)
    }

    @Test
    fun delete_removesIdeasAndSwipeItems() = runTest {
        ideas.save(Idea(title = "A"))
        swipe.save(SwipeItem(title = "S", url = "https://youtu.be/x"))
        val viewModel = SavedViewModel(ideas, swipe, projects)
        keepActive(viewModel.uiState)

        viewModel.deleteIdea(viewModel.uiState.value.ideas.single())
        viewModel.deleteSwipe(viewModel.uiState.value.swipe.single())

        assertTrue(viewModel.uiState.value.ideas.isEmpty())
        assertTrue(viewModel.uiState.value.swipe.isEmpty())
    }
}
