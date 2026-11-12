package com.creatora.app.ui.render

import androidx.lifecycle.SavedStateHandle
import com.creatora.app.domain.model.Asset
import com.creatora.app.domain.model.AssetType
import com.creatora.app.domain.model.LicenceEntry
import com.creatora.app.domain.model.Project
import com.creatora.app.domain.model.VideoFormat
import com.creatora.app.domain.model.VoicePreset
import com.creatora.app.domain.render.AspectRatio
import com.creatora.app.domain.render.RenderEvent
import com.creatora.app.testutil.FakeAssetRepository
import com.creatora.app.testutil.FakeLicenceRepository
import com.creatora.app.testutil.FakeMediaProbe
import com.creatora.app.testutil.FakeMediaSaver
import com.creatora.app.testutil.FakeNarrator
import com.creatora.app.testutil.FakeProjectRepository
import com.creatora.app.testutil.FakeVideoRenderer
import com.creatora.app.testutil.FakeVoicePresetRepository
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

class RenderViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val projects = FakeProjectRepository()
    private val assets = FakeAssetRepository()
    private val licences = FakeLicenceRepository()
    private val voices = FakeVoicePresetRepository()
    private val narrator = FakeNarrator()
    private val probe = FakeMediaProbe()
    private val renderer = FakeVideoRenderer(listOf(RenderEvent.Progress(50), RenderEvent.Done("file:///out.mp4")))
    private val saver = FakeMediaSaver()
    private val kit = com.creatora.app.testutil.PlanKit()

    private suspend fun script(project: Project = Project(title = "Video", format = VideoFormat.LONG_VIDEO, hook = "One. Two.", structure = "Three.", licensedFootageOnly = false)) =
        projects.save(project)

    private suspend fun image(name: String, projectId: Long? = null): Long =
        assets.save(Asset(uri = "file:///$name.jpg", type = AssetType.IMAGE, name = "$name.jpg", mimeType = "image/jpeg", projectId = projectId))

    private fun TestScope.open(projectId: Long): RenderViewModel {
        val viewModel = RenderViewModel(
            SavedStateHandle(mapOf(RenderViewModel.ARG_PROJECT_ID to projectId)),
            projects, assets, licences, voices, narrator, probe, renderer, saver, kit.auth, kit.plans, kit.daily,
        )
        keepActive(viewModel.uiState)
        return viewModel
    }

    @Test
    fun theDefaultSizeFollowsTheFormat() = runTest {
        val shorts = script(Project(title = "S", format = VideoFormat.SHORTS_REELS))
        val post = script(Project(title = "P", format = VideoFormat.POST_CAROUSEL))
        val long = script()

        assertEquals(AspectRatio.PORTRAIT, open(shorts).uiState.value.options.ratio)
        assertEquals(AspectRatio.SQUARE, open(post).uiState.value.options.ratio)
        assertEquals(AspectRatio.LANDSCAPE, open(long).uiState.value.options.ratio)
    }

    @Test
    fun footage_followsTheLicencePolicyAndTheProject() = runTest {
        val licensedOnly = script(Project(title = "V", format = VideoFormat.LONG_VIDEO, hook = "Hi.", licensedFootageOnly = true))
        val withLicence = image("a")
        image("b") // no licence
        image("other", projectId = 999)
        licences.save(LicenceEntry(assetId = withLicence, source = "Own", licence = "Owned"))
        assets.save(Asset(uri = "file:///m.mp3", type = AssetType.AUDIO, name = "m.mp3", mimeType = "audio/mpeg"))

        val state = open(licensedOnly).uiState.value
        assertEquals(listOf("a.jpg"), state.footage.map { it.name })
        assertTrue(state.music.isEmpty()) // the music has no licence entry either

        val anything = script()
        val anyState = open(anything).uiState.value
        assertEquals(listOf("a.jpg", "b.jpg"), anyState.footage.map { it.name })
        assertEquals(listOf("m.mp3"), anyState.music.map { it.name })
    }

    @Test
    fun render_narratesEachSentenceBuildsTheRequestAndMarksTheProjectReady() = runTest {
        val voice = voices.save(VoicePreset(name = "Deep", pitch = 0.8f, speechRate = 1.2f))
        val id = script(Project(title = "Video", format = VideoFormat.LONG_VIDEO, hook = "Meet Creatora.", structure = "Two.", narratorVoiceId = voice, pronunciations = "Creatora = kree-uh-TOR-uh", licensedFootageOnly = false))
        image("a")
        val viewModel = open(id)

        viewModel.render()

        assertEquals(listOf("Meet kree-uh-TOR-uh.", "Two."), narrator.spoken.map { it.first })
        assertEquals(listOf(0.8f, 0.8f), narrator.spoken.map { it.second })
        val request = renderer.request!!
        assertEquals(AspectRatio.LANDSCAPE, request.ratio)
        assertEquals(listOf("Meet Creatora.", "Two."), request.cues.map { it.text }) // captions show the written text
        assertEquals(4_000_000L, request.scenes.sumOf { it.durationUs })
        assertEquals(RenderPhase.Done("file:///out.mp4"), viewModel.uiState.value.phase)
        val saved = projects.items.value.single()
        assertEquals("file:///out.mp4", saved.renderUri)
        assertTrue(saved.isReady)
        assertTrue(narrator.released > 0)
    }

    @Test
    fun captionsCanBeTurnedOff_andMusicIsPassedOn() = runTest {
        val id = script()
        image("a")
        val music = assets.save(Asset(uri = "file:///m.mp3", type = AssetType.AUDIO, name = "m.mp3", mimeType = "audio/mpeg"))
        val viewModel = open(id)

        viewModel.setCaptions(false)
        viewModel.setMusic(music)
        viewModel.setRatio(AspectRatio.SQUARE)
        viewModel.render()

        val request = renderer.request!!
        assertTrue(request.cues.isEmpty())
        assertEquals("file:///m.mp3", request.musicUri)
        assertEquals(AspectRatio.SQUARE, request.ratio)
    }

    @Test
    fun videoLengthsComeFromTheProbe() = runTest {
        val id = script()
        assets.save(Asset(uri = "file:///v.mp4", type = AssetType.VIDEO, name = "v.mp4", mimeType = "video/mp4"))
        probe.durations = mapOf("file:///v.mp4" to 1_500L)
        val viewModel = open(id)

        viewModel.render()

        val scene = renderer.request!!.scenes.first()
        assertTrue(scene.isVideo)
        assertEquals(1_500_000L, scene.durationUs)
    }

    @Test
    fun problems_areReportedAndNothingIsRendered() = runTest {
        val emptyScript = script(Project(title = "E", format = VideoFormat.LONG_VIDEO, licensedFootageOnly = false))
        image("a")
        open(emptyScript).also { it.render() }.let { assertEquals(RenderPhase.Failed(RenderProblem.NO_SCRIPT), it.uiState.value.phase) }

        val noFootage = script(Project(title = "N", format = VideoFormat.LONG_VIDEO, hook = "Hi.", licensedFootageOnly = true))
        open(noFootage).also { it.render() }.let { assertEquals(RenderPhase.Failed(RenderProblem.NO_FOOTAGE), it.uiState.value.phase) }

        narrator.secondsPerSentence = null
        open(script()).also { it.render() }.let { assertEquals(RenderPhase.Failed(RenderProblem.NO_VOICE), it.uiState.value.phase) }

        assertNull(renderer.request)
    }

    @Test
    fun aFailedRender_isReportedAndTheProjectIsNotMarkedReady() = runTest {
        renderer.events = listOf(RenderEvent.Progress(10), RenderEvent.Failed("ERROR_CODE_X"))
        val id = script()
        image("a")
        val viewModel = open(id)

        viewModel.render()

        assertEquals(RenderPhase.Failed(RenderProblem.RENDER_FAILED), viewModel.uiState.value.phase)
        assertFalse(projects.items.value.single().isReady)
        assertNull(projects.items.value.single().renderUri)
    }

    @Test
    fun cancel_returnsToIdleAndFreesTheVoice() = runTest {
        val viewModel = open(script())

        viewModel.cancel()

        assertEquals(RenderPhase.Idle, viewModel.uiState.value.phase)
        assertTrue(narrator.released > 0)
    }

    @Test
    fun saving_reportsSuccessOrFailure() = runTest {
        val id = script(Project(title = "V", format = VideoFormat.LONG_VIDEO, hook = "Hi.", renderUri = "file:///old.mp4"))
        val viewModel = open(id)
        assertEquals("file:///old.mp4", viewModel.uiState.value.resultUri) // an earlier render is still offered

        viewModel.saveToGallery("old.mp4")
        assertEquals(GalleryMessage.SAVED, viewModel.uiState.value.galleryMessage)
        assertEquals(listOf(Triple("file:///old.mp4", "old.mp4", "video/mp4")), saver.gallery)

        saver.succeeds = false
        viewModel.saveTo("content://picked")
        assertEquals(GalleryMessage.FAILED, viewModel.uiState.value.galleryMessage)
        assertEquals(listOf("file:///old.mp4" to "content://picked"), saver.copies)
    }
}
