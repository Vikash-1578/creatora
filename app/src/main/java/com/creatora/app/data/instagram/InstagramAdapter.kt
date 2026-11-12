package com.creatora.app.data.instagram

import com.creatora.app.data.ai.HttpTransport
import com.creatora.app.data.instagram.InstagramLogin.Companion.GRAPH
import com.creatora.app.data.instagram.InstagramLogin.Companion.GRAPH_ROOT
import com.creatora.app.data.instagram.InstagramLogin.Companion.PROFILE_FIELDS
import com.creatora.app.data.instagram.InstagramLogin.Companion.enc
import com.creatora.app.data.platform.PlatformApi
import com.creatora.app.domain.account.AdapterError
import com.creatora.app.domain.account.AdapterOutcome
import com.creatora.app.domain.account.ChannelPreview
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.account.PeriodMetrics
import com.creatora.app.domain.account.PlatformAdapter
import com.creatora.app.domain.account.PlatformRef
import com.creatora.app.domain.account.UnavailableAdapter
import com.creatora.app.domain.account.VideoAnalytics
import com.creatora.app.domain.account.VideoStats
import com.creatora.app.domain.platform.StoredToken
import com.creatora.app.domain.platform.TokenStore
import com.creatora.app.domain.repository.ErrorReporter
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Instagram API with Instagram Login, for professional accounts the user signed in to. Reads the profile, media,
 * insights and comments. Instagram has no public search, trending or other-account lookup in this API, so those say so.
 * Written from Meta's documentation; not yet run against a live account.
 */
class InstagramAdapter(
    http: HttpTransport,
    errors: ErrorReporter,
    private val tokens: TokenStore,
    private val now: () -> Long = System::currentTimeMillis,
) : PlatformAdapter by UnavailableAdapter(ConnectPlatform.INSTAGRAM, NOT_PUBLIC) {

    override val platform = ConnectPlatform.INSTAGRAM
    private val api = PlatformApi(http, errors, "Instagram")

    /** The saved token for [accountId], refreshed when it is within a week of ending. Null when the user is not signed in. */
    private suspend fun token(accountId: String): String? {
        val saved = tokens.get(platform, accountId) ?: return null
        val timeLeft = saved.expiresAt - now()
        if (saved.expiresAt > 0 && timeLeft <= 0) return null
        if (saved.expiresAt > 0 && timeLeft < REFRESH_WINDOW && now() - saved.issuedAt > DAY) {
            val refreshed = api.get("$GRAPH_ROOT/refresh_access_token?grant_type=ig_refresh_token&access_token=${enc(saved.access)}", parse = ::parseInstagramLongLivedToken)
            if (refreshed is AdapterOutcome.Ok) {
                tokens.put(platform, accountId, StoredToken(refreshed.value.first, null, now() + refreshed.value.second * 1000, now()))
                return refreshed.value.first
            }
        }
        return saved.access
    }

    private suspend fun <T> get(accountId: String, path: String, parse: (String) -> T): AdapterOutcome<T> {
        val bearer = token(accountId) ?: return AdapterOutcome.Failed(AdapterError.NotAuthorized)
        val url = "$GRAPH/$path${if ('?' in path) '&' else '?'}access_token=${enc(bearer)}"
        return api.get(url, parse = parse)
    }

    override suspend fun lookup(ref: PlatformRef): AdapterOutcome<ChannelPreview> {
        val id = ref.handleOrId
        if (tokens.get(platform, id) == null) return AdapterOutcome.Failed(AdapterError.NotAvailable("Instagram only shows accounts you have signed in to"))
        return get(id, "me?fields=$PROFILE_FIELDS", ::parseInstagramProfile)
    }

    override suspend fun recentVideos(channelId: String, limit: Int): AdapterOutcome<List<VideoStats>> {
        val media = when (val r = get(channelId, "me/media?fields=id,caption,permalink,thumbnail_url,media_url,timestamp,like_count,comments_count&limit=${limit.coerceIn(1, 50)}", ::parseInstagramMedia)) {
            is AdapterOutcome.Ok -> r.value
            is AdapterOutcome.Failed -> return r
        }
        if (media.isEmpty()) return AdapterOutcome.Ok(emptyList())
        // Instagram gives views only through insights, one call per post. A post without them is left out, not shown as zero views.
        val results = coroutineScope { media.map { m -> async { m to get(channelId, "${m.id}/insights?metric=views", ::parseInstagramInsights) } }.map { it.await() } }
        val withViews = results.mapNotNull { (m, r) -> (r as? AdapterOutcome.Ok)?.value?.get("views")?.let { m.toVideoStats(it) } }
        if (withViews.isEmpty()) (results.first().second as? AdapterOutcome.Failed)?.let { return it }
        return AdapterOutcome.Ok(withViews)
    }

    /** Reach and views per post, and the average watch time for Reels (the only video figures Instagram gives). */
    override suspend fun videoMetrics(channelId: String, videoIds: List<String>): AdapterOutcome<List<VideoAnalytics>> = coroutineScope {
        val rows = videoIds.map { id ->
            async {
                val reel = (get(channelId, "$id/insights?metric=ig_reels_avg_watch_time", ::parseInstagramInsights) as? AdapterOutcome.Ok)?.value
                VideoAnalytics(videoId = id, averageViewSeconds = reel?.get("ig_reels_avg_watch_time")?.let { it / 1000.0 })
            }
        }
        AdapterOutcome.Ok(rows.map { it.await() })
    }

    override suspend fun analytics(channelId: String, videoIds: List<String>) = videoMetrics(channelId, videoIds)

    /** Views for the days given. Followers come from `follower_count` (net change, last 30 days, accounts of 100 followers or more). */
    override suspend fun channelMetrics(channelId: String, startDate: LocalDate, endDate: LocalDate): AdapterOutcome<PeriodMetrics> {
        val since = startDate.atStartOfDay().toEpochSecond(ZoneOffset.UTC)
        val until = endDate.plusDays(1).atStartOfDay().toEpochSecond(ZoneOffset.UTC)
        val views = when (val r = get(channelId, "me/insights?metric=views&period=day&metric_type=total_value&since=$since&until=$until", ::parseInstagramInsights)) {
            is AdapterOutcome.Ok -> r.value["views"] ?: return AdapterOutcome.Failed(AdapterError.NotAvailable("Instagram sent no views for this period"))
            is AdapterOutcome.Failed -> return r
        }
        val net = (get(channelId, "me/insights?metric=follower_count&period=day&since=$since&until=$until", ::parseInstagramInsights) as? AdapterOutcome.Ok)?.value?.get("follower_count")
        // Only the net change is known: shown as gained or lost, which gives the same net figure.
        return AdapterOutcome.Ok(PeriodMetrics(views = views, watchMinutes = null, subscribersGained = net?.coerceAtLeast(0), subscribersLost = net?.let { (-it).coerceAtLeast(0) }))
    }

    /** The newest comments on one of the account's own posts, plain text. Used by the comment inbox in Grow. */
    suspend fun comments(accountId: String, mediaId: String, limit: Int = 50): AdapterOutcome<List<String>> =
        get(accountId, "$mediaId/comments?fields=text&limit=${limit.coerceIn(1, 50)}", ::parseInstagramComments)

    companion object {
        const val NOT_PUBLIC = "Instagram does not share this through its API"
        private const val DAY = 24 * 60 * 60 * 1000L
        private const val REFRESH_WINDOW = 7 * DAY
    }
}
