package com.creatora.app.ui.home

import com.creatora.app.domain.account.AppLanguage
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.home.AccountSelection
import com.creatora.app.domain.model.Project
import com.creatora.app.domain.model.VideoFormat
import com.creatora.app.domain.repository.UpcomingPost
import com.creatora.app.testutil.FakeProjectRepository
import com.creatora.app.testutil.FakeSettingsRepository
import com.creatora.app.testutil.FakeUpcomingPostRepository
import com.creatora.app.testutil.HomeKit
import com.creatora.app.testutil.MainDispatcherRule
import com.creatora.app.testutil.keepActive
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val kit = HomeKit()
    private val projects = FakeProjectRepository()
    private val upcoming = FakeUpcomingPostRepository()
    private val settings = FakeSettingsRepository()

    private fun TestScope.open(): HomeViewModel {
        val vm = HomeViewModel(kit.context, projects, upcoming, settings)
        keepActive(vm.state)
        return vm
    }

    @Test
    fun theHeaderHasTheFirstName_thePhoto_andTheChosenLanguage() = runTest {
        settings.language.value = AppLanguage.HINDI
        val vm = open()

        assertEquals("Asha", vm.state.value.firstName)
        assertEquals(AppLanguage.HINDI, vm.state.value.language)
        assertFalse(vm.state.value.hasAccounts)
    }

    @Test
    fun withoutASavedChoice_theFirstAccountIsSelected_andTheChoiceIsRemembered() = runTest {
        kit.connect("UC1", "First")
        kit.connect("UC2", "Second")
        val vm = open()
        assertEquals(AccountSelection.One("YOUTUBE_UC1"), vm.state.value.selection)
        assertEquals(listOf("First", "Second"), vm.state.value.accounts.map { it.title })

        vm.select(AccountSelection.All)
        assertEquals(AccountSelection.All, vm.state.value.selection)
        assertEquals("all", kit.prefs.selectedKey.value)

        vm.select(AccountSelection.One("YOUTUBE_UC2"))
        assertEquals(AccountSelection.One("YOUTUBE_UC2"), vm.state.value.selection)
    }

    @Test
    fun continueProject_isTheLatestUnfinishedCreateProject() = runTest {
        val vm = open()
        assertNull(vm.state.value.continueProject)

        projects.save(Project(title = "Old", format = VideoFormat.LONG_VIDEO, updatedAt = 10))
        projects.save(Project(title = "Latest", format = VideoFormat.VIDEO_AD, updatedAt = 30))
        projects.save(Project(title = "Ready", format = VideoFormat.LONG_VIDEO, updatedAt = 50, isReady = true))
        projects.save(Project(title = "Template", format = VideoFormat.LONG_VIDEO, updatedAt = 60, isTemplate = true))
        projects.save(Project(title = "Library only", updatedAt = 70))

        assertEquals("Latest", vm.state.value.continueProject?.title)
    }

    @Test
    fun upcoming_showsFuturePostsOfTheSelectedAccounts_soonestFirst_atMostFive() = runTest {
        kit.connect("UC1", "First")
        kit.connect("UC2", "Second")
        val now = System.currentTimeMillis()
        val hour = 3_600_000L
        fun post(id: String, key: String?, at: Long) = UpcomingPost(id, key, ConnectPlatform.YOUTUBE, "Post $id", at)
        upcoming.posts.value = listOf(
            post("late", "YOUTUBE_UC1", now + 9 * hour), post("soon", "YOUTUBE_UC1", now + hour),
            post("other", "YOUTUBE_UC2", now + 2 * hour), post("past", "YOUTUBE_UC1", now - hour),
            post("everyone", null, now + 3 * hour),
        ) + (1..6).map { post("x$it", "YOUTUBE_UC1", now + (10 + it) * hour) }
        val vm = open()

        assertEquals(listOf("soon", "everyone", "late", "x1", "x2"), vm.state.value.upcoming.map { it.id })

        vm.select(AccountSelection.One("YOUTUBE_UC2"))
        assertEquals(listOf("other", "everyone"), vm.state.value.upcoming.map { it.id })

        vm.select(AccountSelection.All)
        assertTrue("other" in vm.state.value.upcoming.map { it.id } && "soon" in vm.state.value.upcoming.map { it.id })
    }
}
