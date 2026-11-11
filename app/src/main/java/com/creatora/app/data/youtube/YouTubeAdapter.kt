package com.creatora.app.data.youtube

import com.creatora.app.data.ai.HttpTransport
import com.creatora.app.domain.account.AdapterError
import com.creatora.app.domain.account.AdapterOutcome
import com.creatora.app.domain.account.ChannelPreview
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.account.PlatformAdapter
import com.creatora.app.domain.account.PlatformRef
import com.creatora.app.domain.account.VideoAnalytics
import com.creatora.app.domain.account.VideoStats
import com.creatora.app.domain.account.YouTubeScopes
import com.creatora.app.domain.repository.AuthorizationOutcome
import com.creatora.app.domain.repository.YouTubeAuthorizer
import java.io.IOException
import java.net.URLEncoder
import java.time.LocalDate
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
) : PlatformAdapter {

    override val platform = ConnectPlatform.YOUTUBE

    private var token: String? = null
    private var tokenAt = 0L

    private suspend fun accessToken(): String? {
        token?.takeIf { System.currentTimeMillis() - tokenAt < TOKEN_MILLIS }?.let { return it }
        return when (val outcome = authorizer.authorize(YouTubeScopes.SIGN_IN)) {
            is AuthorizationOutcome.Granted -> outcome.accessToken.also { token = it; tokenAt = System.currentTimeMillis() }
            else -> null
        }
    }

    private suspend fun <T> get(url: String, parse: (String) -> T): AdapterOutcome<T> {
        val bearer = accessToken() ?: return AdapterOutcome.Failed(AdapterError.NotAuthorized)
        val response = try {
            http.get(url, mapOf("Authorization" to "Bearer $bearer", "Accept" to "application/json"))
        } catch (e: IOException) {
            return AdapterOutcome.Failed(AdapterError.Network)
        }
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

    override suspend fun analytics(channelId: String, videoIds: List<String>): AdapterOutcome<List<VideoAnalytics>> {
        if (videoIds.isEmpty()) return AdapterOutcome.Ok(emptyList())
        val range = "startDate=${LocalDate.now().minusDays(RANGE_DAYS)}&endDate=${LocalDate.now()}"
        val filter = "filters=${encode("video==" + videoIds.joinToString(","))}"
        val core = get(
            "$ANALYTICS/reports?ids=channel==MINE&$range&dimensions=video&$filter&maxResults=${videoIds.size}&sort=-views" +
                "&metrics=views,averageViewDuration,averageViewPercentage,videoThumbnailImpressions,videoThumbnailImpressionsClickRate",
            ::parseReport,
        )
        val coreRows = when (core) {
            is AdapterOutcome.Ok -> core.value
            is AdapterOutcome.Failed -> return core
        }
        // Revenue is asked for separately: it fails for channels that are not monetized, which must not hide the rest.
        val revenue = get(
            "$ANALYTICS/reports?ids=channel==MINE&$range&dimensions=video&$filter&maxResults=${videoIds.size}&metrics=estimatedRevenue",
            ::parseReport,
        )
        val revenueRows = when (revenue) {
            is AdapterOutcome.Ok -> revenue.value
            is AdapterOutcome.Failed -> emptyList()
        }
        val merged = reportToAnalytics(coreRows, revenueRows)
        val withTraffic = merged.map { analytics ->
            val traffic = get(
                "$ANALYTICS/reports?ids=channel==MINE&$range&dimensions=insightTrafficSourceType&metrics=views" +
                    "&filters=${encode("video==" + analytics.videoId)}&sort=-views",
                ::parseReport,
            )
            if (traffic is AdapterOutcome.Ok) analytics.copy(trafficSources = reportToTraffic(traffic.value)) else analytics
        }
        return AdapterOutcome.Ok(withTraffic)
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

    private fun errorMessage(body: String): String? =
        runCatching { JSONObject(body).getJSONObject("error").getString("message") }.getOrNull()

    private companion object {
        const val DATA = "https://www.googleapis.com/youtube/v3"
        const val ANALYTICS = "https://youtubeanalytics.googleapis.com/v2"
        const val RANGE_DAYS = 90L
        const val TOKEN_MILLIS = 50 * 60 * 1000L
    }
}
