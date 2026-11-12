package com.creatora.app.data.tiktok

import com.creatora.app.data.platform.PlatformApi
import com.creatora.app.data.platform.formEncode
import com.creatora.app.domain.account.AdapterOutcome
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.platform.PlatformKeyProvider
import com.creatora.app.domain.platform.StoredToken
import com.creatora.app.domain.platform.TokenStore

const val TIKTOK_API = "https://open.tiktokapis.com/v2"

/** TikTok access tokens last a day: this hands out a valid one for an account, refreshing with the refresh token when needed. */
class TikTokSession(
    private val keys: PlatformKeyProvider,
    private val tokens: TokenStore,
    private val api: PlatformApi,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun token(openId: String): String? {
        val saved = tokens.get(ConnectPlatform.TIKTOK, openId) ?: return null
        if (saved.expiresAt - now() > REFRESH_AHEAD_MILLIS) return saved.access
        val refresh = saved.refresh ?: return saved.access.takeIf { saved.expiresAt > now() }
        val app = keys.keys(ConnectPlatform.TIKTOK)
        val r = api.post(
            "$TIKTOK_API/oauth/token/",
            formEncode(mapOf("client_key" to app.appId, "client_secret" to app.secret, "grant_type" to "refresh_token", "refresh_token" to refresh)),
            mapOf("Content-Type" to "application/x-www-form-urlencoded"),
            ::parseTikTokTokens,
        )
        if (r !is AdapterOutcome.Ok) return saved.access.takeIf { saved.expiresAt > now() }
        tokens.put(ConnectPlatform.TIKTOK, openId, r.value.toStored(now(), refreshFallback = refresh))
        return r.value.access
    }

    private companion object {
        const val REFRESH_AHEAD_MILLIS = 5 * 60 * 1000L
    }
}

fun TikTokTokens.toStored(now: Long, refreshFallback: String? = null) =
    StoredToken(access, refresh ?: refreshFallback, now + accessSeconds * 1000, now)
