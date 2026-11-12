package com.creatora.app.data.tiktok

import com.creatora.app.data.ai.HttpTransport
import com.creatora.app.data.platform.AuthResult
import com.creatora.app.data.platform.OAuthFlow
import com.creatora.app.data.platform.Pkce
import com.creatora.app.data.platform.PlatformApi
import com.creatora.app.data.platform.formEncode
import com.creatora.app.data.platform.reason
import com.creatora.app.domain.account.AdapterOutcome
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.platform.LoginOutcome
import com.creatora.app.domain.platform.OAuthBrowser
import com.creatora.app.domain.platform.PlatformKeyProvider
import com.creatora.app.domain.platform.PlatformLogin
import com.creatora.app.domain.platform.TokenStore
import com.creatora.app.domain.repository.ErrorReporter
import java.net.URLEncoder

/** TikTok Login Kit (OAuth with PKCE): profile and stats, the video list and posting. */
class TikTokLogin(
    private val keys: PlatformKeyProvider,
    browser: OAuthBrowser,
    http: HttpTransport,
    errors: ErrorReporter,
    private val tokens: TokenStore,
    private val now: () -> Long = System::currentTimeMillis,
) : PlatformLogin {
    override val platform = ConnectPlatform.TIKTOK
    private val flow = OAuthFlow(browser)
    private val api = PlatformApi(http, errors, "TikTok")

    override suspend fun signIn(): LoginOutcome {
        val missing = keys.missing(platform)
        if (missing.isNotEmpty()) return LoginOutcome.NotConfigured(missing)
        val app = keys.keys(platform)
        val verifier = Pkce.randomToken(48)
        val code = when (val result = flow.authorize { state -> authorizeUrl(app.appId, keys.redirectUrl, state, Pkce.challenge(verifier)) }) {
            is AuthResult.Code -> result.code
            AuthResult.Cancelled -> return LoginOutcome.Cancelled
            is AuthResult.Denied -> return LoginOutcome.Failed(result.reason)
        }
        val issued = when (val r = api.post(
            "$TIKTOK_API/oauth/token/",
            formEncode(mapOf("client_key" to app.appId, "client_secret" to app.secret, "code" to code, "grant_type" to "authorization_code", "redirect_uri" to keys.redirectUrl, "code_verifier" to verifier)),
            mapOf("Content-Type" to "application/x-www-form-urlencoded"),
            ::parseTikTokTokens,
        )) {
            is AdapterOutcome.Ok -> r.value
            is AdapterOutcome.Failed -> return LoginOutcome.Failed(r.error.reason())
        }
        val user = when (val r = api.get("$TIKTOK_API/user/info/?fields=$USER_FIELDS", mapOf("Authorization" to "Bearer ${issued.access}"), ::parseTikTokUser)) {
            is AdapterOutcome.Ok -> r.value
            is AdapterOutcome.Failed -> return LoginOutcome.Failed(r.error.reason())
        }
        tokens.put(platform, user.id, issued.toStored(now()))
        return LoginOutcome.Signed(listOf(user))
    }

    internal companion object {
        const val USER_FIELDS = "open_id,avatar_url,display_name,bio_description,username,follower_count,following_count,likes_count,video_count"
        val SCOPES = listOf("user.info.basic", "user.info.profile", "user.info.stats", "video.list", "video.publish")

        fun authorizeUrl(clientKey: String, redirect: String, state: String, challenge: String): String {
            fun enc(v: String) = URLEncoder.encode(v, "UTF-8")
            return "https://www.tiktok.com/v2/auth/authorize/?client_key=${enc(clientKey)}&scope=${enc(SCOPES.joinToString(","))}&response_type=code" +
                "&redirect_uri=${enc(redirect)}&state=${enc(state)}&code_challenge=${enc(challenge)}&code_challenge_method=S256"
        }
    }
}
