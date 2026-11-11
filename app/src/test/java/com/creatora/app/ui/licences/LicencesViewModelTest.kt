package com.creatora.app.ui.licences

import androidx.lifecycle.SavedStateHandle
import com.creatora.app.domain.model.Asset
import com.creatora.app.domain.model.AssetType
import com.creatora.app.domain.model.LicenceEntry
import com.creatora.app.testutil.FakeAssetRepository
import com.creatora.app.testutil.FakeLicenceRepository
import com.creatora.app.testutil.MainDispatcherRule
import com.creatora.app.testutil.keepActive
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LicencesViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val licences = FakeLicenceRepository()
    private val assets = FakeAssetRepository()

    private suspend fun twoAssets(): Pair<Long, Long> {
        val a = assets.save(Asset(uri = "file:///a", type = AssetType.IMAGE, name = "a.jpg", mimeType = "image/jpeg"))
        val b = assets.save(Asset(uri = "file:///b", type = AssetType.AUDIO, name = "b.mp3", mimeType = "audio/mpeg"))
        licences.save(LicenceEntry(assetId = a, source = "Own photo", licence = "Owned"))
        licences.save(LicenceEntry(assetId = b, source = "Archive", licence = "CC BY 4.0"))
        return a to b
    }

    private fun viewModel(assetId: Long?) = LicencesViewModel(
        SavedStateHandle(if (assetId == null) emptyMap() else mapOf(LicencesViewModel.ARG_ASSET_ID to assetId)),
        licences,
        assets,
    )

    @Test
    fun forOneAsset_showsOnlyItsEntries() = runTest {
        val (a, _) = twoAssets()
        val viewModel = viewModel(a)
        keepActive(viewModel.uiState)

        val state = viewModel.uiState.value
        assertEquals("a.jpg", state.asset?.name)
        assertEquals(listOf("Owned"), state.rows.map { it.entry.licence })
    }

    @Test
    fun forAllAssets_showsEveryEntryWithItsAssetName() = runTest {
        twoAssets()
        val viewModel = viewModel(null)
        keepActive(viewModel.uiState)

        val state = viewModel.uiState.value
        assertNull(state.asset)
        assertEquals(listOf("a.jpg", "b.mp3"), state.rows.map { it.assetName })
    }

    @Test
    fun entryOfADeletedAsset_hasNoAssetName() = runTest {
        val (a, _) = twoAssets()
        assets.delete(a)
        val viewModel = viewModel(null)
        keepActive(viewModel.uiState)

        assertNull(viewModel.uiState.value.rows.first().assetName)
    }

    @Test
    fun addEntry_trimsAndSavesForTheAsset() = runTest {
        val (a, _) = twoAssets()
        val viewModel = viewModel(a)
        keepActive(viewModel.uiState)

        viewModel.addEntry(" Pexels ", " Pexels licence ", " Sam ", " https://example.com ")

        val entry = viewModel.uiState.value.rows.map { it.entry }.first { it.source == "Pexels" }
        assertEquals("Pexels licence", entry.licence)
        assertEquals("Sam", entry.author)
        assertEquals("https://example.com", entry.url)
        assertEquals(a, entry.assetId)
    }

    @Test
    fun addEntry_needsASourceAndALicenceAndAnAsset() = runTest {
        val (a, _) = twoAssets()
        val forAsset = viewModel(a)
        val forAll = viewModel(null)

        forAsset.addEntry("", "Owned", "", "")
        forAsset.addEntry("Own", "  ", "", "")
        forAll.addEntry("Own", "Owned", "", "")

        assertEquals(2, licences.items.value.size)
    }

    @Test
    fun delete_removesTheEntry() = runTest {
        val (a, _) = twoAssets()
        val viewModel = viewModel(a)
        keepActive(viewModel.uiState)

        viewModel.delete(viewModel.uiState.value.rows.single().entry)

        assertTrue(viewModel.uiState.value.rows.isEmpty())
    }
}
