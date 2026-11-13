package com.creatora.app.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.creatora.app.data.ai.AiKeysStore
import com.creatora.app.data.ai.AndroidKeystoreCipher
import com.creatora.app.data.ai.DataStoreAiProviderStore
import com.creatora.app.data.ai.FailoverAiService
import com.creatora.app.data.ai.HttpTransport
import com.creatora.app.data.ai.SecretCipher
import com.creatora.app.data.ai.UrlConnectionTransport
import com.creatora.app.data.local.CreatoraDatabase
import com.creatora.app.data.local.RoomAssetRepository
import com.creatora.app.data.local.RoomChannelSnapshotRepository
import com.creatora.app.data.local.RoomCharacterRepository
import com.creatora.app.data.local.RoomIdeaRepository
import com.creatora.app.data.local.RoomLicenceRepository
import com.creatora.app.data.local.RoomProjectRepository
import com.creatora.app.data.local.RoomScheduledPostRepository
import com.creatora.app.data.local.RoomVoicePresetRepository
import com.creatora.app.data.media.AndroidSpeechPlayer
import com.creatora.app.data.render.AndroidMediaProbe
import com.creatora.app.data.render.AndroidMediaSaver
import com.creatora.app.data.render.AndroidNarrator
import com.creatora.app.data.render.AndroidThumbnailRenderer
import com.creatora.app.data.render.Media3VideoRenderer
import com.creatora.app.data.media.ContentResolverUserFiles
import com.creatora.app.data.settings.DataStoreBrandKitRepository
import com.creatora.app.data.settings.DataStoreSettingsRepository
import com.creatora.app.domain.repository.AiProviderStore
import com.creatora.app.domain.repository.AiService
import com.creatora.app.domain.repository.AssetRepository
import com.creatora.app.domain.repository.BrandKitRepository
import com.creatora.app.domain.repository.ChannelSnapshotRepository
import com.creatora.app.domain.repository.CharacterRepository
import com.creatora.app.domain.repository.IdeaRepository
import com.creatora.app.domain.repository.LicenceRepository
import com.creatora.app.domain.render.VideoRenderer
import com.creatora.app.domain.repository.MediaProbe
import com.creatora.app.domain.repository.MediaSaver
import com.creatora.app.domain.repository.Narrator
import com.creatora.app.domain.repository.ProjectRepository
import com.creatora.app.domain.repository.ThumbnailRenderer
import com.creatora.app.domain.repository.ScheduledPostRepository
import com.creatora.app.domain.repository.SettingsRepository
import com.creatora.app.domain.repository.SpeechPlayer
import com.creatora.app.domain.repository.UserFiles
import com.creatora.app.domain.repository.VoicePresetRepository
import dagger.Binds
import com.creatora.app.BuildConfig
import com.creatora.app.data.cloud.FirebaseAiService
import com.creatora.app.data.cloud.FirebaseAuthService
import com.creatora.app.data.cloud.FirestoreAiHistoryRepository
import com.creatora.app.data.cloud.CrashlyticsErrorReporter
import com.creatora.app.domain.repository.ErrorReporter
import com.creatora.app.data.cloud.FirestoreCacheRepository
import com.creatora.app.data.cloud.FirebaseImageGenerator
import com.creatora.app.data.create.HttpLinkReader
import com.creatora.app.domain.create.ImageGenerator
import com.creatora.app.domain.create.LinkReader
import com.creatora.app.data.cloud.FirestoreDailyUsageRepository
import com.creatora.app.domain.repository.DailyUsageRepository
import com.creatora.app.data.cloud.FirestoreCompetitorRepository
import com.creatora.app.data.cloud.FirestoreSearchLogRepository
import com.creatora.app.data.research.CachedResearchRepository
import com.creatora.app.domain.repository.CompetitorRepository
import com.creatora.app.domain.repository.SearchLogRepository
import com.creatora.app.domain.research.ResearchRepository
import com.creatora.app.data.cloud.FirestoreConnectedAccountRepository
import com.creatora.app.data.cloud.FirestoreCreatorIdeaRepository
import com.creatora.app.data.cloud.FirestoreUpcomingPostRepository
import com.creatora.app.data.home.CachedTrendingRepository
import com.creatora.app.data.home.DataStoreHomePrefs
import com.creatora.app.data.home.DataStoreNotificationInbox
import com.creatora.app.data.cloud.FirestoreGoalRepository
import com.creatora.app.data.cloud.FirestoreUsageRepository
import com.creatora.app.data.cloud.FirestoreUserRepository
import com.creatora.app.data.cloud.GoogleYouTubeAuthorizer
import com.creatora.app.data.cloud.PlayBillingRepository
import com.creatora.app.data.notify.DataStoreNotificationPrefs
import com.creatora.app.domain.ai.MeteredAiService
import com.creatora.app.domain.repository.AiHistoryRepository
import com.creatora.app.domain.repository.AuthService
import com.creatora.app.domain.repository.BillingRepository
import com.creatora.app.domain.repository.CacheRepository
import com.creatora.app.domain.repository.ConnectedAccountRepository
import com.creatora.app.domain.repository.CreatorIdeaRepository
import com.creatora.app.domain.repository.HomePrefsRepository
import com.creatora.app.domain.repository.NotificationInboxRepository
import com.creatora.app.domain.repository.TrendingRepository
import com.creatora.app.domain.repository.UpcomingPostRepository
import com.creatora.app.domain.repository.GoalRepository
import com.creatora.app.domain.repository.NotificationPrefsRepository
import com.creatora.app.domain.repository.UsageRepository
import com.creatora.app.domain.repository.UserRepository
import com.creatora.app.domain.repository.YouTubeAuthorizer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): CreatoraDatabase =
        Room.databaseBuilder(context, CreatoraDatabase::class.java, CreatoraDatabase.NAME).build()

    @Provides
    @Singleton
    @AiKeysStore
    fun aiKeysDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("ai_keys") }

    // Same file as the old preferencesDataStore(name = "settings"), so saved settings survive.
    @Provides
    @Singleton
    fun settingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }
}

