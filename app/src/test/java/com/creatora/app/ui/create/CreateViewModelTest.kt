package com.creatora.app.ui.create

import com.creatora.app.domain.ai.AiTasks
import com.creatora.app.domain.model.AiError
import com.creatora.app.domain.model.AiOutcome
import com.creatora.app.domain.model.VideoFormat
import com.creatora.app.testutil.FakeAiService
import com.creatora.app.testutil.okReply
import com.creatora.app.testutil.FakeProjectRepository
import com.creatora.app.testutil.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class CreateViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val projects = FakeProjectRepository()
    private val aiService = FakeAiService(okReply("- Idea one\n- Idea two"))

    @Test
    fun start_createsAProjectInTheChosenFormatAndReturnsItsId() {
        var createdId = 0L

        CreateViewModel(projects, AiTasks(aiService)).start(VideoFormat.CARTOON_STORY, "  Untitled Cartoon story ", onCreated = { createdId = it })

        val project = projects.items.value.single()
        assertEquals(createdId, project.id)
        assertEquals("Untitled Cartoon story", project.title)
        assertEquals(VideoFormat.CARTOON_STORY, project.format)
        assertEquals(0, project.step)
        assertFalse(project.isReady)
        assertFalse(project.isTemplate)
    }

    @Test
    fun start_canBeginFromAnIdea() {
        CreateViewModel(projects, AiTasks(aiService)).start(VideoFormat.SHORTS_REELS, "Idea", onCreated = {}, brief = " Idea ")

        assertEquals("Idea", projects.items.value.single().brief)
    }

    @Test
    fun start_reusesAnEmptyDraftOfTheSameFormat_insteadOfCreatingAnotherOne() {
        val viewModel = CreateViewModel(projects, AiTasks(aiService))
        val ids = mutableListOf<Long>()

        viewModel.start(VideoFormat.LONG_VIDEO, "Untitled Long video", onCreated = { ids += it })
        // The user renames it in the wizard, backs out and taps Start again.
        projects.items.value = projects.items.value.map { it.copy(title = "hello World") }
        viewModel.start(VideoFormat.LONG_VIDEO, "Untitled Long video", onCreated = { ids += it })

        assertEquals(1, projects.items.value.size)
        assertEquals(listOf(ids[0], ids[0]), ids)
        assertEquals("hello World", projects.items.value.single().title)
    }

    @Test
    fun start_createsANewProjectWhenTheDraftHasContentOrAnotherFormatIsChosen() {
        val viewModel = CreateViewModel(projects, AiTasks(aiService))
        viewModel.start(VideoFormat.LONG_VIDEO, "One", onCreated = {})
        projects.items.value = projects.items.value.map { it.copy(brief = "Some brief") }

        viewModel.start(VideoFormat.LONG_VIDEO, "Two", onCreated = {})
        viewModel.start(VideoFormat.SHORTS_REELS, "Three", onCreated = {})
        viewModel.start(VideoFormat.SHORTS_REELS, "Four", onCreated = {}, brief = "From an idea")

        assertEquals(listOf("One", "Two", "Three", "Four"), projects.items.value.sortedBy { it.id }.map { it.title })
    }

    @Test
    fun suggestIdeas_showsTheIdeasOrTheErrors() {
        val viewModel = CreateViewModel(projects, AiTasks(aiService))

        viewModel.suggestIdeas("cats")
        assertEquals(listOf("Idea one", "Idea two"), viewModel.ideas.value.ideas)
        assertFalse(viewModel.ideas.value.loading)

        aiService.outcome = AiOutcome.Failed(listOf(AiError.NoKey))
        viewModel.suggestIdeas("cats")
        assertEquals(listOf(AiError.NoKey), viewModel.ideas.value.errors)
        assertEquals(emptyList<String>(), viewModel.ideas.value.ideas)

        viewModel.clearIdeas()
        assertEquals(IdeasUiState(), viewModel.ideas.value)
    }
}
