package com.creatora.app.domain.repository

import com.creatora.app.domain.model.Asset
import com.creatora.app.domain.model.BrandKit
import com.creatora.app.domain.model.PickedFile
import com.creatora.app.domain.model.ChannelSnapshot
import com.creatora.app.domain.model.Character
import com.creatora.app.domain.model.Idea
import com.creatora.app.domain.model.LicenceEntry
import com.creatora.app.domain.model.Project
import com.creatora.app.domain.model.ScheduledPost
import com.creatora.app.domain.model.VoicePreset
import kotlinx.coroutines.flow.Flow

/** Basic persistence for one model type. */
interface Repository<T> {
    fun observeAll(): Flow<List<T>>
    fun observe(id: Long): Flow<T?>

    /** Inserts when the item's id is 0, otherwise updates. Returns the item's id. */
    suspend fun save(item: T): Long
    suspend fun delete(id: Long)
}

/** Newest-updated first. */
interface ProjectRepository : Repository<Project>

/** Newest first. */
interface IdeaRepository : Repository<Idea>

/** Newest first. */
interface AssetRepository : Repository<Asset>

interface LicenceRepository : Repository<LicenceEntry> {
    fun observeForAsset(assetId: Long): Flow<List<LicenceEntry>>
}

/** Sorted by name. */
interface VoicePresetRepository : Repository<VoicePreset>

/** Sorted by name. */
interface CharacterRepository : Repository<Character>

/** Soonest first. */
interface ScheduledPostRepository : Repository<ScheduledPost>

interface ChannelSnapshotRepository : Repository<ChannelSnapshot> {
    /** Oldest first, for charts. */
    fun observeForChannel(channelId: String): Flow<List<ChannelSnapshot>>
}

interface BrandKitRepository {
    val brandKit: Flow<BrandKit>
    suspend fun update(transform: (BrandKit) -> BrandKit)
}

/** Files the user picks with the system pickers. */
interface UserFiles {
    /** Copies the picked file into app storage so it stays readable. Returns null if it can't be read. */
    suspend fun import(uri: String): PickedFile?

    /** Deletes a copy made by [import]. Other URIs are ignored. */
    suspend fun discard(uri: String)
}
