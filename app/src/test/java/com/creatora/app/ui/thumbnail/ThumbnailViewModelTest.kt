package com.creatora.app.ui.thumbnail

import androidx.lifecycle.SavedStateHandle
import com.creatora.app.domain.model.Asset
import com.creatora.app.domain.model.AssetType
import com.creatora.app.domain.model.Project
import com.creatora.app.testutil.FakeAssetRepository
import com.creatora.app.testutil.FakeBrandKitRepository
import com.creatora.app.testutil.FakeMediaSaver
import com.creatora.app.testutil.FakeProjectRepository
import com.creatora.app.testutil.FakeThumbnailRenderer
import com.creatora.app.testutil.MainDispatcherRule
import com.creatora.app.testutil.keepActive
import com.creatora.app.ui.render.GalleryMessage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ThumbnailViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val projects = FakeProjectRepository()
    private val assets = FakeAssetRepository()
    private val brandKit = FakeBrandKitRepository()
    private val renderer = FakeThumbnailRenderer()
    private val saver = FakeMediaSaver()

    private fun newViewModel(projectId: Long) = ThumbnailViewModel(
        SavedStateHandle(mapOf(ThumbnailViewModel.ARG_PROJECT_ID to projectId)),
        projects, assets, brandKit, renderer, saver,
    )

    @Test
    fun startsWithTheProjectTitleAndOnlyImageAssets() = runTest {
        val id = projects.save(Project(title = "My video"))
        assets.save(Asset(uri = "file:///a.jpg", type = AssetType.IMAGE, name = "a.jpg", mimeType = "image/jpeg"))
        assets.save(Asset(uri = "file:///b.mp3", type = AssetType.AUDIO, name = "b.mp3", mimeType = "audio/mpeg"))
        assets.save(Asset(uri = "file:///c.jpg", type = AssetType.IMAGE, name = "c.jpg", mimeType = "image/jpeg", projectId = 99))
        val viewModel = newViewModel(id)
        keepActive(viewModel.uiState)

        val state = viewModel.uiState.value
        assertEquals("My video", state.text)
        assertEquals(listOf("a.jpg"), state.images.map { it.name })

        viewModel.setText("Edited")
        assertEquals("Edited", viewModel.uiState.value.text)
    }

    @Test
    fun create_drawsWithTheChosenPictureTextAndFirstBrandColour() = runTest {
        val id = projects.save(Project(title = "Title"))
        brandKit.update { it.copy(colors = listOf("#112233", "#445566")) }
        val viewModel = newViewModel(id)
        keepActive(viewModel.uiState)

        viewModel.selectImage("file:///a.jpg")
        viewModel.create()

        assertEquals(Triple<String?, String, String?>("file:///a.jpg", "Title", "#112233"), renderer.calls.single())
        assertEquals("file:///thumb.png", viewModel.uiState.value.resultUri)
        assertFalse(viewModel.uiState.value.working)
        assertFalse(viewModel.uiState.value.failed)
    }

    @Test
    fun create_reportsAPictureThatCannotBeRead_andKeepsThePreviousResult() = runTest {
        val id = projects.save(Project(title = "Title"))
        val viewModel = newViewModel(id)
        keepActive(viewModel.uiState)
        viewModel.create()

        renderer.result = null
        viewModel.create()

        assertTrue(viewModel.uiState.value.failed)
        assertEquals("file:///thumb.png", viewModel.uiState.value.resultUri)
    }

    @Test
    fun saving_usesThePngMimeTypeAndReportsTheResult() = runTest {
        val id = projects.save(Project(title = "Title"))
        val viewModel = newViewModel(id)
        keepActive(viewModel.uiState)
        viewModel.create()

        viewModel.saveToGallery("thumb.png")
        assertEquals(listOf(Triple("file:///thumb.png", "thumb.png", "image/png")), saver.gallery)
        assertEquals(GalleryMessage.SAVED, viewModel.uiState.value.galleryMessage)

        saver.succeeds = false
        viewModel.saveTo("content://dest")
        assertEquals(GalleryMessage.FAILED, viewModel.uiState.value.galleryMessage)
    }
}
