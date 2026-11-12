package com.creatora.app.data.youtube

import com.creatora.app.data.ai.HttpTransport
import com.creatora.app.domain.account.AdapterError
import com.creatora.app.domain.account.AdapterOutcome
import com.creatora.app.domain.account.ChannelPreview
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.account.PeriodMetrics
import com.creatora.app.domain.account.PlatformAdapter
import com.creatora.app.domain.account.PlatformRef
import com.creatora.app.domain.account.VideoAnalytics
import com.creatora.app.domain.account.VideoStats
import com.creatora.app.domain.account.YouTubeScopes
import com.creatora.app.domain.repository.AuthorizationOutcome
import com.creatora.app.domain.repository.ErrorReporter
import com.creatora.app.domain.repository.YouTubeAuthorizer
import java.io.IOException
import java.net.URLEncoder
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

/**
 * YouTube Data API v3 and YouTube Analytics API, called with the user's own Google access token. Nothing here is
 * verified against the live APIs yet (no Firebase project or channel was available when this was written).
 * Metric names follow Google's documentation: CTR is `videoThumbnailImpressionsClickRate`.
 */
@Singleton
class YouTubeAdapter @Inject constructor(
    private val http: HttpTransport,
    private val authorizer: YouTubeAuthorizer,
    private val errors: ErrorReporter,
) : PlatformAdapter {

    override val platform = ConnectPlatform.YOUTUBE

    private var token: String? = null
    private var tokenAt = 0L
    private var grantedScopes: Set<String> = emptySet()

    private suspend fun accessToken(): String? {
        token?.takeIf { System.currentTimeMillis() - tokenAt < TOKEN_MILLIS }?.let { return it }
        return when (val outcome = authorizer.authorize(YouTubeScopes.SIGN_IN)) {
            is AuthorizationOutcome.Granted -> outcome.accessToken.also {
                token = it
                tokenAt = System.currentTimeMillis()
                grantedScopes = outcome.grantedScopes
            }
            else -> null
        }
    }

    private suspend fun <T> get(url: String, parse: (String) -> T): AdapterOutcome<T> {
        val bearer = accessToken() ?: return AdapterOutcome.Failed(AdapterError.NotAuthorized)
        // Google lets people untick single permissions; without the Analytics one every report would fail with a 403.
        if (url.startsWith(ANALYTICS) && grantedScopes.isNotEmpty() && YouTubeScopes.ANALYTICS !in grantedScopes) {
            token = null // the next try asks Google again
            return AdapterOutcome.Failed(AdapterError.PermissionMissing)
        }
        val response = try {
            http.get(url, mapOf("Authorization" to "Bearer $bearer", "Accept" to "application/json"))
        } catch (e: IOException) {
            return AdapterOutcome.Failed(AdapterError.Network)
        }
        if (response.code !in 200..299) errors.record(YouTubeApiException(response.code, url.substringBefore('?').removePrefix("https://"), response.body))
        return when {
            response.code in 200..299 -> runCatching { AdapterOutcome.Ok(parse(response.body)) }
                .getOrElse { AdapterOutcome.Failed(AdapterError.NotAvailable("The reply could not be read")) }
            response.code == 401 -> { token = null; AdapterOutcome.Failed(AdapterError.NotAuthorized) }
            response.code == 404 -> AdapterOutcome.Failed(AdapterError.NotFound)
            response.code == 403 && response.body.contains("quota", ignoreCase = true) -> AdapterOutcome.Failed(AdapterError.QuotaExceeded)
            else -> AdapterOutcome.Failed(AdapterError.NotAvailable(errorMessage(response.body) ?: "HTTP ${response.code}"))
        }
    }

    override suspend fun ownChannels(): AdapterOutcome<List<ChannelPreview>> =
        get("$DATA/channels?part=snippet,statistics&mine=true&maxResults=50", ::parseChannels)

    override suspend fun lookup(ref: PlatformRef): AdapterOutcome<ChannelPreview> {
        val id = ref.handleOrId.trim()
        val selector = when {
            id.startsWith("UC") && id.length == 24 -> "id=$id"
            id.startsWith("@") -> "forHandle=${encode(id)}"
            else -> "forHandle=${encode("@$id")}"
        }
        return when (val result = get("$DATA/channels?part=snippet,statistics&$selector", ::parseChannels)) {
            is AdapterOutcome.Ok -> result.value.firstOrNull()?.let { AdapterOutcome.Ok(it) } ?: AdapterOutcome.Failed(AdapterError.NotFound)
            is AdapterOutcome.Failed -> result
        }
    }

    override suspend fun recentVideos(channelId: String, limit: Int): AdapterOutcome<List<VideoStats>> {
        // A channel's uploads playlist id is its channel id with "UU" instead of "UC".
        val playlist = "UU" + channelId.removePrefix("UC")
        val ids = when (val result = get("$DATA/playlistItems?part=contentDetails&playlistId=$playlist&maxResults=${limit.coerceIn(1, 50)}", ::parsePlaylistVideoIds)) {
            is AdapterOutcome.Ok -> result.value
            is AdapterOutcome.Failed -> return result
        }
        if (ids.isEmpty()) return AdapterOutcome.Ok(emptyList())
        return get("$DATA/videos?part=snippet,statistics&id=${ids.joinToString(",")}", ::parseVideos)
    }

    /** Analytics data is a day or two behind and Google counts days in Pacific time, so "today" on a phone ahead of it can be in the future. */
    private fun lastReportDay(): LocalDate = LocalDate.now(ZoneOffset.UTC).minusDays(1)

    private fun reportRange(): String = "startDate=${lastReportDay().minusDays(RANGE_DAYS)}&endDate=${lastReportDay()}"

    private suspend fun coreReport(channelId: String, videoIds: List<String>): AdapterOutcome<List<Map<String, Any?>>> {
        val filter = "filters=${encode("video==" + videoIds.joinToString(","))}"
        return get(
            "$ANALYTICS/reports?ids=channel==$channelId&${reportRange()}&dimensions=video&$filter&maxResults=${videoIds.size}&sort=-views" +
                "&metrics=views,averageViewDuration,averageViewPercentage",
            ::parseReport,
        )
    }

    /** Thumbnail click-through is asked for apart from the rest: if YouTube refuses it, views and retention still come back. */
    private suspend fun ctrByVideo(channelId: String, videoIds: List<String>): Map<Any?, Map<String, Any?>> {
        val filter = "filters=${encode("video==" + videoIds.joinToString(","))}"
        val rows = (get(
            "$ANALYTICS/reports?ids=channel==$channelId&${reportRange()}&dimensions=video&$filter&maxResults=${videoIds.size}" +
                "&metrics=videoThumbnailImpressions,videoThumbnailImpressionsClickRate",
            ::parseReport,
        ) as? AdapterOutcome.Ok)?.value.orEmpty()
        return rows.associateBy { it["video"] }
    }

    override suspend fun videoMetrics(channelId: String, videoIds: List<String>): AdapterOutcome<List<VideoAnalytics>> {
        if (videoIds.isEmpty()) return AdapterOutcome.Ok(emptyList())
        return when (val core = coreReport(channelId, videoIds)) {
            is AdapterOutcome.Ok -> {
                val ctr = ctrByVideo(channelId, videoIds)
                AdapterOutcome.Ok(reportToAnalytics(core.value.map { row -> row + (ctr[row["video"]] ?: emptyMap()) }, emptyList()))
            }
            is AdapterOutcome.Failed -> core
        }
    }

    override suspend fun analytics(channelId: String, videoIds: List<String>): AdapterOutcome<List<VideoAnalytics>> {
        if (videoIds.isEmpty()) return AdapterOutcome.Ok(emptyList())
        val coreRows = when (val core = coreReport(channelId, videoIds)) {
            is AdapterOutcome.Ok -> core.value
            is AdapterOutcome.Failed -> return core
        }
        // Revenue is asked for separately: it fails for channels that are not monetized, which must not hide the rest.
        val filter = "filters=${encode("video==" + videoIds.joinToString(","))}"
        val revenue = get(
            "$ANALYTICS/reports?ids=channel==$channelId&${reportRange()}&dimensions=video&$filter&maxResults=${videoIds.size}&metrics=estimatedRevenue",
            ::parseReport,
        )
        val revenueRows = when (revenue) {
            is AdapterOutcome.Ok -> revenue.value
            is AdapterOutcome.Failed -> emptyList()
        }
        val ctr = ctrByVideo(channelId, videoIds)
        val merged = reportToAnalytics(coreRows.map { row -> row + (ctr[row["video"]] ?: emptyMap()) }, revenueRows)
        val withTraffic = merged.map { analytics ->
            val traffic = get(
                "$ANALYTICS/reports?ids=channel==$channelId&${reportRange()}&dimensions=insightTrafficSourceType&metrics=views" +
                    "&filters=${encode("video==" + analytics.videoId)}&sort=-views",
                ::parseReport,
            )
            if (traffic is AdapterOutcome.Ok) analytics.copy(trafficSources = reportToTraffic(traffic.value)) else analytics
        }
        return AdapterOutcome.Ok(withTraffic)
    }

    override suspend fun channelMetrics(channelId: String, startDate: LocalDate, endDate: LocalDate): AdapterOutcome<PeriodMetrics> {
        val end = minOf(endDate, lastReportDay())
        val range = "startDate=${minOf(startDate, end)}&endDate=$end"
        val core = when (val result = get(
            "$ANALYTICS/reports?ids=channel==$channelId&$range&metrics=views,estimatedMinutesWatched,subscribersGained,subscribersLost",
            ::parseReport,
        )) {
            is AdapterOutcome.Ok -> result.value
            is AdapterOutcome.Failed -> return result
        }
        // Revenue is separate: refused for channels that are not monetized, which leaves it unknown (not zero).
        val revenue = (get("$ANALYTICS/reports?ids=channel==$channelId&$range&metrics=estimatedRevenue", ::parseReport) as? AdapterOutcome.Ok)?.value
        return reportToPeriodMetrics(core, revenue)?.let { AdapterOutcome.Ok(it) }
            ?: AdapterOutcome.Failed(AdapterError.NotAvailable("No data for this period"))
    }

    override suspend fun trending(region: String, categoryId: String?, limit: Int): AdapterOutcome<List<VideoStats>> {
        val category = categoryId?.let { "&videoCategoryId=$it" }.orEmpty()
        return get("$DATA/videos?part=snippet,statistics&chart=mostPopular&regionCode=${encode(region)}$category&maxResults=${limit.coerceIn(1, 50)}", ::parseVideos)
    }

    override suspend fun searchVideos(query: String, limit: Int, region: String?): AdapterOutcome<List<VideoStats>> {
        val since = java.time.Instant.now().minus(java.time.Duration.ofDays(SEARCH_DAYS)).toString()
        val ids = when (val result = get(
            "$DATA/search?part=id&type=video&order=viewCount&q=${encode(query)}&publishedAfter=${encode(since)}&maxResults=${limit.coerceIn(1, 50)}${region?.let { "&regionCode=${encode(it)}" }.orEmpty()}",
            ::parseSearchVideoIds,
        )) {
            is AdapterOutcome.Ok -> result.value
            is AdapterOutcome.Failed -> return result
        }
        if (ids.isEmpty()) return AdapterOutcome.Ok(emptyList())
        return get("$DATA/videos?part=snippet,statistics&id=${ids.joinToString(",")}", ::parseVideos)
    }

    override suspend fun keywordSearch(query: String, limit: Int): AdapterOutcome<List<VideoStats>> {
        val ids = when (val result = get(
            "$DATA/search?part=id&type=video&order=relevance&q=${encode(query)}&maxResults=${limit.coerceIn(1, 50)}",
            ::parseSearchVideoIds,
        )) {
            is AdapterOutcome.Ok -> result.value
            is AdapterOutcome.Failed -> return result
        }
        if (ids.isEmpty()) return AdapterOutcome.Ok(emptyList())
        return get("$DATA/videos?part=snippet,statistics&id=${ids.joinToString(",")}", ::parseVideos)
    }

    override suspend fun channels(ids: List<String>): AdapterOutcome<List<ChannelPreview>> {
        if (ids.isEmpty()) return AdapterOutcome.Ok(emptyList())
        return get("$DATA/channels?part=snippet,statistics&id=${ids.take(50).joinToString(",")}&maxResults=50", ::parseChannels)
    }

    override suspend fun searchChannels(query: String, limit: Int): AdapterOutcome<List<ChannelPreview>> =
        idsThenChannels("$DATA/search?part=snippet&type=channel&q=${encode(query)}&maxResults=${limit.coerceIn(1, 50)}", ::parseSearchChannelIds)

    override suspend fun featuredChannels(channelId: String): AdapterOutcome<List<ChannelPreview>> =
        idsThenChannels("$DATA/channelSections?part=contentDetails&channelId=$channelId", ::parseFeaturedChannelIds)

    private suspend fun idsThenChannels(url: String, parseIds: (String) -> List<String>): AdapterOutcome<List<ChannelPreview>> {
        val ids = when (val result = get(url, parseIds)) {
            is AdapterOutcome.Ok -> result.value
            is AdapterOutcome.Failed -> return result
        }
        return channels(ids)
    }

    override suspend fun topComments(videoId: String, limit: Int): AdapterOutcome<List<String>> =
        get("$DATA/commentThreads?part=snippet&videoId=$videoId&order=relevance&textFormat=plainText&maxResults=${limit.coerceIn(1, 100)}", ::parseComments)

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

    private fun errorMessage(body: String): String? =
        runCatching { JSONObject(body).getJSONObject("error").getString("message") }.getOrNull()

    private companion object {
        const val DATA = "https://www.googleapis.com/youtube/v3"
        const val ANALYTICS = "https://youtubeanalytics.googleapis.com/v2"
        const val RANGE_DAYS = 90L
        const val SEARCH_DAYS = 30L
        const val TOKEN_MILLIS = 50 * 60 * 1000L
    }
}

/** A non-success answer from YouTube, kept as an exception so Crashlytics shows the real status and reason. */
class YouTubeApiException(val code: Int, val path: String, body: String) : Exception("YouTube API $code at $path: ${body.take(400)}")
