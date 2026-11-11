package com.creatora.app.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProjectEntity::class,
        IdeaEntity::class,
        AssetEntity::class,
        LicenceEntryEntity::class,
        VoicePresetEntity::class,
        CharacterEntity::class,
        ScheduledPostEntity::class,
        ChannelSnapshotEntity::class,
    ],
    version = 3,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class CreatoraDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun ideaDao(): IdeaDao
    abstract fun assetDao(): AssetDao
    abstract fun licenceEntryDao(): LicenceEntryDao
    abstract fun voicePresetDao(): VoicePresetDao
    abstract fun characterDao(): CharacterDao
    abstract fun scheduledPostDao(): ScheduledPostDao
    abstract fun channelSnapshotDao(): ChannelSnapshotDao

    companion object {
        const val NAME = "creatora.db"
    }
}
