package com.creatora.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Operations every table supports; each DAO supplies the SQL for its own table. */
interface CrudDao<E> {
    fun observeAll(): Flow<List<E>>
    fun observe(id: Long): Flow<E?>

    /** Returns the new row id on insert, or -1 when an existing row was updated. */
    @Upsert
    suspend fun upsert(entity: E): Long
    suspend fun deleteById(id: Long)
}

@Dao
interface ProjectDao : CrudDao<ProjectEntity> {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    override fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    override fun observe(id: Long): Flow<ProjectEntity?>

    @Query("DELETE FROM projects WHERE id = :id")
    override suspend fun deleteById(id: Long)
}

@Dao
interface IdeaDao : CrudDao<IdeaEntity> {
    @Query("SELECT * FROM ideas ORDER BY createdAt DESC")
    override fun observeAll(): Flow<List<IdeaEntity>>

    @Query("SELECT * FROM ideas WHERE id = :id")
    override fun observe(id: Long): Flow<IdeaEntity?>

    @Query("DELETE FROM ideas WHERE id = :id")
    override suspend fun deleteById(id: Long)
}

@Dao
interface AssetDao : CrudDao<AssetEntity> {
    @Query("SELECT * FROM assets ORDER BY createdAt DESC")
    override fun observeAll(): Flow<List<AssetEntity>>

    @Query("SELECT * FROM assets WHERE id = :id")
    override fun observe(id: Long): Flow<AssetEntity?>

    @Query("DELETE FROM assets WHERE id = :id")
    override suspend fun deleteById(id: Long)
}

@Dao
interface LicenceEntryDao : CrudDao<LicenceEntryEntity> {
    @Query("SELECT * FROM licence_entries ORDER BY createdAt DESC")
    override fun observeAll(): Flow<List<LicenceEntryEntity>>

    @Query("SELECT * FROM licence_entries WHERE id = :id")
    override fun observe(id: Long): Flow<LicenceEntryEntity?>

    @Query("DELETE FROM licence_entries WHERE id = :id")
    override suspend fun deleteById(id: Long)

    @Query("SELECT * FROM licence_entries WHERE assetId = :assetId ORDER BY createdAt DESC")
    fun observeForAsset(assetId: Long): Flow<List<LicenceEntryEntity>>
}

@Dao
interface VoicePresetDao : CrudDao<VoicePresetEntity> {
    @Query("SELECT * FROM voice_presets ORDER BY name COLLATE NOCASE")
    override fun observeAll(): Flow<List<VoicePresetEntity>>

    @Query("SELECT * FROM voice_presets WHERE id = :id")
    override fun observe(id: Long): Flow<VoicePresetEntity?>

    @Query("DELETE FROM voice_presets WHERE id = :id")
    override suspend fun deleteById(id: Long)
}

@Dao
interface CharacterDao : CrudDao<CharacterEntity> {
    @Query("SELECT * FROM characters ORDER BY name COLLATE NOCASE")
    override fun observeAll(): Flow<List<CharacterEntity>>

    @Query("SELECT * FROM characters WHERE id = :id")
    override fun observe(id: Long): Flow<CharacterEntity?>

    @Query("DELETE FROM characters WHERE id = :id")
    override suspend fun deleteById(id: Long)
}

@Dao
interface ScheduledPostDao : CrudDao<ScheduledPostEntity> {
    @Query("SELECT * FROM scheduled_posts ORDER BY scheduledAt")
    override fun observeAll(): Flow<List<ScheduledPostEntity>>

    @Query("SELECT * FROM scheduled_posts WHERE id = :id")
    override fun observe(id: Long): Flow<ScheduledPostEntity?>

    @Query("DELETE FROM scheduled_posts WHERE id = :id")
    override suspend fun deleteById(id: Long)
}

@Dao
interface CompetitorDao : CrudDao<CompetitorEntity> {
    @Query("SELECT * FROM competitors ORDER BY title COLLATE NOCASE")
    override fun observeAll(): Flow<List<CompetitorEntity>>

    @Query("SELECT * FROM competitors WHERE id = :id")
    override fun observe(id: Long): Flow<CompetitorEntity?>

    @Query("DELETE FROM competitors WHERE id = :id")
    override suspend fun deleteById(id: Long)
}

@Dao
interface SwipeItemDao : CrudDao<SwipeItemEntity> {
    @Query("SELECT * FROM swipe_items ORDER BY createdAt DESC")
    override fun observeAll(): Flow<List<SwipeItemEntity>>

    @Query("SELECT * FROM swipe_items WHERE id = :id")
    override fun observe(id: Long): Flow<SwipeItemEntity?>

    @Query("DELETE FROM swipe_items WHERE id = :id")
    override suspend fun deleteById(id: Long)
}

@Dao
interface CommentDraftDao : CrudDao<CommentDraftEntity> {
    @Query("SELECT * FROM comment_drafts ORDER BY createdAt DESC")
    override fun observeAll(): Flow<List<CommentDraftEntity>>

    @Query("SELECT * FROM comment_drafts WHERE id = :id")
    override fun observe(id: Long): Flow<CommentDraftEntity?>

    @Query("DELETE FROM comment_drafts WHERE id = :id")
    override suspend fun deleteById(id: Long)
}

@Dao
interface AbTestDao : CrudDao<AbTestEntity> {
    @Query("SELECT * FROM ab_tests ORDER BY startedAt DESC")
    override fun observeAll(): Flow<List<AbTestEntity>>

    @Query("SELECT * FROM ab_tests WHERE id = :id")
    override fun observe(id: Long): Flow<AbTestEntity?>

    @Query("DELETE FROM ab_tests WHERE id = :id")
    override suspend fun deleteById(id: Long)
}

@Dao
interface ChannelSnapshotDao : CrudDao<ChannelSnapshotEntity> {
    @Query("SELECT * FROM channel_snapshots ORDER BY capturedAt")
    override fun observeAll(): Flow<List<ChannelSnapshotEntity>>

    @Query("SELECT * FROM channel_snapshots WHERE id = :id")
    override fun observe(id: Long): Flow<ChannelSnapshotEntity?>

    @Query("DELETE FROM channel_snapshots WHERE id = :id")
    override suspend fun deleteById(id: Long)

    @Query("SELECT * FROM channel_snapshots WHERE channelId = :channelId ORDER BY capturedAt")
    fun observeForChannel(channelId: String): Flow<List<ChannelSnapshotEntity>>
}
