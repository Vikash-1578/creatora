package com.creatora.app.data.ai

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.creatora.app.domain.model.AiProvider
import com.creatora.app.domain.model.AiProviderConfig
import com.creatora.app.domain.repository.AiProviderStore
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** The DataStore file that holds the encrypted API keys (excluded from backups). */
@Qualifier
annotation class AiKeysStore

/** Encrypts and decrypts short secrets. */
interface SecretCipher {
    fun encrypt(plain: String): String

    /** Null if [token] can't be decrypted, for example after the phone's keystore was reset. */
    fun decrypt(token: String): String?
}

/** AES-256-GCM with a key that lives in the Android Keystore and never leaves the phone. */
@Singleton
class AndroidKeystoreCipher @Inject constructor() : SecretCipher {

    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(plain.toByteArray()), Base64.NO_WRAP)
    }

    override fun decrypt(token: String): String? = runCatching {
        val bytes = Base64.decode(token, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
            .apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES)) }
        String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES))
    }.getOrNull()

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val ALIAS = "creatora_ai_keys"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}

/** [AiProviderStore] on its own DataStore file. Keys are stored only in encrypted form. */
class DataStoreAiProviderStore @Inject constructor(
    @AiKeysStore private val dataStore: DataStore<Preferences>,
    private val cipher: SecretCipher,
) : AiProviderStore {

    override val configs: Flow<List<AiProviderConfig>> = dataStore.data.map { prefs ->
        AiProvider.entries.map { provider ->
            AiProviderConfig(
                provider = provider,
                hasKey = prefs[keyOf(provider)]?.let(cipher::decrypt) != null,
                model = prefs[modelOf(provider)] ?: provider.defaultModel,
            )
        }
    }

    override suspend fun key(provider: AiProvider): String? =
        dataStore.data.first()[keyOf(provider)]?.let(cipher::decrypt)

    override suspend fun setKey(provider: AiProvider, key: String) {
        dataStore.edit { it[keyOf(provider)] = cipher.encrypt(key.trim()) }
    }

    override suspend fun removeKey(provider: AiProvider) {
        dataStore.edit { it.remove(keyOf(provider)) }
    }

    override suspend fun setModel(provider: AiProvider, model: String) {
        dataStore.edit { if (model.isBlank()) it.remove(modelOf(provider)) else it[modelOf(provider)] = model.trim() }
    }

    private fun keyOf(provider: AiProvider) = stringPreferencesKey("key_${provider.name}")
    private fun modelOf(provider: AiProvider) = stringPreferencesKey("model_${provider.name}")
}

/** The YouTube Data API key, stored encrypted in the same file as the AI keys. */
class DataStoreYouTubeKeyStore @Inject constructor(
    @AiKeysStore private val dataStore: DataStore<Preferences>,
    private val cipher: SecretCipher,
) : com.creatora.app.domain.repository.YouTubeKeyStore {
    private val keyName = stringPreferencesKey("key_YOUTUBE")

    override val hasKey: Flow<Boolean> = dataStore.data.map { prefs -> prefs[keyName]?.let(cipher::decrypt) != null }

    override suspend fun key(): String? = dataStore.data.first()[keyName]?.let(cipher::decrypt)

    override suspend fun setKey(key: String) {
        dataStore.edit { it[keyName] = cipher.encrypt(key.trim()) }
    }

    override suspend fun removeKey() {
        dataStore.edit { it.remove(keyName) }
    }
}
