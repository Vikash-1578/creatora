package com.creatora.app.data.instagram

import com.creatora.app.data.ai.HttpTransport
import com.creatora.app.data.platform.AuthResult
import com.creatora.app.data.platform.OAuthFlow
import com.creatora.app.data.platform.PlatformApi
import com.creatora.app.data.platform.formEncode
import com.creatora.app.data.platform.reason
import com.creatora.app.domain.account.AdapterOutcome
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.platform.LoginOutcome
import com.creatora.app.domain.platform.OAuthBrowser
import com.creatora.app.domain.platform.PlatformKeyProvider
import com.creatora.app.domain.platform.PlatformLogin
import com.creatora.app.domain.platform.StoredToken
import com.creatora.app.domain.platform.TokenStore
import com.creatora.app.domain.repository.ErrorReporter
import java.net.URLEncoder

/** Instagram API with Instagram Login: the user signs in with the professional (Business or Creator) account itself. */
class InstagramLogin(
    private val keys: PlatformKeyProvider,
    browser: OAuthBrowser,
    http: HttpTransport,
    errors: ErrorReporter,
    private val tokens: TokenStore,
    private val now: () -> Long = System::currentTimeMillis,
) : PlatformLogin {
    override val platform = ConnectPlatform.INSTAGRAM
    private val flow = OAuthFlow(browser)
    private val api = PlatformApi(http, errors, "Instagram")

    override suspend fun signIn(): LoginOutcome {
        val missing = keys.missing(platform)
        if (missing.isNotEmpty()) return LoginOutcome.NotConfigured(missing)
        val app = keys.keys(platform)
        val code = when (val result = flow.authorize { state -> authorizeUrl(app.appId, keys.redirectUrl, state) }) {
            is AuthResult.Code -> result.code
            AuthResult.Cancelled -> return LoginOutcome.Cancelled
            is AuthResult.Denied -> return LoginOutcome.Failed(result.reason)
        }
        val shortLived = when (val r = api.post(
            TOKEN_URL,
            formEncode(mapOf("client_id" to app.appId, "client_secret" to app.secret, "grant_type" to "authorization_code", "redirect_uri" to keys.redirectUrl, "code" to code)),
            mapOf("Content-Type" to "application/x-www-form-urlencoded"),
            ::parseInstagramShortLivedToken,
        )) {
            is AdapterOutcome.Ok -> r.value
            is AdapterOutcome.Failed -> return LoginOutcome.Failed(r.error.reason())
        }
        // The first token lasts an hour; trade it for one that lasts 60 days (and is refreshed when used).
        val exchanged = api.get("$GRAPH_ROOT/access_token?grant_type=ig_exchange_token&client_secret=${enc(app.secret)}&access_token=${enc(shortLived)}", parse = ::parseInstagramLongLivedToken)
        val token = when (exchanged) {
            is AdapterOutcome.Ok -> StoredToken(exchanged.value.first, null, now() + exchanged.value.second * 1000, now())
            is AdapterOutcome.Failed -> StoredToken(shortLived, null, now() + SHORT_LIFE_MILLIS, now())
        }
        val profileJson = when (val r = api.get("$GRAPH/me?fields=$PROFILE_FIELDS&access_token=${enc(token.access)}") { it }) {
            is AdapterOutcome.Ok -> r.value
            is AdapterOutcome.Failed -> return LoginOutcome.Failed(r.error.reason())
        }
        val preview = runCatching { parseInstagramProfile(profileJson) }.getOrElse { return LoginOutcome.Failed("Instagram sent an account Creatora could not read.") }
        tokens.put(platform, preview.id, token)
        return LoginOutcome.Signed(listOf(preview))
    }

    internal companion object {
        const val TOKEN_URL = "https://api.instagram.com/oauth/access_token"
        const val GRAPH_ROOT = "https://graph.instagram.com"
        const val GRAPH = "$GRAPH_ROOT/v23.0"
        const val SHORT_LIFE_MILLIS = 60 * 60 * 1000L
        const val PROFILE_FIELDS = "id,user_id,username,name,biography,profile_picture_url,followers_count,follows_count,media_count,account_type"
        val SCOPES = listOf("instagram_business_basic", "instagram_business_content_publish", "instagram_business_manage_comments", "instagram_business_manage_insights")

        fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

        fun authorizeUrl(appId: String, redirect: String, state: String) =
            "https://www.instagram.com/oauth/authorize?client_id=${enc(appId)}&redirect_uri=${enc(redirect)}&response_type=code&scope=${enc(SCOPES.joinToString(","))}&state=${enc(state)}"
    }
}