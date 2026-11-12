package com.creatora.app.data.platform

import android.content.Context
import com.creatora.app.data.ai.SecretCipher
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.platform.StoredToken
import com.creatora.app.domain.platform.TokenStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

/**
 * Sign-in tokens, encrypted with the same Android Keystore key as the AI keys and kept in the app's private storage.
 * They never leave the device. Clearing the app's data removes them; a token that cannot be decrypted counts as not signed in.
 */
@Singleton
class KeystoreTokenStore @Inject constructor(@ApplicationContext context: Context, private val cipher: SecretCipher) : TokenStore {
    private val prefs = context.getSharedPreferences("platform_tokens", Context.MODE_PRIVATE)

    private fun name(platform: ConnectPlatform, id: String) = "${platform.name}_$id"

    override fun get(platform: ConnectPlatform, accountId: String): StoredToken? = runCatching {
        val json = JSONObject(cipher.decrypt(prefs.getString(name(platform, accountId), null) ?: return null) ?: return null)
        StoredToken(json.getString("a"), json.optString("r").ifEmpty { null }, json.optLong("e"), json.optLong("i"))
    }.getOrNull()

    override fun put(platform: ConnectPlatform, accountId: String, token: StoredToken) {
        val json = JSONObject().put("a", token.access).put("r", token.refresh.orEmpty()).put("e", token.expiresAt).put("i", token.issuedAt)
        prefs.edit().putString(name(platform, accountId), cipher.encrypt(json.toString())).apply()
    }

    override fun remove(platform: ConnectPlatform, accountId: String) {
        prefs.edit().remove(name(platform, accountId)).apply()
    }
}
