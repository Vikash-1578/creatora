package com.creatora.app.ui.home

import com.creatora.app.domain.grow.MyChannel
import com.creatora.app.domain.model.ChannelSnapshot
import com.creatora.app.domain.model.Idea
import com.creatora.app.domain.model.Platform
import com.creatora.app.domain.model.Project
import com.creatora.app.domain.model.ScheduledPost
import com.creatora.app.domain.model.VideoFormat
import com.creatora.app.testutil.FakeIdeaRepository
import com.creatora.app.testutil.FakeMyChannelRepository
import com.creatora.app.testutil.FakeProjectRepository
import com.creatora.app.testutil.FakeScheduledPostRepository
import com.creatora.app.testutil.FakeSnapshotRepository
import com.creatora.app.testutil.MainDispatcherRule
import com.creatora.app.testutil.keepActive
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val projects = FakeProjectRepository()
    private val ideas = FakeIdeaRepository()
    private val posts = FakeScheduledPostRepository()
    private val channel = FakeMyChannelRepository()
    private val snapshots = FakeSnapshotRepository()

    private fun TestScope.open(): HomeViewModel {
        val viewModel = HomeViewModel(projects, ideas, posts, channel, snapshots)
        keepActive(viewModel.uiState)
        return viewModel
    }

    @Test
    fun continueProject_isTheLatestUnfinishedCreateProject() = runTest {
        val viewModel = open()
        assertNull(viewModel.uiState.value.continueProject)

        projects.save(Project(title = "Old", format = VideoFormat.LONG_VIDEO, updatedAt = 10))
        projects.save(Project(title = "Latest", format = VideoFormat.VIDEO_AD, updatedAt = 30))
        projects.save(Project(title = "Ready", format = VideoFormat.LONG_VIDEO, updatedAt = 50, isReady = true))
        projects.save(Project(title = "Template", format = VideoFormat.LONG_VIDEO, updatedAt = 60, isTemplate = true))
        projects.save(Project(title = "Library only", updatedAt = 70))

        assertEquals("Latest", viewModel.uiState.value.continueProject?.title)
    }

    @Test
    fun ideas_areTheNewestTwoThatAreNotProjectsYet() = runTest {
        val viewModel = open()
        ideas.save(Idea(title = "Oldest", createdAt = 1))
        ideas.save(Idea(title = "Used", createdAt = 2, projectId = 9))
        ideas.save(Idea(title = "Middle", createdAt = 3))
        ideas.save(Idea(title = "Newest", createdAt = 4))

        assertEquals(listOf("Newest", "Middle"), viewModel.uiState.value.ideas.map { it.title })
    }

    @Test
    fun nextPost_isTheSoonestFutureOne_withItsProjectTitle() = runTest {
        val viewModel = open()
        val now = System.currentTimeMillis()
        val first = projects.save(Project(title = "Draft title", publishTitle = "Final title"))
        val second = projects.save(Project(title = "Second"))
        posts.save(ScheduledPost(projectId = second, platform = Platform.INSTAGRAM, scheduledAt = now + 7_200_000))
        posts.save(ScheduledPost(projectId = first, platform = Platform.YOUTUBE, scheduledAt = now + 3_600_000))
        posts.save(ScheduledPost(projectId = second, platform = Platform.YOUTUBE, scheduledAt = now - 3_600_000))

        val next = viewModel.uiState.value.nextPost!!
        assertEquals(first, next.post.projectId)
        assertEquals("Final title", next.title)
    }

    @Test
    fun nextPost_isNullWithoutUpcomingPosts() = runTest {
        val viewModel = open()
        posts.save(ScheduledPost(projectId = 1, platform = Platform.YOUTUBE, scheduledAt = 1))
        assertNull(viewModel.uiState.value.nextPost)
    }

    @Test
    fun channel_showsChangesOverSevenDays_orNothingWhenNotConnected() = runTest {
        val viewModel = open()
        assertNull(viewModel.uiState.value.channel)

        val day = 24L * 60 * 60 * 1000
        snapshots.save(ChannelSnapshot(channelId = "c1", subscriberCount = 100, viewCount = 1_000, videoCount = 5, capturedAt = 0))
        snapshots.save(ChannelSnapshot(channelId = "c1", subscriberCount = 130, viewCount = 1_800, videoCount = 6, capturedAt = 8 * day))
        snapshots.save(ChannelSnapshot(channelId = "other", subscriberCount = 1, viewCount = 1, videoCount = 1, capturedAt = 9 * day))
        channel.set(MyChannel("c1", "My channel"))

        val stats = viewModel.uiState.value.channel!!
        assertEquals("My channel", stats.channel.title)
        assertEquals(30L, stats.subscribers?.change)
        assertEquals(800L, stats.views?.change)
        assertEquals(1L, stats.videos?.change)

        channel.clear()
        assertNull(viewModel.uiState.value.channel)
    }

    @Test
    fun startProject_createsAProjectInTheChosenFormat() = runTest {
        val viewModel = open()
        var started = 0L

        viewModel.startProject("   ", VideoFormat.LONG_VIDEO) { started = it }
        assertTrue(projects.items.value.isEmpty())

        viewModel.startProject("  Studio tour ", VideoFormat.SHORTS_REELS) { started = it }

        val project = projects.items.value.single()
        assertEquals(started, project.id)
        assertEquals(listOf("Studio tour", "Studio tour"), listOf(project.title, project.brief))
        assertEquals(VideoFormat.SHORTS_REELS, project.format)
    }

    @Test
    fun useIdea_startsAProjectAndLinksTheIdea() = runTest {
        val viewModel = open()
        val id = ideas.save(Idea(title = "Studio tour", notes = "Show the desk"))
        var started = 0L

        viewModel.useIdea(ideas.items.value.single()) { started = it }

        val project = projects.items.value.single()
        assertEquals(started, project.id)
        assertEquals("Studio tour\nShow the desk", project.brief)
        assertEquals(project.id, ideas.items.value.single { it.id == id }.projectId)
        assertTrue(viewModel.uiState.value.ideas.isEmpty())
    }
}
