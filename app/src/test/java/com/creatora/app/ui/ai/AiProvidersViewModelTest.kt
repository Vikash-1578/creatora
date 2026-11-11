package com.creatora.app.ui.ai

import com.creatora.app.domain.model.AiProvider
import com.creatora.app.testutil.FakeAiProviderStore
import com.creatora.app.testutil.FakeYouTubeKeyStore
import com.creatora.app.testutil.MainDispatcherRule
import com.creatora.app.testutil.keepActive
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AiProvidersViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val store = FakeAiProviderStore()
    private val youTube = FakeYouTubeKeyStore()

    @Test
    fun save_storesANewKeyAndAnEditedModel() = runTest {
        val viewModel = AiProvidersViewModel(store, youTube)
        keepActive(viewModel.configs)

        viewModel.save(AiProvider.GROQ, key = " abc ", model = "custom-model")

        assertEquals("abc", store.key(AiProvider.GROQ))
        val groq = viewModel.configs.value.single { it.provider == AiProvider.GROQ }
        assertTrue(groq.hasKey)
        assertEquals("custom-model", groq.model)
    }

    @Test
    fun save_withAnEmptyKeyKeepsTheSavedOne_andTheDefaultModelClearsTheOverride() = runTest {
        val viewModel = AiProvidersViewModel(store, youTube)
        keepActive(viewModel.configs)
        viewModel.save(AiProvider.GEMINI, "first", "custom")

        viewModel.save(AiProvider.GEMINI, key = "", model = AiProvider.GEMINI.defaultModel)

        assertEquals("first", store.key(AiProvider.GEMINI))
        assertEquals(AiProvider.GEMINI.defaultModel, viewModel.configs.value.first().model)
    }

    @Test
    fun youTubeKey_canBeSavedAndRemoved_butBlankIsIgnored() = runTest {
        val viewModel = AiProvidersViewModel(store, youTube)
        keepActive(viewModel.youTubeHasKey)

        viewModel.saveYouTubeKey("   ")
        assertFalse(viewModel.youTubeHasKey.value)

        viewModel.saveYouTubeKey(" yt-key ")
        assertEquals("yt-key", youTube.key())
        assertTrue(viewModel.youTubeHasKey.value)

        viewModel.removeYouTubeKey()
        assertNull(youTube.key())
        assertFalse(viewModel.youTubeHasKey.value)
    }

    @Test
    fun removeKey_forgetsTheKey() = runTest {
        val viewModel = AiProvidersViewModel(store, youTube)
        keepActive(viewModel.configs)
        viewModel.save(AiProvider.OPENROUTER, "k", AiProvider.OPENROUTER.defaultModel)

        viewModel.removeKey(AiProvider.OPENROUTER)

        assertNull(store.key(AiProvider.OPENROUTER))
        assertFalse(viewModel.configs.value.last().hasKey)
    }
}
