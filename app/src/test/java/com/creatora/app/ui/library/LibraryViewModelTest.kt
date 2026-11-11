package com.creatora.app.ui.library

import com.creatora.app.domain.model.Asset
import com.creatora.app.domain.model.AssetType
import com.creatora.app.domain.model.LicenceEntry
import com.creatora.app.domain.model.PickedFile
import com.creatora.app.domain.model.Platform
import com.creatora.app.domain.model.Project
import com.creatora.app.domain.model.ScheduledPost
import com.creatora.app.testutil.FakeAssetRepository
import com.creatora.app.testutil.FakeCharacterRepository
import com.creatora.app.testutil.FakeLicenceRepository
import com.creatora.app.testutil.FakeProjectRepository
import com.creatora.app.testutil.FakeScheduledPostRepository
import com.creatora.app.testutil.FakeUserFiles
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
class LibraryViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val projects = FakeProjectRepository()
    private val posts = FakeScheduledPostRepository()
    private val assets = FakeAssetRepository()
    private val characters = FakeCharacterRepository()
    private val voices = FakeVoicePresetRepository()
    private val licences = FakeLicenceRepository()
    private val files = FakeUserFiles()
    private val viewModel = LibraryViewModel(projects, posts, assets, characters, voices, licences, files)

    private fun TestScope.state(): LibraryUiState {
        keepActive(viewModel.uiState)
        return viewModel.uiState.value
    }

    @Test
    fun createProject_trimsTheNameAndIgnoresBlank() = runTest {
        viewModel.createProject("  My video  ")
        viewModel.createProject("   ")

        assertEquals(listOf("My video"), state().projects.map { it.project.title })
    }

    @Test
    fun templatesAreKeptApartFromProjects() = runTest {
        projects.save(Project(title = "Video"))
        projects.save(Project(title = "Intro layout", isTemplate = true))

        val state = state()
        assertEquals(listOf("Video"), state.projects.map { it.project.title })
        assertEquals(listOf("Intro layout"), state.templates.map { it.title })
    }

    @Test
    fun copyProject_makesANewProjectOrATemplate() = runTest {
        val id = projects.save(Project(title = "Video"))
        val original = projects.items.value.single { it.id == id }

        viewModel.copyProject(original, "Video (copy)", asTemplate = false)
        viewModel.copyProject(original, "Video template", asTemplate = true)

        val all = projects.items.value
        assertEquals(3, all.size)
        assertEquals(3, all.map { it.id }.toSet().size)
        assertFalse(all.single { it.title == "Video (copy)" }.isTemplate)
        assertTrue(all.single { it.title == "Video template" }.isTemplate)
    }

    @Test
    fun renameProject_changesTheTitleAndKeepsTheId() = runTest {
        val id = projects.save(Project(title = "Old", createdAt = 1, updatedAt = 1))

        viewModel.renameProject(projects.items.value.single(), " New ")

        val renamed = projects.items.value.single()
        assertEquals(id, renamed.id)
        assertEquals("New", renamed.title)
        assertTrue(renamed.updatedAt > 1)
    }

    @Test
    fun project_showsItsNextFutureScheduledPost() = runTest {
        val now = System.currentTimeMillis()
        val id = projects.save(Project(title = "Video"))
        posts.save(ScheduledPost(projectId = id, platform = Platform.YOUTUBE, scheduledAt = now - 1_000))
        posts.save(ScheduledPost(projectId = id, platform = Platform.YOUTUBE, scheduledAt = now + 90_000))
        posts.save(ScheduledPost(projectId = id, platform = Platform.INSTAGRAM, scheduledAt = now + 60_000))

        assertEquals(now + 60_000, state().projects.single().goesLiveAt)
    }

    @Test
    fun delete_hidesAtOnceAndDeletesAfterTheUndoWindow() = runTest {
        val id = projects.save(Project(title = "Video"))
        state()

        viewModel.delete(DeleteKind.PROJECT, id, "Video")

        assertTrue(viewModel.uiState.value.projects.isEmpty())
        assertEquals(1, projects.items.value.size) // still stored, so Undo can bring it back

        advanceTimeBy(LibraryViewModel.UNDO_WINDOW_MS + 1)
        runCurrent()

        assertTrue(projects.items.value.isEmpty())
        assertNull(viewModel.uiState.value.pendingDelete)
    }

    @Test
    fun undoDelete_bringsTheItemBackAndNeverDeletesIt() = runTest {
        val id = projects.save(Project(title = "Video"))
        state()

        viewModel.delete(DeleteKind.PROJECT, id, "Video")
        viewModel.undoDelete()
        advanceTimeBy(LibraryViewModel.UNDO_WINDOW_MS * 2)
        runCurrent()

        assertEquals(listOf("Video"), viewModel.uiState.value.projects.map { it.project.title })
        assertEquals(1, projects.items.value.size)
    }

    @Test
    fun startingASecondDelete_commitsTheFirstOne() = runTest {
        val first = projects.save(Project(title = "First"))
        val second = projects.save(Project(title = "Second"))
        state()

        viewModel.delete(DeleteKind.PROJECT, first, "First")
        viewModel.delete(DeleteKind.PROJECT, second, "Second")

        assertEquals(listOf("Second"), projects.items.value.map { it.title })
        assertTrue(viewModel.uiState.value.projects.isEmpty())
    }

    @Test
    fun deletingAnAsset_alsoDeletesItsCopiedFile() = runTest {
        val id = assets.save(Asset(uri = "file:///a.jpg", type = AssetType.IMAGE, name = "a.jpg", mimeType = "image/jpeg"))
        state()

        viewModel.delete(DeleteKind.ASSET, id, "a.jpg")
        advanceTimeBy(LibraryViewModel.UNDO_WINDOW_MS + 1)
        runCurrent()

        assertTrue(assets.items.value.isEmpty())
        assertEquals(listOf("file:///a.jpg"), files.discarded)
    }

    @Test
    fun importFiles_addsSupportedFilesAndCountsTheRest() = runTest {
        files.readable["content://1"] = PickedFile("file:///1.jpg", "1.jpg", "image/jpeg")
        files.readable["content://2"] = PickedFile("file:///2.mp4", "2.mp4", "video/mp4")
        files.readable["content://3"] = PickedFile("file:///3.mp3", "3.mp3", "audio/mpeg")
        files.readable["content://4"] = PickedFile("file:///4.pdf", "4.pdf", "application/pdf")
        // content://5 can't be read at all

        viewModel.importFiles(listOf("content://1", "content://2", "content://3", "content://4", "content://5"))

        val state = state()
        assertEquals(
            listOf(AssetType.IMAGE, AssetType.VIDEO, AssetType.AUDIO),
            assets.items.value.map { it.type },
        )
        assertEquals(ImportResult(imported = 3, skipped = 2), state.importResult)
        assertEquals(listOf("file:///4.pdf"), files.discarded) // the unsupported copy is cleaned up

        viewModel.importResultShown()
        assertNull(viewModel.uiState.value.importResult)
    }

    @Test
    fun assetsWithALicenceEntryAreMarked() = runTest {
        val logged = assets.save(Asset(uri = "file:///a", type = AssetType.IMAGE, name = "a", mimeType = "image/png"))
        assets.save(Asset(uri = "file:///b", type = AssetType.IMAGE, name = "b", mimeType = "image/png"))
        licences.save(LicenceEntry(assetId = logged, source = "Own", licence = "Owned"))

        val state = state()
        assertEquals(setOf(logged), state.licensedAssetIds)
        assertEquals(1, state.licenceCount)
    }

    @Test
    fun search_filtersEveryListByName() = runTest {
        projects.save(Project(title = "Cooking video"))
        projects.save(Project(title = "Travel vlog"))
        characters.save(com.creatora.app.domain.model.Character(name = "Chef"))
        characters.save(com.creatora.app.domain.model.Character(name = "Pilot"))
        state()

        viewModel.setQuery("COOK")
        assertEquals(listOf("Cooking video"), viewModel.uiState.value.projects.map { it.project.title })
        assertTrue(viewModel.uiState.value.characters.isEmpty())

        viewModel.setQuery("pil")
        assertEquals(listOf("Pilot"), viewModel.uiState.value.characters.map { it.name })

        viewModel.setQuery("")
        assertEquals(2, viewModel.uiState.value.projects.size)
    }

    @Test
    fun setFilter_changesTheSelectedFilter() = runTest {
        state()
        viewModel.setFilter(LibraryFilter.VOICES)
        assertEquals(LibraryFilter.VOICES, viewModel.uiState.value.filter)
    }

    @Test
    fun saveCharacterAndVoice_ignoreBlankNames() = runTest {
        viewModel.saveCharacter(com.creatora.app.domain.model.Character(name = "  "))
        viewModel.saveVoice(com.creatora.app.domain.model.VoicePreset(name = ""))
        viewModel.saveCharacter(com.creatora.app.domain.model.Character(name = " Mia "))
        viewModel.saveVoice(com.creatora.app.domain.model.VoicePreset(name = "Calm", pitch = 0.8f))

        assertEquals(listOf("Mia"), characters.items.value.map { it.name })
        assertEquals(listOf(0.8f), voices.items.value.map { it.pitch })
    }
}
