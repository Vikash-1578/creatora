package com.creatora.app.testutil

import com.creatora.app.data.platform.redirectParams
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.platform.OAuthBrowser
import com.creatora.app.domain.platform.PlatformKeyProvider
import com.creatora.app.domain.platform.PlatformKeys
import com.creatora.app.domain.platform.StoredToken
import com.creatora.app.domain.platform.TokenStore
import com.creatora.app.domain.platform.missingSettings

class FakeTokenStore : TokenStore {
    val saved = mutableMapOf<Pair<ConnectPlatform, String>, StoredToken>()
    override fun get(platform: ConnectPlatform, accountId: String) = saved[platform to accountId]
    override fun put(platform: ConnectPlatform, accountId: String, token: StoredToken) { saved[platform to accountId] = token }
    override fun remove(platform: ConnectPlatform, accountId: String) { saved.remove(platform to accountId) }
}

class FakeKeys(
    override var redirectUrl: String = "https://creatora-test.web.app/oauth/",
    var all: MutableMap<ConnectPlatform, PlatformKeys> = mutableMapOf(
        ConnectPlatform.INSTAGRAM to PlatformKeys("ig-app", "ig-secret"),
        ConnectPlatform.FACEBOOK to PlatformKeys("fb-app", "fb-secret", "fb-config"),
        ConnectPlatform.TIKTOK to PlatformKeys("tt-key", "tt-secret"),
    ),
) : PlatformKeyProvider {
    override fun keys(platform: ConnectPlatform) = all[platform] ?: PlatformKeys("", "")
    override fun missing(platform: ConnectPlatform) = missingSettings(platform, keys(platform), redirectUrl)
}

/** Pretends to be the platform's sign-in page: remembers the URL it was sent to and answers with [answer]. */
class FakeBrowser(var answer: (String) -> String? = { url -> "creatora://oauth?code=the-code&state=${stateOf(url)}" }) : OAuthBrowser {
    val opened = mutableListOf<String>()
    override suspend fun authorize(url: String): String? { opened += url; return answer(url) }

    companion object {
        fun stateOf(url: String) = redirectParams(url)["state"].orEmpty()
    }
}
