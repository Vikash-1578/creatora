package com.creatora.app.domain.research

import com.creatora.app.domain.ai.AiTasks
import com.creatora.app.domain.home.CreatorIdea
import com.creatora.app.domain.home.IdeaStatus
import com.creatora.app.domain.model.AiError
import com.creatora.app.domain.model.AiOutcome
import com.creatora.app.domain.model.VideoFormat
import com.creatora.app.testutil.FakeAiService
import com.creatora.app.testutil.FakeCreatorIdeaRepository
import com.creatora.app.testutil.FakeProjectRepository
import com.creatora.app.testutil.okReply
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdeaActionsTest {
    private val ideas = FakeCreatorIdeaRepository()
    private val projects = FakeProjectRepository()
    private val ai = FakeAiService(okReply("HOOK: Start here\nSTRUCTURE: Do the steps\nCTA: Subscribe"))
    private val actions = IdeaActions(ideas, projects, AiTasks(ai))

    @Test
    fun save_putsTheIdeaInTheBankAsSaved() = runTest {
        val id = actions.save("uid1", CreatorIdea(title = "Dal in 10 minutes", source = "research", status = IdeaStatus.NEW))

        assertEquals(IdeaStatus.SAVED, ideas.ideas.value.single { it.id == id }.status)
    }

    @Test
    fun start_makesAProject_marksTheIdeaPlanned_andLinksThem() = runTest {
        val id = actions.save("uid1", CreatorIdea(title = "Dal in 10 minutes", reason = "Your best video", source = "trends"))

        val started = actions.start("uid1", ideas.ideas.value.single { it.id == id })

        val project = projects.items.value.single()
        assertEquals(started.projectId, project.id)
        assertEquals("Dal in 10 minutes", project.title)
        assertEquals("Dal in 10 minutes\nYour best video", project.brief)
        assertEquals(VideoFormat.LONG_VIDEO, project.format)
        assertEquals("", project.hook)
        assertFalse(started.draftFailed)
        assertTrue(ai.requests.isEmpty()) // no AI action unless asked
        val linked = ideas.ideas.value.single()
        assertEquals(IdeaStatus.PLANNED, linked.status)
        assertEquals(project.id, linked.projectId)
    }

    @Test
    fun startingFromAConceptThatWasNeverSaved_savesItPlannedAndLinked() = runTest {
        val started = actions.start("uid1", CreatorIdea(title = "A fast weeknight dish", source = "competitor"))

        val idea = ideas.ideas.value.single()
        assertEquals(IdeaStatus.PLANNED, idea.status)
        assertEquals(started.projectId, idea.projectId)
        assertEquals("competitor", idea.source)
    }

    @Test
    fun letAiMakeIt_writesTheScriptDraftIntoTheProject() = runTest {
        actions.start("uid1", CreatorIdea(title = "A fast weeknight dish", reason = "Time limit in the title"), draftWithAi = true)

        val project = projects.items.value.single()
        assertEquals("Start here", project.hook)
        assertEquals("Do the steps", project.structure)
        assertEquals("Subscribe", project.callToAction)
        assertTrue("A fast weeknight dish" in ai.requests.single().messages.single().text)
    }

    @Test
    fun ifTheDraftFails_theProjectStillOpens_andSaysSo() = runTest {
        ai.outcome = AiOutcome.Failed(listOf(AiError.LimitReached))

        val started = actions.start("uid1", CreatorIdea(title = "A fast weeknight dish"), draftWithAi = true)

        assertTrue(started.draftFailed)
        assertEquals("", projects.items.value.single().hook)
        assertEquals(IdeaStatus.PLANNED, ideas.ideas.value.single().status)
    }
}
