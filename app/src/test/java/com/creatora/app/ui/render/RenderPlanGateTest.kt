package com.creatora.app.ui.render

import androidx.lifecycle.SavedStateHandle
import com.creatora.app.domain.account.Plan
import com.creatora.app.domain.home.dayKey
import com.creatora.app.domain.model.Asset
import com.creatora.app.domain.model.AssetType
import com.creatora.app.domain.model.Project
import com.creatora.app.domain.model.VideoFormat
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
import com.creatora.app.testutil.PlanKit
import com.creatora.app.testutil.keepActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The plan's format, length and videos-a-day limits at render time, and when a render counts. */
class RenderPlanGateTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val projects = FakeProjectRepository()
    private val assets = FakeAssetRepository()
    private val licences = FakeLicenceRepository()
    private val renderer = FakeVideoRenderer(listOf(RenderEvent.Progress(50), RenderEvent.Done("file:///out.mp4")))
    private val today = dayKey(System.currentTimeMillis())

    private suspend fun project(format: VideoFormat = VideoFormat.SHORTS_REELS, words: Int = 10, counted: Boolean = false): Long {
        val id = projects.save(Project(title = "Video", format = format, structure = ("word ".repeat(words).trim()) + ".", licensedFootageOnly = false, counted = counted))
        assets.save(Asset(uri = "file:///a.jpg", type = AssetType.IMAGE, name = "a.jpg", mimeType = "image/jpeg", projectId = id))
        return id
    }

    private fun TestScope.open(id: Long, kit: PlanKit): RenderViewModel {
        val vm = RenderViewModel(
            SavedStateHandle(mapOf(RenderViewModel.ARG_PROJECT_ID to id)),
            projects, assets, licences, FakeVoicePresetRepository(), FakeNarrator(), FakeMediaProbe(), renderer, FakeMediaSaver(),
            kit.auth, kit.plans, kit.daily,
        )
        keepActive(vm.uiState)
        return vm
    }

    @Test
    fun aFreeShortIsRendered_andCountsOnce() = runTest {
        val kit = PlanKit(Plan.FREE)
        val id = project()
        val vm = open(id, kit)

        vm.render()

        assertTrue(vm.uiState.value.phase is RenderPhase.Done)
        assertEquals(1, kit.daily.observe("uid1", today).first().videos)
        assertTrue(projects.items.value.single().counted)

        vm.render() // re-rendering the same project does not count again
        assertEquals(1, kit.daily.observe("uid1", today).first().videos)
    }

    @Test
    fun theSecondFreeVideoOfTheDay_isRefused() = runTest {
        val kit = PlanKit(Plan.FREE)
        kit.daily.add("uid1", today, videos = 1)
        val vm = open(project(), kit)

        vm.render()

        assertEquals(RenderPhase.Failed(RenderProblem.DAILY_LIMIT), vm.uiState.value.phase)
        assertEquals(1, kit.daily.observe("uid1", today).first().videos)
    }

    @Test
    fun aFreeVideoOverThirtySeconds_isRefused_evenForAProjectAlreadyCounted() = runTest {
        val kit = PlanKit(Plan.FREE)
        val vm = open(project(words = 100, counted = true), kit) // 100 words is about 40 seconds

        vm.render()

        assertEquals(RenderPhase.Failed(RenderProblem.TOO_LONG), vm.uiState.value.phase)
    }

    @Test
    fun aLockedFormat_isRefusedOnFree_andRendersOnPro() = runTest {
        val free = PlanKit(Plan.FREE)
        val vm = open(project(VideoFormat.LONG_VIDEO), free)
        vm.render()
        assertEquals(RenderPhase.Failed(RenderProblem.FORMAT_LOCKED), vm.uiState.value.phase)

        val pro = PlanKit(Plan.PRO)
        val proVm = open(projects.items.value.single().id, pro)
        proVm.render()
        assertTrue(proVm.uiState.value.phase is RenderPhase.Done)
    }

    @Test
    fun proMakesSeveralADay() = runTest {
        val kit = PlanKit(Plan.PRO)
        kit.daily.add("uid1", today, videos = 3)
        val vm = open(project(VideoFormat.LONG_VIDEO), kit)

        vm.render()

        assertTrue(vm.uiState.value.phase is RenderPhase.Done)
        assertEquals(4, kit.daily.observe("uid1", today).first().videos)
    }
}
