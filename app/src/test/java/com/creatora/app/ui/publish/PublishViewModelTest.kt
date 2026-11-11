package com.creatora.app.ui.publish

import androidx.lifecycle.SavedStateHandle
import com.creatora.app.domain.ai.AiTasks
import com.creatora.app.domain.model.AiError
import com.creatora.app.domain.model.AiOutcome
import com.creatora.app.domain.model.Platform
import com.creatora.app.domain.model.Project
import com.creatora.app.domain.model.ScheduledPost
import com.creatora.app.domain.publish.ChecklistItem
import com.creatora.app.testutil.FakeAiService
import com.creatora.app.testutil.FakeProjectRepository
import com.creatora.app.testutil.FakeReminderScheduler
import com.creatora.app.testutil.FakeScheduledPostRepository
import com.creatora.app.testutil.MainDispatcherRule
import com.creatora.app.testutil.keepActive
import com.creatora.app.testutil.okReply
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PublishViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val projects = FakeProjectRepository()
    private val posts = FakeScheduledPostRepository()
    private val reminders = FakeReminderScheduler()
    private val aiService = FakeAiService(okReply("TITLES:\n- Catchy title\nDESCRIPTION: A description\nHASHTAGS: #one #two\nCHAPTERS: 00:00 Intro"))

    private suspend fun project(p: Project = Project(title = "Video", hook = "A script.")) = projects.save(p)

    private fun TestScope.open(id: Long): PublishViewModel {
        val viewModel = PublishViewModel(SavedStateHandle(mapOf(PublishViewModel.ARG_PROJECT_ID to id)), projects, posts, reminders, AiTasks(aiService))
        keepActive(viewModel.uiState)
        return viewModel
    }

    @Test
    fun checklistFollowsTheAnswersAndFields() = runTest {
        val viewModel = open(project())
        assertTrue(viewModel.uiState.value.checklist.none { it.done })

        viewModel.setTitle("My title")
        viewModel.setAiContent(true)
        viewModel.setMadeForKids(false)

        val done = viewModel.uiState.value.checklist.filter { it.done }.map { it.item }
        assertEquals(listOf(ChecklistItem.TITLE, ChecklistItem.AI_LABEL, ChecklistItem.MADE_FOR_KIDS), done)
        assertEquals("My title", projects.items.value.single().publishTitle)
    }

    @Test
    fun suggest_waitsForThePickBeforeChangingAnything() = runTest {
        val id = project()
        val viewModel = open(id)

        viewModel.suggest()

        assertEquals(listOf("Catchy title"), viewModel.uiState.value.ai.suggestion?.titles)
        assertEquals("", projects.items.value.single().publishTitle)
        assertTrue(aiService.requests.single().messages.single().text.contains("A script."))
        viewModel.dismissSuggestion()
        assertNull(viewModel.uiState.value.ai.suggestion)
    }

    @Test
    fun suggest_reportsAiErrors_andNeedsNoScriptCheckHere() = runTest {
        aiService.outcome = AiOutcome.Failed(listOf(AiError.NoKey))
        val viewModel = open(project())

        viewModel.suggest()

        assertEquals(listOf(AiError.NoKey), viewModel.uiState.value.ai.errors)
        assertFalse(viewModel.uiState.value.ai.busy)
    }

    @Test
    fun schedule_savesThePostAndSetsAReminderWithTheTitle() = runTest {
        val id = project(Project(title = "Video", publishTitle = "Great title"))
        val viewModel = open(id)

        viewModel.schedule(Platform.INSTAGRAM, 5_000L)

        val post = posts.items.value.single()
        assertEquals(listOf(post), viewModel.uiState.value.posts)
        assertEquals(listOf(FakeReminderScheduler.Reminder(post.id, id, "Great title", "INSTAGRAM", 5_000L)), reminders.scheduled)
    }

    @Test
    fun theReminderFallsBackToTheProjectTitle() = runTest {
        val viewModel = open(project(Project(title = "Plain name")))
        viewModel.schedule(Platform.YOUTUBE, 1_000L)
        assertEquals("Plain name", reminders.scheduled.single().title)
    }

    @Test
    fun removePost_cancelsItsReminderAndDeletesIt_onlyThisProjectsPostsShow() = runTest {
        val id = project()
        val other = project()
        val viewModel = open(id)
        viewModel.schedule(Platform.YOUTUBE, 1_000L)
        posts.save(ScheduledPost(projectId = other, platform = Platform.YOUTUBE, scheduledAt = 2_000L))
        assertEquals(1, viewModel.uiState.value.posts.size)

        val post = viewModel.uiState.value.posts.single()
        viewModel.removePost(post)

        assertEquals(listOf(post.id), reminders.cancelled)
        assertTrue(viewModel.uiState.value.posts.isEmpty())
        assertEquals(1, posts.items.value.size) // the other project's post is untouched
    }
}
