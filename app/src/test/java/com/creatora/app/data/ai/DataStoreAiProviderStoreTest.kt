package com.creatora.app.data.ai

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.core.DataStore
import com.creatora.app.domain.model.AiProvider
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Reverses the text; good enough to prove the store never writes the plain key. */
private class ReverseCipher(var canDecrypt: Boolean = true) : SecretCipher {
    override fun encrypt(plain: String) = "enc:" + plain.reversed()
    override fun decrypt(token: String) = if (canDecrypt) token.removePrefix("enc:").reversed() else null
}

class DataStoreAiProviderStoreTest {
    @get:Rule val folder = TemporaryFolder()

    private fun TestScope.dataStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "ai.preferences_pb") }

    @Test
    fun startsWithNoKeysAndDefaultModels() = runTest {
        val configs = DataStoreAiProviderStore(dataStore(), ReverseCipher()).configs.first()

        assertEquals(AiProvider.entries, configs.map { it.provider })
        assertTrue(configs.none { it.hasKey })
        assertEquals(AiProvider.entries.map { it.defaultModel }, configs.map { it.model })
    }

    @Test
    fun savesTheKeyEncryptedAndReadsItBack() = runTest {
        val dataStore = dataStore()
        val store = DataStoreAiProviderStore(dataStore, ReverseCipher())

        store.setKey(AiProvider.GROQ, "  gsk_secret  ")

        assertEquals("gsk_secret", store.key(AiProvider.GROQ))
        assertNull(store.key(AiProvider.GEMINI))
        assertTrue(store.configs.first().single { it.provider == AiProvider.GROQ }.hasKey)
        val raw = dataStore.data.first().asMap().values.map { it.toString() }
        assertTrue(raw.none { "gsk_secret" in it })
    }

    @Test
    fun removeKey_forgetsIt() = runTest {
        val store = DataStoreAiProviderStore(dataStore(), ReverseCipher())
        store.setKey(AiProvider.GEMINI, "abc")

        store.removeKey(AiProvider.GEMINI)

        assertNull(store.key(AiProvider.GEMINI))
        assertFalse(store.configs.first().first().hasKey)
    }

    @Test
    fun aKeyThatCannotBeDecrypted_countsAsMissing() = runTest {
        val cipher = ReverseCipher()
        val store = DataStoreAiProviderStore(dataStore(), cipher)
        store.setKey(AiProvider.GEMINI, "abc")

        cipher.canDecrypt = false

        assertNull(store.key(AiProvider.GEMINI))
        assertFalse(store.configs.first().first().hasKey)
    }

    @Test
    fun modelNameCanBeSavedAndResetToTheDefault() = runTest {
        val store = DataStoreAiProviderStore(dataStore(), ReverseCipher())

        store.setModel(AiProvider.OPENROUTER, " my/model ")
        assertEquals("my/model", store.configs.first().last().model)

        store.setModel(AiProvider.OPENROUTER, "  ")
        assertEquals(AiProvider.OPENROUTER.defaultModel, store.configs.first().last().model)
        assertNotEquals("my/model", store.configs.first().last().model)
    }
}
