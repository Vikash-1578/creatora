package com.creatora.app.ui.research

import com.creatora.app.domain.ai.AiTasks
import com.creatora.app.domain.home.CreatorIdea
import com.creatora.app.domain.home.IdeaStatus
import com.creatora.app.domain.model.Project
import com.creatora.app.domain.research.BankFilter
import com.creatora.app.domain.research.IdeaActions
import com.creatora.app.testutil.FakeCreatorIdeaRepository
import com.creatora.app.testutil.FakeProjectRepository
import com.creatora.app.testutil.HomeKit
import com.creatora.app.testutil.MainDispatcherRule
import com.creatora.app.testutil.keepActive
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class IdeaBankViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val kit = HomeKit()
    private val ideas = FakeCreatorIdeaRepository()
    private val projects = FakeProjectRepository()

    private fun TestScope.open(): IdeaBankViewModel {
        val vm = IdeaBankViewModel(kit.auth, ideas, projects, kit.context, IdeaActions(ideas, projects, AiTasks(kit.ai)))
        keepActive(vm.ui)
        return vm
    }

    private suspend fun fill() {
        kit.connect("UC1")
        kit.connect("UC2", "Second")
        val open = projects.save(Project(title = "Open project"))
        val done = projects.save(Project(title = "Done project", isReady = true))
        ideas.save("uid1", CreatorIdea("a", "Unstarted", status = IdeaStatus.SAVED, source = "research", accountKey = "YOUTUBE_UC1"))
        ideas.save("uid1", CreatorIdea("b", "Started", status = IdeaStatus.PLANNED, projectId = open, source = "trends", accountKey = "YOUTUBE_UC2"))
        ideas.save("uid1", CreatorIdea("c", "Finished", status = IdeaStatus.PLANNED, projectId = done, source = "competitor"))
        ideas.save("uid1", CreatorIdea("d", "Unsaved daily", status = IdeaStatus.NEW, source = "daily"))
        ideas.save("uid1", CreatorIdea("e", "Dismissed", status = IdeaStatus.DISMISSED))
    }

    @Test
    fun theBankHoldsSavedPlannedAndMadeIdeas_withTheirRealStatus() = runTest {
        fill()
        val vm = open()

        val ui = vm.ui.value

        assertTrue(ui.loaded)
        assertEquals(mapOf("a" to IdeaStatus.SAVED, "b" to IdeaStatus.PLANNED, "c" to IdeaStatus.MADE), ui.items.associate { it.idea.id to it.status })
        assertEquals("My channel", ui.items.first { it.idea.id == "a" }.account?.title) // the account the idea is for
    }

    @Test
    fun anIdeaWhoseProjectIsReady_isWrittenBackAsMade() = runTest {
        fill()
        open()

        assertEquals(IdeaStatus.MADE, ideas.ideas.value.single { it.id == "c" }.status)
        assertEquals(IdeaStatus.SAVED, ideas.ideas.value.single { it.id == "a" }.status) // others are untouched
    }

    @Test
    fun theFilters_showOneStatusOrOneAccount() = runTest {
        fill()
        val vm = open()

        vm.setFilter(BankFilter.NEW)
        assertEquals(listOf("a"), vm.ui.value.items.map { it.idea.id })
        vm.setFilter(BankFilter.PLANNED)
        assertEquals(listOf("b"), vm.ui.value.items.map { it.idea.id })
        vm.setFilter(BankFilter.MADE)
        assertEquals(listOf("c"), vm.ui.value.items.map { it.idea.id })

        vm.setFilter(BankFilter.ALL)
        vm.setAccount("YOUTUBE_UC2")
        assertEquals(listOf("b"), vm.ui.value.items.map { it.idea.id })
        vm.setAccount(null)
        assertEquals(3, vm.ui.value.items.size)
    }

    @Test
    fun markPlanned_andRemove() = runTest {
        fill()
        val vm = open()

        vm.markPlanned(ideas.ideas.value.single { it.id == "a" })
        assertEquals(IdeaStatus.PLANNED, vm.ui.value.items.single { it.idea.id == "a" }.status)

        vm.remove(ideas.ideas.value.single { it.id == "a" })
        assertFalse(vm.ui.value.items.any { it.idea.id == "a" })
        assertEquals(IdeaStatus.DISMISSED, ideas.ideas.value.single { it.id == "a" }.status)
    }

    @Test
    fun create_startsAProject_marksPlanned_andLinksIt() = runTest {
        fill()
        val vm = open()
        var started: com.creatora.app.domain.research.StartedProject? = null

        vm.create(ideas.ideas.value.single { it.id == "a" }) { started = it }

        val linked = ideas.ideas.value.single { it.id == "a" }
        assertEquals(IdeaStatus.PLANNED, linked.status)
        assertEquals(started!!.projectId, linked.projectId)
        assertEquals("Unstarted", projects.items.value.single { it.id == started!!.projectId }.title)
    }

    @Test
    fun anEmptyBank_isLoadedButEmpty() = runTest {
        val ui = open().ui.value

        assertTrue(ui.loaded && ui.items.isEmpty())
    }
}
