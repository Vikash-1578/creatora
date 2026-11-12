package com.creatora.app.data.facebook

import com.creatora.app.data.ai.HttpTransport
import com.creatora.app.data.platform.AuthResult
import com.creatora.app.data.platform.OAuthFlow
import com.creatora.app.data.platform.PlatformApi
import com.creatora.app.data.platform.reason
import com.creatora.app.domain.account.AdapterOutcome
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.platform.LoginOutcome
import com.creatora.app.domain.platform.OAuthBrowser
import com.creatora.app.domain.platform.PlatformKeyProvider
import com.creatora.app.domain.platform.PlatformKeys
import com.creatora.app.domain.platform.PlatformLogin
import com.creatora.app.domain.platform.StoredToken
import com.creatora.app.domain.platform.TokenStore
import com.creatora.app.domain.repository.ErrorReporter
import java.net.URLEncoder

/**
 * Facebook Login for Business: the user picks which Pages to share, and Facebook hands back a token for each Page.
 * With a configuration id (FACEBOOK_CONFIG_ID) the permissions come from that configuration; without one the classic
 * permission list below is asked for. Only Pages, never personal profiles.
 */
class FacebookLogin(
    private val keys: PlatformKeyProvider,
    browser: OAuthBrowser,
    http: HttpTransport,
    errors: ErrorReporter,
    private val tokens: TokenStore,
    private val now: () -> Long = System::currentTimeMillis,
) : PlatformLogin {
    override val platform = ConnectPlatform.FACEBOOK
    private val flow = OAuthFlow(browser)
    private val api = PlatformApi(http, errors, "Facebook")

    override suspend fun signIn(): LoginOutcome {
        val missing = keys.missing(platform)
        if (missing.isNotEmpty()) return LoginOutcome.NotConfigured(missing)
        val app = keys.keys(platform)
        val code = when (val result = flow.authorize { state -> authorizeUrl(app, keys.redirectUrl, state) }) {
            is AuthResult.Code -> result.code
            AuthResult.Cancelled -> return LoginOutcome.Cancelled
            is AuthResult.Denied -> return LoginOutcome.Failed(result.reason)
        }
        val shortLived = when (val r = api.get("$GRAPH/oauth/access_token?client_id=${enc(app.appId)}&redirect_uri=${enc(keys.redirectUrl)}&client_secret=${enc(app.secret)}&code=${enc(code)}", parse = ::parseFacebookToken)) {
            is AdapterOutcome.Ok -> r.value
            is AdapterOutcome.Failed -> return LoginOutcome.Failed(r.error.reason())
        }
        // A long-lived user token makes the Page tokens it produces last until the user removes the app.
        val userToken = (api.get("$GRAPH/oauth/access_token?grant_type=fb_exchange_token&client_id=${enc(app.appId)}&client_secret=${enc(app.secret)}&fb_exchange_token=${enc(shortLived)}", parse = ::parseFacebookToken) as? AdapterOutcome.Ok)?.value ?: shortLived
        val pages = when (val r = api.get("$GRAPH/me/accounts?fields=id,name,about,fan_count,followers_count,picture.type(large){url},access_token&limit=100&access_token=${enc(userToken)}", parse = ::parseFacebookPages)) {
            is AdapterOutcome.Ok -> r.value.filter { it.token != null }
            is AdapterOutcome.Failed -> return LoginOutcome.Failed(r.error.reason())
        }
        if (pages.isEmpty()) return LoginOutcome.Failed("This Facebook account has no Pages that were shared with Creatora. Creatora connects Pages, not personal profiles.")
        pages.forEach { tokens.put(platform, it.preview.id, StoredToken(it.token!!, null, 0L, now())) }
        return LoginOutcome.Signed(pages.map { it.preview })
    }

    internal companion object {
        const val VERSION = "v23.0"
        const val GRAPH = "https://graph.facebook.com/$VERSION"
        val SCOPES = listOf("pages_show_list", "pages_read_engagement", "pages_manage_posts", "pages_read_user_content", "pages_manage_engagement", "read_insights")

        fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

        fun authorizeUrl(app: PlatformKeys, redirect: String, state: String): String {
            val base = "https://www.facebook.com/$VERSION/dialog/oauth?client_id=${enc(app.appId)}&redirect_uri=${enc(redirect)}&state=${enc(state)}&response_type=code"
            return if (app.configId.isNotBlank()) "$base&config_id=${enc(app.configId)}&override_default_response_type=true" else "$base&scope=${enc(SCOPES.joinToString(","))}"
        }
    }
}
