package com.creatora.app.data.platform

import com.creatora.app.BuildConfig
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.platform.PlatformKeyProvider
import com.creatora.app.domain.platform.PlatformKeys
import com.creatora.app.domain.platform.missingSettings
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The developer-app settings baked in at build time from local.properties or CI secrets (see docs/platform-setup.md).
 * Known limit: the app secrets end up inside the APK, which is fine for tester builds. Before a public release the
 * code exchange moves to a server function so the secrets stay off the phone.
 */
@Singleton
class BuildConfigPlatformKeys @Inject constructor() : PlatformKeyProvider {
    override val redirectUrl: String = BuildConfig.OAUTH_REDIRECT_URL

    override fun keys(platform: ConnectPlatform): PlatformKeys = when (platform) {
        ConnectPlatform.INSTAGRAM -> PlatformKeys(BuildConfig.INSTAGRAM_APP_ID, BuildConfig.INSTAGRAM_APP_SECRET)
        ConnectPlatform.FACEBOOK -> PlatformKeys(BuildConfig.FACEBOOK_APP_ID, BuildConfig.FACEBOOK_APP_SECRET, BuildConfig.FACEBOOK_CONFIG_ID)
        ConnectPlatform.TIKTOK -> PlatformKeys(BuildConfig.TIKTOK_CLIENT_KEY, BuildConfig.TIKTOK_CLIENT_SECRET)
        else -> PlatformKeys("", "")
    }

    override fun missing(platform: ConnectPlatform): List<String> = missingSettings(platform, keys(platform), redirectUrl)
}
