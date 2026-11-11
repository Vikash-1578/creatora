package com.creatora.app.testutil

import com.creatora.app.domain.model.Asset
import com.creatora.app.domain.model.BrandKit
import com.creatora.app.domain.model.Character
import com.creatora.app.domain.model.LicenceEntry
import com.creatora.app.domain.model.PickedFile
import com.creatora.app.domain.model.Project
import com.creatora.app.domain.model.ScheduledPost
import com.creatora.app.domain.model.VoicePreset
import com.creatora.app.domain.repository.AssetRepository
import com.creatora.app.domain.repository.BrandKitRepository
import com.creatora.app.domain.repository.CharacterRepository
import com.creatora.app.domain.repository.LicenceRepository
import com.creatora.app.domain.repository.ProjectRepository
import com.creatora.app.domain.repository.Repository
import com.creatora.app.domain.repository.ScheduledPostRepository
import com.creatora.app.domain.repository.UserFiles
import com.creatora.app.domain.repository.VoicePresetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.map

/** In-memory [Repository]: ids start at 1, [items] holds the current rows in insertion order. */
open class FakeRepository<T>(
    private val idOf: (T) -> Long,
    private val withId: (T, Long) -> T,
) : Repository<T> {
    val items = MutableStateFlow<List<T>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<T>> = items
    override fun observe(id: Long): Flow<T?> = items.map { list -> list.firstOrNull { idOf(it) == id } }

    override suspend fun save(item: T): Long {
        val id = if (idOf(item) == 0L) nextId++ else idOf(item)
        val saved = withId(item, id)
        items.value = if (items.value.any { idOf(it) == id }) {
            items.value.map { if (idOf(it) == id) saved else it }
        } else {
            items.value + saved
        }
        return id
    }

    override suspend fun delete(id: Long) {
        items.value = items.value.filterNot { idOf(it) == id }
    }
}

class FakeProjectRepository : FakeRepository<Project>({ it.id }, { p, id -> p.copy(id = id) }), ProjectRepository
class FakeAssetRepository : FakeRepository<Asset>({ it.id }, { a, id -> a.copy(id = id) }), AssetRepository
class FakeCharacterRepository : FakeRepository<Character>({ it.id }, { c, id -> c.copy(id = id) }), CharacterRepository
class FakeVoicePresetRepository : FakeRepository<VoicePreset>({ it.id }, { v, id -> v.copy(id = id) }), VoicePresetRepository
class FakeScheduledPostRepository : FakeRepository<ScheduledPost>({ it.id }, { p, id -> p.copy(id = id) }), ScheduledPostRepository

class FakeLicenceRepository : FakeRepository<LicenceEntry>({ it.id }, { e, id -> e.copy(id = id) }), LicenceRepository {
    override fun observeForAsset(assetId: Long): Flow<List<LicenceEntry>> =
        items.map { list -> list.filter { it.assetId == assetId } }
}

class FakeBrandKitRepository : BrandKitRepository {
    override val brandKit = MutableStateFlow(BrandKit())
    override suspend fun update(transform: (BrandKit) -> BrandKit) {
        brandKit.value = transform(brandKit.value)
    }
}

/** Files that can be imported are listed in [readable] by their source URI. */
class FakeUserFiles : UserFiles {
    val readable = mutableMapOf<String, PickedFile>()
    val discarded = mutableListOf<String>()
    override suspend fun import(uri: String): PickedFile? = readable[uri]
    override suspend fun discard(uri: String) {
        discarded += uri
    }
}

class FakeSpeechPlayer : com.creatora.app.domain.repository.SpeechPlayer {
    data class Spoken(val text: String, val pitch: Float, val rate: Float)

    val spoken = mutableListOf<Spoken>()
    var available = true
    var stops = 0
    var released = false

    override suspend fun speak(text: String, pitch: Float, speechRate: Float): Boolean {
        if (available) spoken += Spoken(text, pitch, speechRate)
        return available
    }

    override fun stop() {
        stops++
    }

    override fun release() {
        released = true
    }
}

class FakeAiService(var outcome: com.creatora.app.domain.model.AiOutcome<String>) : com.creatora.app.domain.repository.AiService {
    val requests = mutableListOf<com.creatora.app.domain.model.AiRequest>()
    override suspend fun complete(request: com.creatora.app.domain.model.AiRequest): com.creatora.app.domain.model.AiOutcome<String> {
        requests += request
        return outcome
    }
}

