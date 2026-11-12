package com.creatora.app.data.tiktok

import com.creatora.app.data.ai.HttpTransport
import com.creatora.app.data.platform.PlatformApi
import com.creatora.app.data.tiktok.TikTokLogin.Companion.USER_FIELDS
import com.creatora.app.domain.account.AdapterError
import com.creatora.app.domain.account.AdapterOutcome
import com.creatora.app.domain.account.ChannelPreview
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.account.PlatformAdapter
import com.creatora.app.domain.account.PlatformRef
import com.creatora.app.domain.account.UnavailableAdapter
import com.creatora.app.domain.account.VideoAnalytics
import com.creatora.app.domain.account.VideoStats
import com.creatora.app.domain.platform.PlatformKeyProvider
import com.creatora.app.domain.platform.TokenStore
import com.creatora.app.domain.repository.ErrorReporter
import java.time.LocalDate
import org.json.JSONObject

/**
 * TikTok Display API for the account the user signed in to: profile and stats, and the video list with each video's
 * totals so far. TikTok gives no account analytics over time, no comments and no public lookup, so those say so.
 * Written from TikTok's documentation; not yet run against a live account.
 */
class TikTokAdapter(
    http: HttpTransport,
    errors: ErrorReporter,
    private val tokens: TokenStore,
    keys: PlatformKeyProvider,
    now: () -> Long = System::currentTimeMillis,
) : PlatformAdapter by UnavailableAdapter(ConnectPlatform.TIKTOK, NOT_PUBLIC) {

    override val platform = ConnectPlatform.TIKTOK
    private val api = PlatformApi(http, errors, "TikTok")
    private val session = TikTokSession(keys, tokens, api, now)

    override suspend fun lookup(ref: PlatformRef): AdapterOutcome<ChannelPreview> {
        val bearer = session.token(ref.handleOrId) ?: return AdapterOutcome.Failed(AdapterError.NotAvailable("TikTok only shows accounts you have signed in to"))
        return api.get("$TIKTOK_API/user/info/?fields=$USER_FIELDS", mapOf("Authorization" to "Bearer $bearer"), ::parseTikTokUser)
    }

    override suspend fun recentVideos(channelId: String, limit: Int): AdapterOutcome<List<VideoStats>> {
        val bearer = session.token(channelId) ?: return AdapterOutcome.Failed(AdapterError.NotAuthorized)
        return api.post(
            "$TIKTOK_API/video/list/?fields=id,title,video_description,create_time,cover_image_url,share_url,view_count,like_count,comment_count,share_count,duration",
            JSONObject().put("max_count", limit.coerceIn(1, 20)).toString(),
            mapOf("Authorization" to "Bearer $bearer", "Content-Type" to "application/json"),
            ::parseTikTokVideos,
        )
    }

    override suspend fun videoMetrics(channelId: String, videoIds: List<String>): AdapterOutcome<List<VideoAnalytics>> =
        AdapterOutcome.Ok(videoIds.map { VideoAnalytics(videoId = it) })

    override suspend fun analytics(channelId: String, videoIds: List<String>) = videoMetrics(channelId, videoIds)

    override suspend fun channelMetrics(channelId: String, startDate: LocalDate, endDate: LocalDate): AdapterOutcome<com.creatora.app.domain.account.PeriodMetrics> =
        AdapterOutcome.Failed(AdapterError.NotAvailable("TikTok does not share account analytics over time through its API"))

    companion object {
        const val NOT_PUBLIC = "TikTok does not share this through its API"
    }
}
