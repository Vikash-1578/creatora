package com.creatora.app.ui.calendar

import com.creatora.app.domain.model.Platform
import com.creatora.app.domain.model.Project
import com.creatora.app.domain.model.ScheduledPost
import com.creatora.app.testutil.FakeProjectRepository
import com.creatora.app.testutil.FakeReminderScheduler
import com.creatora.app.testutil.FakeScheduledPostRepository
import com.creatora.app.testutil.MainDispatcherRule
import com.creatora.app.testutil.keepActive
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CalendarViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val projects = FakeProjectRepository()
    private val posts = FakeScheduledPostRepository()
    private val reminders = FakeReminderScheduler()

    @Test
    fun groupsPostsIntoUpcomingSoonestFirstAndPastNewestFirst() = runTest {
        val now = System.currentTimeMillis()
        val id = projects.save(Project(title = "Video", publishTitle = "Public title", renderUri = "file:///r.mp4"))
        posts.save(ScheduledPost(projectId = id, platform = Platform.YOUTUBE, scheduledAt = now + 2_000_000))
        posts.save(ScheduledPost(projectId = id, platform = Platform.INSTAGRAM, scheduledAt = now + 1_000_000))
        posts.save(ScheduledPost(projectId = id, platform = Platform.YOUTUBE, scheduledAt = now - 5_000_000))
        posts.save(ScheduledPost(projectId = id, platform = Platform.YOUTUBE, scheduledAt = now - 1_000_000))
        val viewModel = CalendarViewModel(projects, posts, reminders)
        keepActive(viewModel.uiState)

        val state = viewModel.uiState.value

        assertEquals(listOf(Platform.INSTAGRAM, Platform.YOUTUBE), state.upcoming.map { it.post.platform })
        assertEquals(listOf(now - 1_000_000, now - 5_000_000), state.past.map { it.post.scheduledAt })
        assertEquals("Public title", state.upcoming.first().projectTitle)
    }

    @Test
    fun onlyRenderedNonTemplateProjectsCanBeScheduled_andADeletedProjectShowsNoTitle() = runTest {
        val rendered = projects.save(Project(title = "Rendered", renderUri = "file:///r.mp4"))
        projects.save(Project(title = "Draft"))
        projects.save(Project(title = "Template", renderUri = "file:///t.mp4", isTemplate = true))
        posts.save(ScheduledPost(projectId = 999, platform = Platform.YOUTUBE, scheduledAt = System.currentTimeMillis() + 10_000))
        val viewModel = CalendarViewModel(projects, posts, reminders)
        keepActive(viewModel.uiState)

        assertEquals(listOf(rendered), viewModel.uiState.value.schedulable.map { it.id })
        assertNull(viewModel.uiState.value.upcoming.single().projectTitle)
    }

    @Test
    fun scheduleAndRemove_keepRemindersInStep() = runTest {
        val id = projects.save(Project(title = "Video", renderUri = "file:///r.mp4"))
        val viewModel = CalendarViewModel(projects, posts, reminders)
        keepActive(viewModel.uiState)

        viewModel.schedule(id, Platform.YOUTUBE, System.currentTimeMillis() + 60_000)
        viewModel.schedule(999, Platform.YOUTUBE, 1L) // unknown project: ignored

        val post = posts.items.value.single()
        assertEquals(post.id, reminders.scheduled.single().postId)
        viewModel.remove(post)
        assertEquals(listOf(post.id), reminders.cancelled)
        assertTrue(posts.items.value.isEmpty())
    }
}