fun okReply(text: String) = com.creatora.app.domain.model.AiOutcome.Ok(text, com.creatora.app.domain.model.AiProvider.GEMINI)

class FakeAiProviderStore : com.creatora.app.domain.repository.AiProviderStore {
    private val keys = kotlinx.coroutines.flow.MutableStateFlow<Map<com.creatora.app.domain.model.AiProvider, String>>(emptyMap())
    private val models = kotlinx.coroutines.flow.MutableStateFlow<Map<com.creatora.app.domain.model.AiProvider, String>>(emptyMap())

    override val configs = kotlinx.coroutines.flow.combine(keys, models) { k, m ->
        com.creatora.app.domain.model.AiProvider.entries.map { p ->
            com.creatora.app.domain.model.AiProviderConfig(p, p in k, m[p] ?: p.defaultModel)
        }
    }

    override suspend fun key(provider: com.creatora.app.domain.model.AiProvider) = keys.value[provider]
    override suspend fun setKey(provider: com.creatora.app.domain.model.AiProvider, key: String) {
        keys.value = keys.value + (provider to key.trim())
    }
    override suspend fun removeKey(provider: com.creatora.app.domain.model.AiProvider) {
        keys.value = keys.value - provider
    }
    override suspend fun setModel(provider: com.creatora.app.domain.model.AiProvider, model: String) {
        models.value = if (model.isBlank()) models.value - provider else models.value + (provider to model.trim())
    }
}

class FakeNarrator : com.creatora.app.domain.repository.Narrator {
    /** Seconds each sentence lasts; null makes narration fail. */
    var secondsPerSentence: Long? = 2
    val spoken = mutableListOf<Triple<String, Float, Float>>()
    var released = 0

    override suspend fun synthesize(text: String, pitch: Float, speechRate: Float): com.creatora.app.domain.render.NarrationClip? {
        spoken += Triple(text, pitch, speechRate)
        val seconds = secondsPerSentence ?: return null
        return com.creatora.app.domain.render.NarrationClip("file:///n${spoken.size}.wav", seconds * 1_000_000)
    }

    override fun release() {
        released++
    }
}

class FakeMediaProbe(var durations: Map<String, Long> = emptyMap()) : com.creatora.app.domain.repository.MediaProbe {
    override suspend fun videoDurationMs(uri: String): Long? = durations[uri]
}

class FakeVideoRenderer(var events: List<com.creatora.app.domain.render.RenderEvent>) : com.creatora.app.domain.render.VideoRenderer {
    var request: com.creatora.app.domain.render.RenderRequest? = null
    override fun render(request: com.creatora.app.domain.render.RenderRequest) = kotlinx.coroutines.flow.flow {
        this@FakeVideoRenderer.request = request
        events.forEach { emit(it) }
    }
}

class FakeMediaSaver(var succeeds: Boolean = true) : com.creatora.app.domain.repository.MediaSaver {
    val gallery = mutableListOf<Triple<String, String, String>>()
    val copies = mutableListOf<Pair<String, String>>()
    override suspend fun saveToGallery(fileUri: String, displayName: String, mimeType: String): Boolean {
        gallery += Triple(fileUri, displayName, mimeType)
        return succeeds
    }
    override suspend fun copyTo(fileUri: String, destinationUri: String): Boolean {
        copies += fileUri to destinationUri
        return succeeds
    }
}

class FakeThumbnailRenderer(var result: String? = "file:///thumb.png") : com.creatora.app.domain.repository.ThumbnailRenderer {
    val calls = mutableListOf<Triple<String?, String, String?>>()
    override suspend fun render(imageUri: String?, text: String, fallbackColor: String?): String? {
        calls += Triple(imageUri, text, fallbackColor)
        return result
    }
}

class FakeCompetitorRepository : FakeRepository<com.creatora.app.domain.research.Competitor>({ it.id }, { c, id -> c.copy(id = id) }), com.creatora.app.domain.repository.CompetitorRepository
class FakeSwipeRepository : FakeRepository<com.creatora.app.domain.research.SwipeItem>({ it.id }, { s, id -> s.copy(id = id) }), com.creatora.app.domain.repository.SwipeRepository
class FakeIdeaRepository : FakeRepository<com.creatora.app.domain.model.Idea>({ it.id }, { i, id -> i.copy(id = id) }), com.creatora.app.domain.repository.IdeaRepository