/** The cloud services and the AI service chosen for this build. */
@Module
@InstallIn(SingletonComponent::class)
object CloudModule {
    /** Firebase AI Logic with plan limits; the old user-key providers only when the dev flag `devAiKeys` is on. */
    @Provides
    @Singleton
    fun aiService(
        failover: FailoverAiService,
        firebase: FirebaseAiService,
        auth: AuthService,
        users: UserRepository,
        usage: UsageRepository,
        history: AiHistoryRepository,
        settings: SettingsRepository,
        billing: BillingRepository,
        goals: GoalRepository,
    ): AiService =
        if (BuildConfig.DEV_AI_KEYS) failover else MeteredAiService(firebase, auth, users, usage, history, settings, billing, goals)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CloudBindingsModule {
    @Binds abstract fun auth(impl: FirebaseAuthService): AuthService
    @Binds abstract fun users(impl: FirestoreUserRepository): UserRepository
    @Binds abstract fun usage(impl: FirestoreUsageRepository): UsageRepository
    @Binds abstract fun dailyUsage(impl: FirestoreDailyUsageRepository): DailyUsageRepository
    @Binds abstract fun linkReader(impl: HttpLinkReader): LinkReader
    @Binds abstract fun imageGenerator(impl: FirebaseImageGenerator): ImageGenerator
    @Binds abstract fun accounts(impl: FirestoreConnectedAccountRepository): ConnectedAccountRepository
    @Binds abstract fun goals(impl: FirestoreGoalRepository): GoalRepository
    @Binds abstract fun creatorIdeas(impl: FirestoreCreatorIdeaRepository): CreatorIdeaRepository
    @Binds abstract fun upcomingPosts(impl: FirestoreUpcomingPostRepository): UpcomingPostRepository
    @Binds abstract fun cache(impl: FirestoreCacheRepository): CacheRepository
    @Binds abstract fun homePrefs(impl: DataStoreHomePrefs): HomePrefsRepository
    @Binds abstract fun notificationInbox(impl: DataStoreNotificationInbox): NotificationInboxRepository
    @Binds abstract fun trending(impl: CachedTrendingRepository): TrendingRepository
    @Binds abstract fun research(impl: CachedResearchRepository): ResearchRepository
    @Binds abstract fun competitors(impl: FirestoreCompetitorRepository): CompetitorRepository
    @Binds abstract fun searchLog(impl: FirestoreSearchLogRepository): SearchLogRepository
    @Binds abstract fun aiHistory(impl: FirestoreAiHistoryRepository): AiHistoryRepository
    @Binds abstract fun errorReporter(impl: CrashlyticsErrorReporter): ErrorReporter
    @Binds abstract fun youTubeAuthorizer(impl: GoogleYouTubeAuthorizer): YouTubeAuthorizer
    @Binds abstract fun billing(impl: PlayBillingRepository): BillingRepository
    @Binds abstract fun notificationPrefs(impl: DataStoreNotificationPrefs): NotificationPrefsRepository
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun httpTransport(impl: UrlConnectionTransport): HttpTransport
    @Binds abstract fun secretCipher(impl: AndroidKeystoreCipher): SecretCipher
    @Binds abstract fun aiProviderStore(impl: DataStoreAiProviderStore): AiProviderStore
    @Binds abstract fun settings(impl: DataStoreSettingsRepository): SettingsRepository
    @Binds abstract fun brandKit(impl: DataStoreBrandKitRepository): BrandKitRepository
    @Binds abstract fun userFiles(impl: ContentResolverUserFiles): UserFiles
    @Binds abstract fun speechPlayer(impl: AndroidSpeechPlayer): SpeechPlayer
    @Binds abstract fun narrator(impl: AndroidNarrator): Narrator
    @Binds abstract fun mediaProbe(impl: AndroidMediaProbe): MediaProbe
    @Binds abstract fun mediaSaver(impl: AndroidMediaSaver): MediaSaver
    @Binds abstract fun thumbnailRenderer(impl: AndroidThumbnailRenderer): ThumbnailRenderer
    @Binds abstract fun videoRenderer(impl: Media3VideoRenderer): VideoRenderer
    @Binds abstract fun projects(impl: RoomProjectRepository): ProjectRepository
    @Binds abstract fun ideas(impl: RoomIdeaRepository): IdeaRepository
    @Binds abstract fun assets(impl: RoomAssetRepository): AssetRepository
    @Binds abstract fun licences(impl: RoomLicenceRepository): LicenceRepository
    @Binds abstract fun voicePresets(impl: RoomVoicePresetRepository): VoicePresetRepository
    @Binds abstract fun characters(impl: RoomCharacterRepository): CharacterRepository
    @Binds abstract fun scheduledPosts(impl: RoomScheduledPostRepository): ScheduledPostRepository
    @Binds abstract fun channelSnapshots(impl: RoomChannelSnapshotRepository): ChannelSnapshotRepository
}
