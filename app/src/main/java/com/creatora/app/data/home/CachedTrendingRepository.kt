package com.creatora.app.data.home

import com.creatora.app.domain.account.AdapterOutcome
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.account.PlatformRegistry
import com.creatora.app.domain.account.VideoStats
import com.creatora.app.domain.home.categoryForNiche
import com.creatora.app.domain.home.isFresh
import com.creatora.app.domain.repository.CacheRepository
import com.creatora.app.domain.repository.TrendingRepository
import com.creatora.app.domain.repository.TrendingResult
import javax.inject.Inject
import org.json.JSONArray
import org.json.JSONObject

/**
 * Trending videos, cached in Firestore for 24 hours. The most popular chart costs 1 quota unit, a search 100, so the
 * search runs only when the niche has no matching category (or the category chart is nearly empty).
 */
class CachedTrendingRepository @Inject constructor(
    private val registry: PlatformRegistry,
    private val cache: CacheRepository,
    private val now: () -> Long,
) : TrendingRepository {

    @Inject
    constructor(registry: PlatformRegistry, cache: CacheRepository) : this(registry, cache, System::currentTimeMillis)

    override suspend fun trending(uid: String, region: String, niche: String?, force: Boolean): TrendingResult {
        val category = niche?.takeIf { it.isNotBlank() }?.let(::categoryForNiche)
        val key = "trending_${region}_${category ?: "all"}_${niche?.trim()?.lowercase().orEmpty().hashCode()}"
        val cached = cache.get(uid, key)?.let { entry -> decode(entry.text)?.let { it to entry.cachedAt } }
        if (cached != null && !force && isFresh(cached.second, TTL_MILLIS, now())) {
            return TrendingResult.Ok(cached.first, cached.second, fromCache = true)
        }

        val adapter = registry.adapter(ConnectPlatform.YOUTUBE)
        val chart = when (val result = adapter.trending(region, category)) {
            is AdapterOutcome.Ok -> result.value
            is AdapterOutcome.Failed -> return cached?.let { TrendingResult.Ok(it.first, it.second, fromCache = true, stale = true) }
                ?: TrendingResult.Failed(result.error)
        }
        val wantsSearch = !niche.isNullOrBlank() && (category == null || chart.size < MIN_CHART)
        val searched = if (wantsSearch) (adapter.searchVideos(niche!!.trim()) as? AdapterOutcome.Ok)?.value.orEmpty() else emptyList()
        val items = (searched + chart).distinctBy { it.id }.take(MAX_ITEMS)
        val at = now()
        cache.put(uid, key, encode(items), at)
        return TrendingResult.Ok(items, at, fromCache = false)
    }

    private fun encode(items: List<VideoStats>): String = JSONArray().also { array ->
        items.forEach {
            array.put(
                JSONObject().put("id", it.id).put("t", it.title).put("p", it.publishedAt).put("v", it.views)
                    .put("c", it.channelTitle).put("u", it.thumbnailUrl ?: JSONObject.NULL),
            )
        }
    }.toString()

    private fun decode(json: String): List<VideoStats>? = runCatching {
        val array = JSONArray(json)
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            VideoStats(o.getString("id"), o.getString("t"), o.getString("p"), o.getLong("v"), channelTitle = o.optString("c"), thumbnailUrl = o.optString("u").takeIf { it.isNotEmpty() && it != "null" })
        }
    }.getOrNull()

    private companion object {
        const val TTL_MILLIS = 24L * 60 * 60 * 1000
        const val MIN_CHART = 8
        const val MAX_ITEMS = 15
    }
}
