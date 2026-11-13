package com.creatora.app.ui.create

import androidx.lifecycle.SavedStateHandle
import com.creatora.app.domain.account.Plan
import com.creatora.app.domain.ai.AiTasks
import com.creatora.app.domain.create.AiImages
import com.creatora.app.domain.create.ImageOutcome
import com.creatora.app.domain.model.AiError
import com.creatora.app.domain.model.AiOutcome
import com.creatora.app.domain.model.CreateMode
import com.creatora.app.domain.model.Project
import com.creatora.app.domain.model.StepState
import com.creatora.app.domain.model.VideoFormat
import com.creatora.app.domain.model.WizardStep
import com.creatora.app.testutil.FakeAiService
import com.creatora.app.testutil.FakeAssetRepository
import com.creatora.app.testutil.FakeCharacterRepository
import com.creatora.app.testutil.FakeLicenceRepository
import com.creatora.app.testutil.FakeProjectRepository
import com.creatora.app.testutil.FakeSpeechPlayer
import com.creatora.app.testutil.FakeVoicePresetRepository
import com.creatora.app.testutil.MainDispatcherRule
import com.creatora.app.testutil.PlanKit
import com.creatora.app.testutil.keepActive
import com.creatora.app.testutil.okReply
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Phase D: plan limits on the steps, the AI actions, step states and Auto mode. */
class WizardPhaseDTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val projects = FakeProjectRepository()
    private val assets = FakeAssetRepository()
    private val licences = FakeLicenceRepository()
    private val ai = FakeAiService(okReply("ANGLE: A calm start\nLENGTH: 25\nTONE: warm\nAUDIENCE: busy cooks\nHOOK: Can you cook dal in 10 minutes?\nSTRUCTURE: Boil the lentils. Temper the spices.\nCTA: Follow for more."))

    private suspend fun TestScope.open(project: Project, plan: Plan = Plan.FREE): Pair<WizardViewModel, PlanKit> {
        val kit = PlanKit(plan)
        val saved = projects.save(project)
        val vm = WizardViewModel(
            SavedStateHandle(mapOf(WizardViewModel.ARG_PROJECT_ID to saved)),
            projects, FakeCharacterRepository(), FakeVoicePresetRepository(), assets, licences, FakeSpeechPlayer(), AiTasks(ai),
            kit.auth, kit.plans, kit.daily, kit.usage, AiImages(kit.generator, assets, licences, kit.usage, kit.daily, kit.plans, kit.auth),
        )
        keepActive(vm.uiState)
        return vm to kit
    }

    private val shorts = Project(title = "Dal in ten minutes", format = VideoFormat.SHORTS_REELS, brief = "Quick dal")

    @Test
    fun theStateCarriesThePlanAndTheLimitsTheStepsShow() = runTest {
        val (vm, _) = open(shorts)

        val state = vm.uiState.value
        assertEquals(Plan.FREE, state.plan)
        assertEquals(30, state.plan.limits.maxVideoSeconds)
        assertEquals(0, state.usage.aiImages)
        assertEquals(50, state.creditsLeft)
    }

    @Test
    fun letAiSetTheBrief_fillsTheFields_capsTheLength_andMarksTheStepAsAnAiDraft() = runTest {
        ai.outcome = okReply("ANGLE: A calm start\nLENGTH: 90\nTONE: warm\nAUDIENCE: busy cooks")
        val (vm, _) = open(shorts)

        vm.suggestBrief()

        val project = vm.uiState.value.project!!
        assertEquals("A calm start", project.angle)
        assertEquals("warm", project.tone)
        assertEquals("busy cooks", project.audience)
        assertEquals(30, project.targetSeconds) // the Free limit
        assertEquals(StepState.AI_DRAFT, project.stepStates[WizardStep.BRIEF])
        assertTrue("at most 30" in ai.requests.single().system)
    }

    @Test
    fun anUnreadableBrief_isReported_notApplied() = runTest {
        ai.outcome = okReply("no markers here")
        val (vm, _) = open(shorts)

        vm.suggestBrief()

        assertEquals(emptyList<AiError>(), vm.uiState.value.ai.errors)
        assertEquals("", vm.uiState.value.project!!.angle)
    }

    @Test
    fun editingByHand_marksTheStepEdited_andLaterDoneStepsOutOfDate() = runTest {
        val (vm, _) = open(shorts.copy(stepStates = mapOf(WizardStep.SCRIPT to StepState.APPROVED, WizardStep.VOICE to StepState.APPROVED)))

        vm.setAngle("A different angle")

        val states = vm.uiState.value.project!!.stepStates
        assertEquals(StepState.EDITED, states[WizardStep.BRIEF])
        assertEquals(StepState.OUT_OF_DATE, states[WizardStep.SCRIPT])
        assertEquals(StepState.OUT_OF_DATE, states[WizardStep.VOICE])
        assertEquals("A different angle", vm.uiState.value.project!!.angle)
    }

    @Test
    fun movingOn_approvesTheStepYouLeft() = runTest {
        val (vm, _) = open(shorts)
        vm.suggestBrief()

        vm.goToStep(1)

        assertEquals(StepState.APPROVED, vm.uiState.value.project!!.stepStates[WizardStep.BRIEF])
        assertEquals(WizardStep.SCRIPT, vm.uiState.value.step)
        vm.goToStep(0)
        assertEquals(StepState.APPROVED, vm.uiState.value.project!!.stepStates[WizardStep.BRIEF]) // going back changes nothing
    }

    @Test
    fun rewritingOneLine_changesOnlyThatLine() = runTest {
        ai.outcome = okReply("\"Fresh body line.\"")
        val (vm, _) = open(shorts.copy(hook = "Can you cook dal in 10 minutes?", structure = "Old body line. Another line.", callToAction = "Follow me."))

        vm.rewriteLine("Old body line.", "make it shorter")

        val project = vm.uiState.value.project!!
        assertEquals("Fresh body line. Another line.", project.structure)
        assertEquals("Can you cook dal in 10 minutes?", project.hook)
        assertEquals(StepState.AI_DRAFT, project.stepStates[WizardStep.SCRIPT])
        val request = ai.requests.single()
        assertTrue("make it shorter" in request.system && "Old body line." in request.messages.single().text)
    }

    @Test
    fun aLineEditedMeanwhile_isNotOverwritten() = runTest {
        ai.outcome = okReply("Something new.")
        val (vm, _) = open(shorts.copy(structure = "Body."))

        vm.rewriteLine("A line that is not there.", "rewrite")

        assertEquals("Body.", vm.uiState.value.project!!.structure)
        assertEquals(emptyList<AiError>(), vm.uiState.value.ai.errors)
    }

    @Test
    fun shorten_waitsForTheUserToAcceptTheShorterScript() = runTest {
        ai.outcome = okReply("HOOK: Short hook.\nSTRUCTURE: Short body.\nCTA: Short end.")
        val (vm, _) = open(shorts.copy(hook = "A long hook that goes on.", structure = "A long body that goes on and on."))

        vm.shortenScript()

        assertEquals("A long hook that goes on.", vm.uiState.value.project!!.hook) // not applied yet
        assertEquals("Short body.", vm.uiState.value.ai.draft!!.structure)
        assertTrue("about 30 seconds" in ai.requests.single().system)
        vm.applyDraft()
        assertEquals("Short hook.", vm.uiState.value.project!!.hook)
    }

    @Test
    fun overThePlanLength_isFlagged() = runTest {
        val long = "word ".repeat(100).trim() + "."
        val (free, _) = open(shorts.copy(structure = long))
        assertTrue(free.uiState.value.overPlanLength)
    }

    @Test
    fun anAiPicture_isMadeForTheProject_andShownInTheCounters() = runTest {
        val (vm, kit) = open(shorts)

        vm.makeImage("A pot of dal")

        assertTrue(vm.uiState.value.image.outcome is ImageOutcome.Ok)
        assertEquals(1, vm.uiState.value.usage.aiImages)
        assertEquals(47, vm.uiState.value.creditsLeft) // 3 credits
        assertEquals(1, assets.items.value.size)
        assertEquals(StepState.AI_DRAFT, vm.uiState.value.project!!.stepStates[WizardStep.FOOTAGE])
        assertEquals(listOf("A pot of dal"), kit.generator.prompts)
    }

    @Test
    fun theSixthFreePictureOfTheDay_isRefused() = runTest {
        val (vm, kit) = open(shorts)
        repeat(5) { vm.makeImage("p$it") }

        vm.makeImage("sixth")

        assertEquals(ImageOutcome.DailyLimit(5), vm.uiState.value.image.outcome)
        assertEquals(5, kit.generator.prompts.size)
        vm.dismissImageMessage()
        assertNull(vm.uiState.value.image.outcome)
    }

    @Test
    fun theScoreIsOnTheState() = runTest {
        val (vm, _) = open(shorts.copy(hook = "Can you cook dal in 10 minutes?"))

        assertNotNull(vm.uiState.value.score)
    }

    // ------------------------------------------------------------ Auto mode

    @Test
    fun autoMode_asksForConfirmationFirst_withTheCost() = runTest {
        val (vm, _) = open(shorts.copy(mode = CreateMode.AUTO))

        val run = vm.uiState.value.auto!!
        assertEquals(3, run.plan.images)
        assertEquals(11, run.plan.credits)
        assertEquals(false, run.started)
        assertTrue(ai.requests.isEmpty()) // nothing runs before the user confirms
    }

    @Test
    fun aStepByStepProject_neverShowsTheAutoDialog() = runTest {
        val (vm, _) = open(shorts)
        assertNull(vm.uiState.value.auto)
    }

    @Test
    fun autoMode_runsBriefScriptAndPictures_thenOpensGenerateToRender() = runTest {
        val (vm, kit) = open(shorts.copy(mode = CreateMode.AUTO))

        vm.runAuto()

        val run = vm.uiState.value.auto!!
        assertTrue(run.finished)
        assertEquals(AutoStatus.Done, run.steps[AutoStep.BRIEF])
        assertEquals(AutoStatus.Done, run.steps[AutoStep.SCRIPT])
        assertEquals(AutoStatus.Done, run.steps[AutoStep.IMAGES])
        val project = vm.uiState.value.project!!
        assertEquals("A calm start", project.angle)
        assertTrue(project.hook.startsWith("Can you cook dal"))
        assertEquals(3, kit.generator.prompts.size)
        assertEquals(3, assets.items.value.size)

        vm.finishAuto()

        assertNull(vm.uiState.value.auto)
        assertTrue(vm.uiState.value.autoRender)
        assertEquals(WizardStep.GENERATE, vm.uiState.value.step)
        vm.goToStep(WizardStep.REVIEW.ordinal)
        assertEquals(false, vm.uiState.value.autoRender) // leaving the Generate step stops the auto render
    }

    @Test
    fun autoMode_skipsAStepThatCannotRun_andGoesOnWithTheRest() = runTest {
        val (vm, kit) = open(shorts.copy(mode = CreateMode.AUTO))
        kit.generator.result = com.creatora.app.domain.create.ImageResult.Failed

        vm.runAuto()

        val steps = vm.uiState.value.auto!!.steps
        assertEquals(AutoStatus.Done, steps[AutoStep.SCRIPT])
        assertTrue(steps[AutoStep.IMAGES] is AutoStatus.Stopped) // no picture could be made, so none were counted
        assertEquals(0, vm.uiState.value.usage.aiImages)
        assertTrue(assets.items.value.isEmpty())
    }

    @Test
    fun autoMode_stopsTheScriptWhenTheAiIsBlocked_andSaysWhy() = runTest {
        ai.outcome = AiOutcome.Failed(listOf(AiError.LimitReached))
        val (vm, _) = open(shorts.copy(mode = CreateMode.AUTO))

        vm.runAuto()

        val steps = vm.uiState.value.auto!!.steps
        assertEquals(AutoStatus.Stopped(listOf(AiError.LimitReached)), steps[AutoStep.BRIEF])
        assertEquals(AutoStatus.Stopped(listOf(AiError.LimitReached)), steps[AutoStep.SCRIPT])
        assertEquals("", vm.uiState.value.project!!.hook)
    }

    @Test
    fun cancellingAuto_switchesToStepByStep_andKeepsTheProject() = runTest {
        val (vm, _) = open(shorts.copy(mode = CreateMode.AUTO))

        vm.cancelAuto()

        assertNull(vm.uiState.value.auto)
        assertEquals(CreateMode.STEP_BY_STEP, vm.uiState.value.project!!.mode)
    }
}
