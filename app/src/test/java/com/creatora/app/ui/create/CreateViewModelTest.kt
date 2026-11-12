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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CreateViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val projects = FakeProjectRepository()
    private val aiService = FakeAiService(okReply("- Idea one\n- Idea two"))
    private val kit = com.creatora.app.testutil.PlanKit()
    private val ideaRepo = com.creatora.app.testutil.FakeCreatorIdeaRepository()
    private val links = com.creatora.app.testutil.FakeLinkReader()

    private fun newViewModel() = CreateViewModel(
        projects, AiTasks(aiService), kit.auth, kit.plans, ideaRepo, links,
        com.creatora.app.domain.create.ConceptExtractor(AiTasks(aiService)),
    )

    @Test
    fun start_createsAProjectInTheChosenFormatAndReturnsItsId() {
        var createdId = 0L

        newViewModel().start(VideoFormat.CARTOON_STORY, "  Untitled Cartoon story ", onCreated = { createdId = it })

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
        newViewModel().start(VideoFormat.SHORTS_REELS, "Idea", onCreated = {}, brief = " Idea ")

        assertEquals("Idea", projects.items.value.single().brief)
    }

    @Test
    fun start_reusesAnEmptyDraftOfTheSameFormat_insteadOfCreatingAnotherOne() {
        val viewModel = newViewModel()
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
        val viewModel = newViewModel()
        viewModel.start(VideoFormat.LONG_VIDEO, "One", onCreated = {})
        projects.items.value = projects.items.value.map { it.copy(brief = "Some brief") }

        viewModel.start(VideoFormat.LONG_VIDEO, "Two", onCreated = {})
        viewModel.start(VideoFormat.SHORTS_REELS, "Three", onCreated = {})
        viewModel.start(VideoFormat.SHORTS_REELS, "Four", onCreated = {}, brief = "From an idea")

        assertEquals(listOf("One", "Two", "Three", "Four"), projects.items.value.sortedBy { it.id }.map { it.title })
    }

    @Test
    fun suggestIdeas_showsTheIdeasOrTheErrors() {
        val viewModel = newViewModel()

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

// ---------------------------------------------------------------- Phase D: plans, start methods, modes

class CreateViewModelStartTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val projects = FakeProjectRepository()
    private val ideas = com.creatora.app.testutil.FakeCreatorIdeaRepository()
    private val links = com.creatora.app.testutil.FakeLinkReader()
    private val ai = FakeAiService(okReply("CONCEPT: Lentils for busy evenings\nANGLE: Beginners\nHOOK: Can you cook in ten minutes?"))

    private fun open(plan: com.creatora.app.domain.account.Plan = com.creatora.app.domain.account.Plan.PRO): Pair<CreateViewModel, com.creatora.app.testutil.PlanKit> {
        val kit = com.creatora.app.testutil.PlanKit(plan)
        return CreateViewModel(projects, AiTasks(ai), kit.auth, kit.plans, ideas, links, com.creatora.app.domain.create.ConceptExtractor(AiTasks(ai))) to kit
    }

    private val Pair<CreateViewModel, *>.vm get() = first

    @Test
    fun thePlanIsLoaded_soTheTilesCanLock() {
        assertEquals(com.creatora.app.domain.account.Plan.FREE, open(com.creatora.app.domain.account.Plan.FREE).vm.plan.value)
        assertEquals(com.creatora.app.domain.account.Plan.PRO, open().vm.plan.value)
    }

    @Test
    fun aFreeUserCannotStartALockedFormat_butCanStartShorts() {
        val vm = open(com.creatora.app.domain.account.Plan.FREE).vm

        vm.startFrom(VideoFormat.LONG_VIDEO, com.creatora.app.domain.model.StartSource.TOPIC, "Dal", null, com.creatora.app.domain.model.CreateMode.STEP_BY_STEP)
        assertTrue(projects.items.value.isEmpty())

        vm.startFrom(VideoFormat.AVATAR_VIDEO, com.creatora.app.domain.model.StartSource.TOPIC, "Dal", null, com.creatora.app.domain.model.CreateMode.STEP_BY_STEP)
        assertTrue(projects.items.value.isEmpty())

        vm.startFrom(VideoFormat.SHORTS_REELS, com.creatora.app.domain.model.StartSource.TOPIC, "Dal in ten minutes", null, com.creatora.app.domain.model.CreateMode.AUTO)
        val project = projects.items.value.single()
        assertEquals("Dal in ten minutes", project.title)
        assertEquals(com.creatora.app.domain.model.CreateMode.AUTO, project.mode)
        assertEquals(com.creatora.app.domain.model.StartSource.TOPIC, project.startedFrom)
        assertEquals(StartResult.Started(project.id), vm.startResult.value)
    }

    @Test
    fun startingFromAnIdea_linksTheIdeaAndMarksItPlanned() = kotlinx.coroutines.test.runTest {
        val (vm, _) = open()
        val id = ideas.save("uid1", com.creatora.app.domain.home.CreatorIdea(title = "Dal in 10 minutes", reason = "Your best video", status = com.creatora.app.domain.home.IdeaStatus.SAVED))
        val idea = ideas.ideas.value.single { it.id == id }

        vm.startFrom(VideoFormat.SHORTS_REELS, com.creatora.app.domain.model.StartSource.IDEA, "", idea, com.creatora.app.domain.model.CreateMode.STEP_BY_STEP)

        val project = projects.items.value.single()
        assertEquals("Dal in 10 minutes\nYour best video", project.brief)
        assertEquals(com.creatora.app.domain.model.StartSource.IDEA, project.startedFrom)
        assertEquals(com.creatora.app.domain.home.IdeaStatus.PLANNED, ideas.ideas.value.single().status)
        assertEquals(project.id, ideas.ideas.value.single().projectId)
    }

    @Test
    fun savedIdeas_listOnlyTheSavedOnes() = kotlinx.coroutines.test.runTest {
        val (vm, _) = open()
        ideas.save("uid1", com.creatora.app.domain.home.CreatorIdea(title = "Saved", status = com.creatora.app.domain.home.IdeaStatus.SAVED))
        ideas.save("uid1", com.creatora.app.domain.home.CreatorIdea(title = "Daily", status = com.creatora.app.domain.home.IdeaStatus.NEW))
        com.creatora.app.testutil.keepActive(vm.savedIdeas)

        assertEquals(listOf("Saved"), vm.savedIdeas.value.map { it.title })
    }

    @Test
    fun aLink_becomesAConceptInNewWords_andOnlyTheLinkIsKept() {
        links.result = com.creatora.app.domain.create.LinkResult.Ok(com.creatora.app.domain.create.SourceInfo("https://youtu.be/x", "Best dal recipe ever", "Secret description text", listOf("dal")))
        val vm = open().vm

        vm.startFrom(VideoFormat.SHORTS_REELS, com.creatora.app.domain.model.StartSource.LINK, "https://youtu.be/x", null, com.creatora.app.domain.model.CreateMode.STEP_BY_STEP)

        val project = projects.items.value.single()
        assertEquals("Lentils for busy evenings", project.title)
        assertEquals("Lentils for busy evenings", project.concept)
        assertEquals("Beginners", project.angle)
        assertEquals("https://youtu.be/x", project.sourceLink)
        assertEquals(com.creatora.app.domain.model.StartSource.LINK, project.startedFrom)
        assertTrue("Best dal recipe ever" !in project.brief && "Secret description text" !in project.brief) // the source's own words are not kept
    }

    @Test
    fun aBadOrUnreadableLink_saysWhy_andCreatesNothing() {
        val vm = open().vm

        links.result = com.creatora.app.domain.create.LinkResult.Invalid
        vm.startFrom(VideoFormat.SHORTS_REELS, com.creatora.app.domain.model.StartSource.LINK, "nope", null, com.creatora.app.domain.model.CreateMode.STEP_BY_STEP)
        assertEquals(StartResult.LinkInvalid, vm.startResult.value)

        links.result = com.creatora.app.domain.create.LinkResult.Unreadable
        vm.startFrom(VideoFormat.SHORTS_REELS, com.creatora.app.domain.model.StartSource.LINK, "https://x.com", null, com.creatora.app.domain.model.CreateMode.STEP_BY_STEP)
        assertEquals(StartResult.LinkUnreadable, vm.startResult.value)
        assertTrue(projects.items.value.isEmpty())
    }

    @Test
    fun aCopyOfTheSource_isRefused() {
        ai.outcome = okReply("CONCEPT: Best dal recipe ever\nANGLE: a\nHOOK: b")
        links.result = com.creatora.app.domain.create.LinkResult.Ok(com.creatora.app.domain.create.SourceInfo("https://x.com", "Best dal recipe ever", "", emptyList()))
        val vm = open().vm

        vm.startFrom(VideoFormat.SHORTS_REELS, com.creatora.app.domain.model.StartSource.LINK, "https://x.com", null, com.creatora.app.domain.model.CreateMode.STEP_BY_STEP)

        assertEquals(StartResult.TooClose, vm.startResult.value)
        assertTrue(projects.items.value.isEmpty())
    }

    @Test
    fun anEmptyTopic_startsNothing() {
        val vm = open().vm

        vm.startFrom(VideoFormat.SHORTS_REELS, com.creatora.app.domain.model.StartSource.TOPIC, "   ", null, com.creatora.app.domain.model.CreateMode.STEP_BY_STEP)

        assertTrue(projects.items.value.isEmpty())
        assertEquals(StartResult.Idle, vm.startResult.value)
    }
}
