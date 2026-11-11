package com.creatora.app.domain.repository

import com.creatora.app.domain.model.AiOutcome
import com.creatora.app.domain.model.AiProvider
import com.creatora.app.domain.model.AiProviderConfig
import com.creatora.app.domain.model.AiRequest
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

/** On-device text-to-speech for voice previews. */
interface SpeechPlayer {
    /**
     * Speaks [text] with the given pitch and speed (1.0 is normal). Returns false if this phone has no
     * working text-to-speech engine.
     */
    suspend fun speak(text: String, pitch: Float, speechRate: Float): Boolean
    fun stop()

    /** Frees the engine. It starts again on the next [speak]. */
    fun release()
}

/** One AI service. Throws [com.creatora.app.domain.model.AiException] when the call fails. */
interface AiClient {
    val provider: AiProvider
    suspend fun complete(request: AiRequest, apiKey: String, model: String): String
}

/** Runs a request on the user's providers, moving to the next one when a provider fails. */
interface AiService {
    suspend fun complete(request: AiRequest): AiOutcome<String>
}

/** The user's API keys (encrypted on the phone) and optional model names, per provider. */
interface AiProviderStore {
    val configs: Flow<List<AiProviderConfig>>
    suspend fun key(provider: AiProvider): String?
    suspend fun setKey(provider: AiProvider, key: String)
    suspend fun removeKey(provider: AiProvider)

    /** A blank [model] goes back to the provider's default. */
    suspend fun setModel(provider: AiProvider, model: String)
}

/** Turns text into spoken audio files. */
interface Narrator {
    /** Speaks [text] into an audio file. Null if the phone has no text-to-speech voice or it failed. */
    suspend fun synthesize(text: String, pitch: Float, speechRate: Float): com.creatora.app.domain.render.NarrationClip?

    /** Deletes the audio files made so far and frees the speech engine. */
    fun release()
}

interface MediaProbe {
    /** Length of a video in milliseconds, or null if it can't be read. */
    suspend fun videoDurationMs(uri: String): Long?
}

/** Puts finished files where the user can find them. */
interface MediaSaver {
    /** Copies the file into the gallery (Movies or Pictures / Creatora). Android 10 and newer only. */
    suspend fun saveToGallery(fileUri: String, displayName: String, mimeType: String): Boolean

    /** Copies the file to a place the user picked with the system file dialog. */
    suspend fun copyTo(fileUri: String, destinationUri: String): Boolean
}

interface ThumbnailRenderer {
    /**
     * Draws a 1280 x 720 thumbnail: the image (or a plain colour) with [text] on top. Returns the PNG's
     * file:// URI, or null if the image can't be read.
     */
    suspend fun render(imageUri: String?, text: String, fallbackColor: String?): String?
}

/** The public YouTube Data API v3, called with the user's own key. */
interface YouTubeApi {
    suspend fun searchVideos(query: String): com.creatora.app.domain.research.YtOutcome<com.creatora.app.domain.research.VideoSearch>
    suspend fun channels(ids: List<String>): com.creatora.app.domain.research.YtOutcome<List<com.creatora.app.domain.research.YtChannel>>

    /** Finds one channel by id, @handle or name. Null if nothing matches. */
    suspend fun findChannel(query: com.creatora.app.domain.research.ChannelQuery): com.creatora.app.domain.research.YtOutcome<com.creatora.app.domain.research.YtChannel?>
    suspend fun video(id: String): com.creatora.app.domain.research.YtOutcome<com.creatora.app.domain.research.YtVideo?>
    suspend fun trending(regionCode: String): com.creatora.app.domain.research.YtOutcome<List<com.creatora.app.domain.research.YtVideo>>
}

/** The user's YouTube Data API key, encrypted on the phone. */
interface YouTubeKeyStore {
    val hasKey: Flow<Boolean>
    suspend fun key(): String?
    suspend fun setKey(key: String)
    suspend fun removeKey()
}

interface CompetitorRepository : Repository<com.creatora.app.domain.research.Competitor>

interface SwipeRepository : Repository<com.creatora.app.domain.research.SwipeItem>
