package com.creatora.app.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.creatora.app.domain.model.BrandKit
import com.creatora.app.domain.model.FontFile
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreBrandKitRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    private fun TestScope.repository() = DataStoreBrandKitRepository(
        PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "kit.preferences_pb") },
    )

    @Test
    fun startsEmpty() = runTest {
        assertEquals(BrandKit(), repository().brandKit.first())
    }

    @Test
    fun savesLogoColoursAndFonts() = runTest {
        val repository = repository()
        val kit = BrandKit(
            logoUri = "file:///logo.png",
            colors = listOf("#112233", "#445566"),
            fonts = listOf(FontFile("Brand Bold.ttf", "file:///a b/Brand Bold.ttf")),
        )

        repository.update { kit }

        assertEquals(kit, repository.brandKit.first())
    }

    @Test
    fun clearingTheLogoAndLists_removesThem() = runTest {
        val repository = repository()
        repository.update { BrandKit(logoUri = "file:///logo.png", colors = listOf("#112233")) }

        repository.update { BrandKit() }

        assertEquals(BrandKit(), repository.brandKit.first())
    }
}
