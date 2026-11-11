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
import com.creatora.app.data.ai.DataStoreYouTubeKeyStore
import com.creatora.app.data.ai.FailoverAiService
import com.creatora.app.data.ai.HttpTransport
import com.creatora.app.data.ai.SecretCipher
import com.creatora.app.data.ai.UrlConnectionTransport
import com.creatora.app.data.local.CreatoraDatabase
import com.creatora.app.data.local.RoomAssetRepository
import com.creatora.app.data.local.RoomChannelSnapshotRepository
import com.creatora.app.data.local.RoomCompetitorRepository
import com.creatora.app.data.local.RoomSwipeRepository
import com.creatora.app.data.youtube.HttpYouTubeApi
import com.creatora.app.data.local.RoomCharacterRepository
import com.creatora.app.data.local.RoomIdeaRepository
import com.creatora.app.data.local.RoomLicenceRepository
import com.creatora.app.data.local.RoomProjectRepository
import com.creatora.app.data.local.RoomScheduledPostRepository
import com.creatora.app.data.local.RoomVoicePresetRepository
import com.creatora.app.data.media.AndroidSpeechPlayer
import com.creatora.app.data.render.AndroidMediaProbe
import com.creatora.app.data.schedule.WorkManagerReminderScheduler
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
import com.creatora.app.domain.repository.CompetitorRepository
import com.creatora.app.domain.repository.SwipeRepository
import com.creatora.app.domain.repository.YouTubeApi
import com.creatora.app.domain.repository.YouTubeKeyStore
import com.creatora.app.domain.repository.BrandKitRepository
import com.creatora.app.domain.repository.ChannelSnapshotRepository
import com.creatora.app.domain.repository.CharacterRepository
import com.creatora.app.domain.repository.IdeaRepository
import com.creatora.app.domain.repository.LicenceRepository
import com.creatora.app.domain.render.VideoRenderer
import com.creatora.app.domain.repository.MediaProbe
import com.creatora.app.domain.repository.MediaSaver
import com.creatora.app.domain.repository.Narrator
import com.creatora.app.domain.repository.ReminderScheduler
import com.creatora.app.domain.repository.ProjectRepository
import com.creatora.app.domain.repository.ThumbnailRenderer
import com.creatora.app.domain.repository.ScheduledPostRepository
import com.creatora.app.domain.repository.SettingsRepository
import com.creatora.app.domain.repository.SpeechPlayer
import com.creatora.app.domain.repository.UserFiles
import com.creatora.app.domain.repository.VoicePresetRepository
import dagger.Binds
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

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun httpTransport(impl: UrlConnectionTransport): HttpTransport
    @Binds abstract fun secretCipher(impl: AndroidKeystoreCipher): SecretCipher
    @Binds abstract fun aiProviderStore(impl: DataStoreAiProviderStore): AiProviderStore
    @Binds abstract fun aiService(impl: FailoverAiService): AiService
    @Binds abstract fun youTubeKeyStore(impl: DataStoreYouTubeKeyStore): YouTubeKeyStore
    @Binds abstract fun youTubeApi(impl: HttpYouTubeApi): YouTubeApi
    @Binds abstract fun competitors(impl: RoomCompetitorRepository): CompetitorRepository
    @Binds abstract fun swipeItems(impl: RoomSwipeRepository): SwipeRepository
    @Binds abstract fun settings(impl: DataStoreSettingsRepository): SettingsRepository
    @Binds abstract fun brandKit(impl: DataStoreBrandKitRepository): BrandKitRepository
    @Binds abstract fun userFiles(impl: ContentResolverUserFiles): UserFiles
    @Binds abstract fun speechPlayer(impl: AndroidSpeechPlayer): SpeechPlayer
    @Binds abstract fun narrator(impl: AndroidNarrator): Narrator
    @Binds abstract fun reminders(impl: WorkManagerReminderScheduler): ReminderScheduler
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
