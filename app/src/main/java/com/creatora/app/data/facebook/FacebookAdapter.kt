package com.creatora.app.data.facebook

import com.creatora.app.data.ai.HttpTransport
import com.creatora.app.data.facebook.FacebookLogin.Companion.GRAPH
import com.creatora.app.data.facebook.FacebookLogin.Companion.enc
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
import com.creatora.app.domain.platform.TokenStore
import com.creatora.app.domain.repository.ErrorReporter
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Facebook Pages the user manages: profile, posts, insights and comments, each call made with the Page's own token.
 * Facebook has no public lookup, trending or search in this API. Meta renamed many Page insight metrics in 2025, so each
 * metric is asked for on its own: one the platform refuses leaves only that figure empty. Written from Meta's
 * documentation; not yet run against a live Page.
 */
class FacebookAdapter(
    http: HttpTransport,
    errors: ErrorReporter,
    private val tokens: TokenStore,
) : PlatformAdapter by UnavailableAdapter(ConnectPlatform.FACEBOOK, NOT_PUBLIC) {

    override val platform = ConnectPlatform.FACEBOOK
    private val api = PlatformApi(http, errors, "Facebook")

    private suspend fun <T> get(pageId: String, path: String, parse: (String) -> T): AdapterOutcome<T> {
        val token = tokens.get(platform, pageId)?.access ?: return AdapterOutcome.Failed(AdapterError.NotAuthorized)
        return api.get("$GRAPH/$path${if ('?' in path) '&' else '?'}access_token=${enc(token)}", parse = parse)
    }

    override suspend fun lookup(ref: PlatformRef): AdapterOutcome<ChannelPreview> {
        val id = ref.handleOrId
        if (tokens.get(platform, id) == null) return AdapterOutcome.Failed(AdapterError.NotAvailable("Facebook only shows Pages you have signed in to"))
        return get(id, "$id?fields=id,name,about,fan_count,followers_count,picture.type(large){url}", ::parseFacebookPage)
    }

    override suspend fun recentVideos(channelId: String, limit: Int): AdapterOutcome<List<VideoStats>> {
        val posts = when (val r = get(channelId, "$channelId/posts?fields=id,message,created_time,permalink_url,full_picture,reactions.summary(true).limit(0),comments.summary(true).limit(0)&limit=${limit.coerceIn(1, 50)}", ::parseFacebookPosts)) {
            is AdapterOutcome.Ok -> r.value
            is AdapterOutcome.Failed -> return r
        }
        if (posts.isEmpty()) return AdapterOutcome.Ok(emptyList())
        // Views come from each post's insights; a post without them is left out, not shown as zero views.
        val results = coroutineScope { posts.map { p -> async { p to get(channelId, "${p.id}/insights?metric=post_media_view", ::parseFacebookInsights) } }.map { it.await() } }
        val withViews = results.mapNotNull { (p, r) -> (r as? AdapterOutcome.Ok)?.value?.get("post_media_view")?.let { p.toVideoStats(it) } }
        if (withViews.isEmpty()) (results.first().second as? AdapterOutcome.Failed)?.let { return it }
        return AdapterOutcome.Ok(withViews)
    }

    override suspend fun videoMetrics(channelId: String, videoIds: List<String>): AdapterOutcome<List<VideoAnalytics>> =
        AdapterOutcome.Ok(videoIds.map { VideoAnalytics(videoId = it) })

    override suspend fun analytics(channelId: String, videoIds: List<String>) = videoMetrics(channelId, videoIds)

    override suspend fun channelMetrics(channelId: String, startDate: LocalDate, endDate: LocalDate): AdapterOutcome<PeriodMetrics> {
        val since = startDate.atStartOfDay().toEpochSecond(ZoneOffset.UTC)
        val until = endDate.plusDays(1).atStartOfDay().toEpochSecond(ZoneOffset.UTC)
        suspend fun metric(name: String) =
            (get(channelId, "$channelId/insights?metric=$name&period=day&since=$since&until=$until", ::parseFacebookInsights) as? AdapterOutcome.Ok)?.value?.get(name)

        val views = when (val r = get(channelId, "$channelId/insights?metric=page_media_view&period=day&since=$since&until=$until", ::parseFacebookInsights)) {
            is AdapterOutcome.Ok -> r.value["page_media_view"] ?: return AdapterOutcome.Failed(AdapterError.NotAvailable("Facebook sent no views for this period"))
            is AdapterOutcome.Failed -> return r
        }
        val gained = metric("page_daily_follows_unique")
        val lost = metric("page_daily_unfollows_unique")
        val watchMillis = metric("page_video_view_time")
        return AdapterOutcome.Ok(
            PeriodMetrics(views = views, watchMinutes = watchMillis?.let { it / 60_000 }, subscribersGained = gained.takeIf { lost != null }, subscribersLost = lost.takeIf { gained != null }),
        )
    }

    /** The newest comments on one of the Page's own posts, plain text. Used by the comment inbox in Grow. */
    suspend fun comments(pageId: String, postId: String, limit: Int = 50): AdapterOutcome<List<String>> =
        get(pageId, "$postId/comments?fields=message&order=reverse_chronological&limit=${limit.coerceIn(1, 50)}", ::parseFacebookComments)

    companion object {
        const val NOT_PUBLIC = "Facebook does not share this through its API"
    }
}
