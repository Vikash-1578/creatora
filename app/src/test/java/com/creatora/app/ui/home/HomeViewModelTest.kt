package com.creatora.app.ui.home

import com.creatora.app.domain.model.Project
import com.creatora.app.domain.model.VideoFormat
import com.creatora.app.testutil.FakeProjectRepository
import com.creatora.app.testutil.MainDispatcherRule
import com.creatora.app.testutil.keepActive
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val projects = FakeProjectRepository()

    @Test
    fun continueProject_isTheLatestUnfinishedCreateProject() = runTest {
        val viewModel = HomeViewModel(projects)
        keepActive(viewModel.continueProject)
        assertNull(viewModel.continueProject.value)

        projects.save(Project(title = "Old", format = VideoFormat.LONG_VIDEO, updatedAt = 10))
        projects.save(Project(title = "Latest", format = VideoFormat.VIDEO_AD, updatedAt = 30))
        projects.save(Project(title = "Ready", format = VideoFormat.LONG_VIDEO, updatedAt = 50, isReady = true))
        projects.save(Project(title = "Template", format = VideoFormat.LONG_VIDEO, updatedAt = 60, isTemplate = true))
        projects.save(Project(title = "Library only", updatedAt = 70))

        assertEquals("Latest", viewModel.continueProject.value?.title)
    }

    @Test
    fun recentProjects_areTheLatestFive_withoutTemplates() = runTest {
        val viewModel = HomeViewModel(projects)
        keepActive(viewModel.recentProjects)
        assertEquals(emptyList<Project>(), viewModel.recentProjects.value)

        (1..7).forEach { projects.save(Project(title = "P$it", updatedAt = it.toLong())) }
        projects.save(Project(title = "Template", updatedAt = 99, isTemplate = true))

        assertEquals(listOf("P7", "P6", "P5", "P4", "P3"), viewModel.recentProjects.value.map { it.title })
    }
}
