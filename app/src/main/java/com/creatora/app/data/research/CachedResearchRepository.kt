package com.creatora.app.data.research

import com.creatora.app.domain.account.AdapterError
import com.creatora.app.domain.account.AdapterOutcome
import com.creatora.app.domain.account.ChannelPreview
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.account.PlatformRef
import com.creatora.app.domain.account.PlatformRegistry
import com.creatora.app.domain.account.VideoAnalytics
import com.creatora.app.domain.account.VideoStats
import com.creatora.app.domain.home.isFresh
import com.creatora.app.domain.repository.CacheRepository
import com.creatora.app.domain.research.Fetched
import com.creatora.app.domain.research.KeywordSearch
import com.creatora.app.domain.research.ResearchRepository
import javax.inject.Inject

/**
 * Research data from YouTube, cached in the user's cloud so repeated opens cost no quota (the whole app shares 10,000
 * units a day). Time to live: uploads, channel details, comments 24 h; keywords 12 h; discovery 7 days.
 */
class CachedResearchRepository(
    private val registry: PlatformRegistry,
    private val cache: CacheRepository,
    private val now: () -> Long,
) : ResearchRepository {

    @Inject
    constructor(registry: PlatformRegistry, cache: CacheRepository) : this(registry, cache, System::currentTimeMillis)

    private val youtube get() = registry.adapter(ConnectPlatform.YOUTUBE)

    /** Fresh cache wins; otherwise fetch and store; if fetching fails, an older cache is returned as stale. */
    private suspend fun <T> cached(
        uid: String, key: String, ttlMillis: Long, force: Boolean,
        encode: (T) -> String, decode: (String) -> T?, fetch: suspend () -> AdapterOutcome<T>,
    ): Fetched<T> {
        val old = cache.get(uid, key)?.let { entry -> decode(entry.text)?.let { it to entry.cachedAt } }
        if (old != null && !force && isFresh(old.second, ttlMillis, now())) return Fetched.Ok(old.first, old.second, fromCache = true)
        return when (val result = fetch()) {
            is AdapterOutcome.Ok -> {
                val at = now()
                cache.put(uid, key, encode(result.value), at)
                Fetched.Ok(result.value, at, fromCache = false)
            }
            is AdapterOutcome.Failed -> old?.let { Fetched.Ok(it.first, it.second, fromCache = true, stale = true) } ?: Fetched.Failed(result.error)
        }
    }

    override suspend fun channelVideos(uid: String, channelId: String, force: Boolean): Fetched<List<VideoStats>> =
        cached(uid, "r_videos_$channelId", DAY, force, ::encodeVideos, ::decodeVideos) { youtube.recentVideos(channelId, VIDEO_LIMIT) }

    override suspend fun peekChannelVideos(uid: String, channelId: String): List<VideoStats>? =
        cache.get(uid, "r_videos_$channelId")?.let { decodeVideos(it.text) }

    override suspend fun channelInfo(uid: String, channelId: String, force: Boolean): Fetched<ChannelPreview> =
        cached(uid, "r_channel_$channelId", DAY, force, ::encodeChannel, ::decodeChannel) {
            when (val result = youtube.channels(listOf(channelId))) {
                is AdapterOutcome.Ok -> result.value.firstOrNull()?.let { AdapterOutcome.Ok(it) } ?: AdapterOutcome.Failed(AdapterError.NotFound)
                is AdapterOutcome.Failed -> result
            }
        }

    override suspend fun discover(uid: String, niche: String, sources: List<String>, force: Boolean): Fetched<List<ChannelPreview>> {
        val key = "r_discover_${(niche.trim().lowercase() + sources.sorted().joinToString()).hashCode()}"
        return cached(uid, key, WEEK, force, ::encodeChannels, ::decodeChannels) {
            // The niche search is the only 100-unit call; featured channels are best effort (1 unit each).
            when (val searched = youtube.searchChannels(niche.trim())) {
                is AdapterOutcome.Failed -> searched
                is AdapterOutcome.Ok -> {
                    val featured = sources.take(MAX_SOURCES).flatMap { (youtube.featuredChannels(it) as? AdapterOutcome.Ok)?.value.orEmpty() }
                    AdapterOutcome.Ok((searched.value + featured).distinctBy { it.id })
                }
            }
        }
    }

    override suspend fun keyword(uid: String, query: String, force: Boolean): Fetched<KeywordSearch> =
        cached(uid, "r_keyword_${query.trim().lowercase().hashCode()}", HALF_DAY, force, ::encodeKeywordSearch, ::decodeKeywordSearch) {
            when (val found = youtube.keywordSearch(query.trim())) {
                is AdapterOutcome.Failed -> found
                is AdapterOutcome.Ok -> {
                    // Channel sizes are best effort: without them the competition estimate leaves that part out.
                    val ids = found.value.map { it.channelId }.filter { it.isNotEmpty() }.distinct()
                    val sizes = (youtube.channels(ids) as? AdapterOutcome.Ok)?.value.orEmpty().associate { it.id to it.subscribers }
                    AdapterOutcome.Ok(KeywordSearch(found.value, sizes))
                }
            }
        }

    override suspend fun comments(uid: String, videoId: String, force: Boolean): Fetched<List<String>> =
        cached(uid, "r_comments_$videoId", DAY, force, ::encodeStrings, ::decodeStrings) { youtube.topComments(videoId) }

    override suspend fun lookup(ref: PlatformRef): AdapterOutcome<ChannelPreview> = youtube.lookup(ref)

    override suspend fun ownAnalytics(channelId: String, videoId: String): VideoAnalytics? =
        (youtube.analytics(channelId, listOf(videoId)) as? AdapterOutcome.Ok)?.value?.firstOrNull()

    private companion object {
        const val HALF_DAY = 12L * 60 * 60 * 1000
        const val DAY = 24L * 60 * 60 * 1000
        const val WEEK = 7 * DAY
        const val VIDEO_LIMIT = 50
        const val MAX_SOURCES = 4
    }
}
