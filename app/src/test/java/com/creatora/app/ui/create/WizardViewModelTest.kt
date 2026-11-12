package com.creatora.app.ui.create

import androidx.lifecycle.SavedStateHandle
import com.creatora.app.domain.model.Asset
import com.creatora.app.domain.model.AssetType
import com.creatora.app.domain.model.Character
import com.creatora.app.domain.model.LicenceEntry
import com.creatora.app.domain.model.Project
import com.creatora.app.domain.model.VideoFormat
import com.creatora.app.domain.model.VoicePreset
import com.creatora.app.domain.model.WizardStep
import com.creatora.app.testutil.FakeAssetRepository
import com.creatora.app.testutil.FakeCharacterRepository
import com.creatora.app.testutil.FakeLicenceRepository
import com.creatora.app.testutil.FakeProjectRepository
import com.creatora.app.testutil.FakeSpeechPlayer
import com.creatora.app.testutil.FakeVoicePresetRepository
import com.creatora.app.testutil.MainDispatcherRule
import com.creatora.app.testutil.keepActive
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WizardViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val projects = FakeProjectRepository()
    private val characters = FakeCharacterRepository()
    private val voices = FakeVoicePresetRepository()
    private val assets = FakeAssetRepository()
    private val licences = FakeLicenceRepository()
    private val speech = FakeSpeechPlayer()
    private val kit = com.creatora.app.testutil.PlanKit()
    private val aiService = com.creatora.app.testutil.FakeAiService(com.creatora.app.testutil.okReply("HOOK: Hook text\nSTRUCTURE: Body text\nCTA: Call text"))

    private suspend fun newProject(project: Project = Project(title = "Video", format = VideoFormat.LONG_VIDEO)): Long =
        projects.save(project)

    private fun viewModel(projectId: Long) = WizardViewModel(
        SavedStateHandle(mapOf(WizardViewModel.ARG_PROJECT_ID to projectId)),
        projects, characters, voices, assets, licences, speech, com.creatora.app.domain.ai.AiTasks(aiService),
        kit.auth, kit.plans, kit.daily, kit.usage,
        com.creatora.app.domain.create.AiImages(kit.generator, assets, licences, kit.usage, kit.daily, kit.plans, kit.auth),
    )

    private fun TestScope.open(projectId: Long): WizardViewModel {
        val viewModel = viewModel(projectId)
        keepActive(viewModel.uiState)
        return viewModel
    }

    private fun saved(id: Long) = projects.items.value.single { it.id == id }

    @Test
    fun loadsTheProjectAndStartsOnItsSavedStep() = runTest {
        val id = newProject(Project(title = "Video", format = VideoFormat.SHORTS_REELS, step = 3))

        val state = open(id).uiState.value

        assertEquals("Video", state.project?.title)
        assertEquals(WizardStep.VOICE, state.step)
    }

    @Test
    fun edits_showAtOnceAndAreSavedAfterAShortPause() = runTest {
        val id = newProject()
        val viewModel = open(id)

        viewModel.setBrief("About cats")
        viewModel.setHook("Cats rule")

        assertEquals("Cats rule", viewModel.uiState.value.project?.hook)
        assertEquals("", saved(id).brief) // not saved yet

        advanceTimeBy(WizardViewModel.AUTOSAVE_MS + 1)
        runCurrent()

        assertEquals("About cats", saved(id).brief)
        assertEquals("Cats rule", saved(id).hook)
    }

    @Test
    fun goToStep_savesRightAway() = runTest {
        val id = newProject()
        val viewModel = open(id)

        viewModel.setStructure("Main points")
        viewModel.goToStep(WizardStep.SCRIPT.ordinal)

        assertEquals(1, saved(id).step)
        assertEquals("Main points", saved(id).structure)
    }

    @Test
    fun goToStep_staysInsideTheSevenSteps() = runTest {
        val id = newProject()
        val viewModel = open(id)

        viewModel.goToStep(99)
        assertEquals(WizardStep.GENERATE, viewModel.uiState.value.step)

        viewModel.goToStep(-4)
        assertEquals(WizardStep.BRIEF, viewModel.uiState.value.step)
    }

    @Test
    fun briefStep_needsAProjectNameToContinue() = runTest {
        val viewModel = open(newProject())
        assertTrue(viewModel.uiState.value.canGoNext)

        viewModel.setTitle("   ")
        assertFalse(viewModel.uiState.value.canGoNext)

        viewModel.setTitle("Name")
        viewModel.goToStep(WizardStep.SCRIPT.ordinal)
        viewModel.setTitle("")
        assertTrue(viewModel.uiState.value.canGoNext) // only the Brief step checks the name
    }

    @Test
    fun scriptStatsAndEstimate_followTheScript() = runTest {
        val viewModel = open(newProject())
        assertEquals(listOf(Blocker.EMPTY_SCRIPT), viewModel.uiState.value.blockers)

        viewModel.setHook("one two three")
        viewModel.setStructure("four five six seven eight nine ten")
        viewModel.setCallToAction("")

        val state = viewModel.uiState.value
        assertEquals(10, state.words)
        assertTrue(state.blockers.isEmpty())
        assertEquals(4, state.estimate.spokenSeconds)
    }

    @Test
    fun toggleCast_addsAndRemovesCharacters() = runTest {
        val mia = characters.save(Character(name = "Mia"))
        val leo = characters.save(Character(name = "Leo"))
        val viewModel = open(newProject())

        viewModel.toggleCast(mia)
        viewModel.toggleCast(leo)
        assertEquals(listOf("Mia", "Leo"), viewModel.uiState.value.cast.map { it.name })

        viewModel.toggleCast(mia)
        assertEquals(listOf("Leo"), viewModel.uiState.value.cast.map { it.name })
    }

    @Test
    fun castWithADeletedCharacter_ignoresIt() = runTest {
        val mia = characters.save(Character(name = "Mia"))
        val viewModel = open(newProject())
        viewModel.toggleCast(mia)

        characters.delete(mia)

        assertTrue(viewModel.uiState.value.cast.isEmpty())
    }

    @Test
    fun assignVoice_savesItOnTheCharacter() = runTest {
        val voice = voices.save(VoicePreset(name = "Deep", pitch = 0.7f))
        val mia = characters.save(Character(name = "Mia"))
        val viewModel = open(newProject())

        viewModel.assignVoice(characters.items.value.single(), voice)
        assertEquals(voice, characters.items.value.single { it.id == mia }.voicePresetId)

        viewModel.assignVoice(characters.items.value.single(), null)
        assertNull(characters.items.value.single { it.id == mia }.voicePresetId)
    }

    @Test
    fun preview_speaksTheScriptWithPronunciationsAndTheVoiceSettings() = runTest {
        val voice = voices.save(VoicePreset(name = "Fast", pitch = 1.2f, speechRate = 1.5f))
        val viewModel = open(newProject())
        viewModel.setHook("Meet Creatora today.")
        viewModel.setPronunciations("Creatora = kree-uh-TOR-uh")

        viewModel.preview(voices.items.value.single { it.id == voice }, fallbackText = "sample")

        assertEquals(listOf(FakeSpeechPlayer.Spoken("Meet kree-uh-TOR-uh today.", 1.2f, 1.5f)), speech.spoken)
        assertFalse(viewModel.uiState.value.speechUnavailable)
    }

    @Test
    fun preview_usesTheFallbackTextForAnEmptyScriptAndTheDefaultVoice() = runTest {
        val viewModel = open(newProject())

        viewModel.preview(null, fallbackText = "Sample line")

        assertEquals(listOf(FakeSpeechPlayer.Spoken("Sample line", 1f, 1f)), speech.spoken)
    }

    @Test
    fun preview_reportsAPhoneWithoutTextToSpeech() = runTest {
        speech.available = false
        val viewModel = open(newProject())

        viewModel.preview(null, "Sample")

        assertTrue(viewModel.uiState.value.speechUnavailable)
    }

    @Test
    fun footageCounts_showHowManyImportedFilesHaveALicence() = runTest {
        val a = assets.save(Asset(uri = "file:///a", type = AssetType.IMAGE, name = "a", mimeType = "image/png"))
        assets.save(Asset(uri = "file:///b", type = AssetType.IMAGE, name = "b", mimeType = "image/png"))
        licences.save(LicenceEntry(assetId = a, source = "Own", licence = "Owned"))
        val viewModel = open(newProject())

        val state = viewModel.uiState.value
        assertEquals(2, state.importedAssets)
        assertEquals(1, state.licensedAssets)

        assertTrue(state.project!!.licensedFootageOnly)
        viewModel.setLicensedFootageOnly(false)
        assertFalse(viewModel.uiState.value.project!!.licensedFootageOnly)
    }

    @Test
    fun markReady_savesTheProjectAsReady() = runTest {
        val id = newProject()
        val viewModel = open(id)

        viewModel.markReady()

        assertTrue(saved(id).isReady)
    }

    @Test
    fun flush_savesPendingEditsImmediately() = runTest {
        val id = newProject()
        val viewModel = open(id)

        viewModel.setBrief("Unsaved")
        viewModel.flush()

        assertEquals("Unsaved", saved(id).brief)
    }

    @Test
    fun draftScript_waitsForApprovalBeforeChangingTheScript() = runTest {
        val viewModel = open(newProject(Project(title = "Video", format = VideoFormat.LONG_VIDEO, brief = "About cats", hook = "My hook")))

        viewModel.draftScript()

        val draft = viewModel.uiState.value.ai.draft
        assertEquals("Body text", draft?.structure)
        assertTrue(aiService.requests.single().messages.single().text.contains("About cats"))
        assertEquals("My hook", viewModel.uiState.value.project?.hook) // not replaced yet

        viewModel.applyDraft()

        val project = viewModel.uiState.value.project!!
        assertEquals("Hook text", project.hook)
        assertEquals("Body text", project.structure)
        assertEquals("Call text", project.callToAction)
        assertNull(viewModel.uiState.value.ai.draft)
    }

    @Test
    fun dismissDraft_keepsTheUsersScript() = runTest {
        val viewModel = open(newProject(Project(title = "Video", brief = "b", hook = "Mine")))
        viewModel.draftScript()

        viewModel.dismissDraft()

        assertNull(viewModel.uiState.value.ai.draft)
        assertEquals("Mine", viewModel.uiState.value.project?.hook)
    }

    @Test
    fun aiFailure_showsErrorsAndGoToStepClearsThem() = runTest {
        aiService.outcome = com.creatora.app.domain.model.AiOutcome.Failed(listOf(com.creatora.app.domain.model.AiError.NoKey))
        val viewModel = open(newProject())

        viewModel.draftScript()

        assertEquals(listOf(com.creatora.app.domain.model.AiError.NoKey), viewModel.uiState.value.ai.errors)
        assertFalse(viewModel.uiState.value.ai.busy)
        viewModel.goToStep(WizardStep.SCRIPT.ordinal)
        assertNull(viewModel.uiState.value.ai.errors)
    }

    @Test
    fun generatePublishText_usesTheScriptAndKeepsTheResultUntilDismissed() = runTest {
        aiService.outcome = com.creatora.app.testutil.okReply("TITLES:\n- A title\nDESCRIPTION: Desc\nHASHTAGS: #x\nCHAPTERS: 00:00 Intro")
        val viewModel = open(newProject(Project(title = "Video", hook = "Hello world")))

        viewModel.generatePublishText()

        assertEquals(listOf("A title"), viewModel.uiState.value.ai.publishText?.titles)
        assertTrue(aiService.requests.single().messages.single().text.contains("Hello world"))
        viewModel.dismissPublishText()
        assertNull(viewModel.uiState.value.ai.publishText)
    }

    @Test
    fun stopPreview_stopsSpeech() = runTest {
        val viewModel = open(newProject())
        viewModel.stopPreview()
        assertEquals(1, speech.stops)
    }
}