class FakeYouTubeKeyStore(initial: String? = null) : com.creatora.app.domain.repository.YouTubeKeyStore {
    private val current = kotlinx.coroutines.flow.MutableStateFlow(initial)
    override val hasKey = current.map { it != null }
    override suspend fun key() = current.value
    override suspend fun setKey(key: String) {
        current.value = key.trim()
    }
    override suspend fun removeKey() {
        current.value = null
    }
}

/** Answers from the fields; each call is recorded in [calls]. */
class FakeYouTubeApi : com.creatora.app.domain.repository.YouTubeApi {
    private fun <T> ok(value: T) = com.creatora.app.domain.research.YtOutcome.Ok(value)

    var search: com.creatora.app.domain.research.YtOutcome<com.creatora.app.domain.research.VideoSearch> =
        ok(com.creatora.app.domain.research.VideoSearch(0, emptyList()))
    var channelList: com.creatora.app.domain.research.YtOutcome<List<com.creatora.app.domain.research.YtChannel>> = ok(emptyList())
    var channel: com.creatora.app.domain.research.YtOutcome<com.creatora.app.domain.research.YtChannel?> = ok(null)
    var videoResult: com.creatora.app.domain.research.YtOutcome<com.creatora.app.domain.research.YtVideo?> = ok(null)
    var trendingList: com.creatora.app.domain.research.YtOutcome<List<com.creatora.app.domain.research.YtVideo>> = ok(emptyList())
    val calls = mutableListOf<String>()

    override suspend fun searchVideos(query: String) = search.also { calls += "search:$query" }
    override suspend fun channels(ids: List<String>) = channelList.also { calls += "channels:${ids.joinToString(",")}" }
    override suspend fun findChannel(query: com.creatora.app.domain.research.ChannelQuery) = channel.also { calls += "find:$query" }
    override suspend fun video(id: String) = videoResult.also { calls += "video:$id" }
    override suspend fun trending(regionCode: String) = trendingList.also { calls += "trending" }

    var top: com.creatora.app.domain.research.YtOutcome<List<com.creatora.app.domain.research.YtVideo>> = ok(emptyList())
    override suspend fun topVideos(channelId: String) = top.also { calls += "top:$channelId" }
}

class FakeReminderScheduler : com.creatora.app.domain.repository.ReminderScheduler {
    data class Reminder(val postId: Long, val projectId: Long, val title: String, val platform: String, val atMillis: Long)

    val scheduled = mutableListOf<Reminder>()
    val cancelled = mutableListOf<Long>()
    override fun schedule(postId: Long, projectId: Long, title: String, platformName: String, atMillis: Long) {
        scheduled += Reminder(postId, projectId, title, platformName, atMillis)
    }
    override fun cancel(postId: Long) {
        cancelled += postId
    }
}

class FakeCommentDraftRepository : FakeRepository<com.creatora.app.domain.grow.CommentDraft>({ it.id }, { d, id -> d.copy(id = id) }), com.creatora.app.domain.repository.CommentDraftRepository
class FakeAbTestRepository : FakeRepository<com.creatora.app.domain.grow.AbTest>({ it.id }, { t, id -> t.copy(id = id) }), com.creatora.app.domain.repository.AbTestRepository

class FakeSnapshotRepository : FakeRepository<com.creatora.app.domain.model.ChannelSnapshot>({ it.id }, { s, id -> s.copy(id = id) }), com.creatora.app.domain.repository.ChannelSnapshotRepository {
    override fun observeForChannel(channelId: String) = items.map { list -> list.filter { it.channelId == channelId }.sortedBy { it.capturedAt } }
}

class FakeMyChannelRepository(initial: com.creatora.app.domain.grow.MyChannel? = null) : com.creatora.app.domain.repository.MyChannelRepository {
    override val channel = MutableStateFlow(initial)
    override suspend fun set(channel: com.creatora.app.domain.grow.MyChannel) {
        this.channel.value = channel
    }
    override suspend fun clear() {
        channel.value = null
    }
}
