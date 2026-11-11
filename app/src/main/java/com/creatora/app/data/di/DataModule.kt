package com.creatora.app.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
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
import com.creatora.app.data.media.ContentResolverUserFiles
import com.creatora.app.data.settings.DataStoreBrandKitRepository
import com.creatora.app.data.settings.DataStoreSettingsRepository
import com.creatora.app.domain.repository.AssetRepository
import com.creatora.app.domain.repository.BrandKitRepository
import com.creatora.app.domain.repository.ChannelSnapshotRepository
import com.creatora.app.domain.repository.CharacterRepository
import com.creatora.app.domain.repository.IdeaRepository
import com.creatora.app.domain.repository.LicenceRepository
import com.creatora.app.domain.repository.ProjectRepository
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

    // Same file as the old preferencesDataStore(name = "settings"), so saved settings survive.
    @Provides
    @Singleton
    fun settingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun settings(impl: DataStoreSettingsRepository): SettingsRepository
    @Binds abstract fun brandKit(impl: DataStoreBrandKitRepository): BrandKitRepository
    @Binds abstract fun userFiles(impl: ContentResolverUserFiles): UserFiles
    @Binds abstract fun speechPlayer(impl: AndroidSpeechPlayer): SpeechPlayer
    @Binds abstract fun projects(impl: RoomProjectRepository): ProjectRepository
    @Binds abstract fun ideas(impl: RoomIdeaRepository): IdeaRepository
    @Binds abstract fun assets(impl: RoomAssetRepository): AssetRepository
    @Binds abstract fun licences(impl: RoomLicenceRepository): LicenceRepository
    @Binds abstract fun voicePresets(impl: RoomVoicePresetRepository): VoicePresetRepository
    @Binds abstract fun characters(impl: RoomCharacterRepository): CharacterRepository
    @Binds abstract fun scheduledPosts(impl: RoomScheduledPostRepository): ScheduledPostRepository
    @Binds abstract fun channelSnapshots(impl: RoomChannelSnapshotRepository): ChannelSnapshotRepository
}
