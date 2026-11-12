package com.creatora.app.data.di

import com.creatora.app.data.ai.HttpTransport
import com.creatora.app.data.facebook.FacebookAdapter
import com.creatora.app.data.facebook.FacebookLogin
import com.creatora.app.data.facebook.FacebookPublisher
import com.creatora.app.data.instagram.InstagramAdapter
import com.creatora.app.data.instagram.InstagramLogin
import com.creatora.app.data.instagram.InstagramPublisher
import com.creatora.app.data.platform.BuildConfigPlatformKeys
import com.creatora.app.data.platform.KeystoreTokenStore
import com.creatora.app.data.platform.OAuthBridge
import com.creatora.app.data.tiktok.TikTokAdapter
import com.creatora.app.data.tiktok.TikTokLogin
import com.creatora.app.data.tiktok.TikTokPublisher
import com.creatora.app.data.youtube.YouTubeAdapter
import com.creatora.app.domain.account.PlatformRegistry
import com.creatora.app.domain.platform.LoginRegistry
import com.creatora.app.domain.platform.OAuthBrowser
import com.creatora.app.domain.platform.PlatformKeyProvider
import com.creatora.app.domain.platform.PublisherRegistry
import com.creatora.app.domain.platform.TokenStore
import com.creatora.app.domain.repository.ErrorReporter
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlatformBindingsModule {
    @Binds abstract fun keys(impl: BuildConfigPlatformKeys): PlatformKeyProvider
    @Binds abstract fun tokens(impl: KeystoreTokenStore): TokenStore
    @Binds abstract fun browser(impl: OAuthBridge): OAuthBrowser
}

/** Instagram, Facebook Pages and TikTok: sign-in, data and publishing, all behind the same interfaces as YouTube. */
@Module
@InstallIn(SingletonComponent::class)
object PlatformModule {
    @Provides @Singleton
    fun platformRegistry(youtube: YouTubeAdapter, http: HttpTransport, errors: ErrorReporter, tokens: TokenStore, keys: PlatformKeyProvider): PlatformRegistry =
        PlatformRegistry(listOf(youtube, InstagramAdapter(http, errors, tokens), FacebookAdapter(http, errors, tokens), TikTokAdapter(http, errors, tokens, keys)))

    @Provides @Singleton
    fun loginRegistry(keys: PlatformKeyProvider, browser: OAuthBrowser, http: HttpTransport, errors: ErrorReporter, tokens: TokenStore): LoginRegistry =
        LoginRegistry(
            listOf(
                InstagramLogin(keys, browser, http, errors, tokens),
                FacebookLogin(keys, browser, http, errors, tokens),
                TikTokLogin(keys, browser, http, errors, tokens),
            ),
        )

    @Provides @Singleton
    fun publisherRegistry(http: HttpTransport, errors: ErrorReporter, tokens: TokenStore, keys: PlatformKeyProvider): PublisherRegistry =
        PublisherRegistry(listOf(InstagramPublisher(http, errors, tokens), FacebookPublisher(http, errors, tokens), TikTokPublisher(http, errors, tokens, keys)))
}
