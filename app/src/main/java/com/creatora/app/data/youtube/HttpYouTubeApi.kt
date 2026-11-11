package com.creatora.app.data.youtube

import com.creatora.app.data.ai.HttpTransport
import com.creatora.app.domain.repository.YouTubeApi
import com.creatora.app.domain.repository.YouTubeKeyStore
import com.creatora.app.domain.research.ChannelQuery
import com.creatora.app.domain.research.VideoSearch
import com.creatora.app.domain.research.YouTubeException
import com.creatora.app.domain.research.YtChannel
import com.creatora.app.domain.research.YtError
import com.creatora.app.domain.research.YtOutcome
import com.creatora.app.domain.research.YtVideo
import java.io.IOException
import java.net.URLEncoder
import javax.inject.Inject
import org.json.JSONException
import org.json.JSONObject

/** YouTube Data API v3 over HTTPS. The key is sent in a header, never in the URL. */
class HttpYouTubeApi @Inject constructor(
    private val http: HttpTransport,
    private val keys: YouTubeKeyStore,
) : YouTubeApi {

    override suspend fun searchVideos(query: String): YtOutcome<VideoSearch> = attempt {
        val (total, ids) = parseSearchVideoIds(request("search", "part" to "snippet", "type" to "video", "maxResults" to "25", "q" to query))
        VideoSearch(total, if (ids.isEmpty()) emptyList() else videosById(ids))
    }

    override suspend fun channels(ids: List<String>): YtOutcome<List<YtChannel>> = attempt { channelsById(ids) }

    override suspend fun findChannel(query: ChannelQuery): YtOutcome<YtChannel?> = attempt {
        when (query) {
            is ChannelQuery.Id -> channelsById(listOf(query.id)).firstOrNull()
            is ChannelQuery.Handle -> parseChannels(request("channels", "part" to "snippet,statistics", "forHandle" to query.handle)).firstOrNull()
            is ChannelQuery.Name -> {
                val id = parseSearchChannelId(request("search", "part" to "snippet", "type" to "channel", "maxResults" to "1", "q" to query.name))
                id?.let { channelsById(listOf(it)).firstOrNull() }
            }
        }
    }

    override suspend fun video(id: String): YtOutcome<YtVideo?> = attempt { videosById(listOf(id)).firstOrNull() }

    override suspend fun trending(regionCode: String): YtOutcome<List<YtVideo>> = attempt {
        parseVideos(request("videos", "part" to "snippet,statistics", "chart" to "mostPopular", "regionCode" to regionCode, "maxResults" to "25"))
    }

    override suspend fun topVideos(channelId: String): YtOutcome<List<YtVideo>> = attempt {
        val (_, ids) = parseSearchVideoIds(request("search", "part" to "snippet", "type" to "video", "channelId" to channelId, "order" to "viewCount", "maxResults" to "5"))
        if (ids.isEmpty()) emptyList() else videosById(ids).sortedByDescending { it.views }
    }

    private suspend fun videosById(ids: List<String>): List<YtVideo> =
        parseVideos(request("videos", "part" to "snippet,statistics", "id" to ids.joinToString(",")))

    private suspend fun channelsById(ids: List<String>): List<YtChannel> =
        if (ids.isEmpty()) emptyList() else parseChannels(request("channels", "part" to "snippet,statistics", "id" to ids.joinToString(",")))

    private suspend fun request(path: String, vararg params: Pair<String, String>): JSONObject {
        val key = keys.key() ?: throw YouTubeException(YtError.NoKey)
        val query = params.joinToString("&") { (name, value) -> "$name=${URLEncoder.encode(value, "UTF-8")}" }
        val response = try {
            http.get("$BASE_URL/$path?$query", mapOf("X-Goog-Api-Key" to key))
        } catch (e: IOException) {
            throw YouTubeException(YtError.Network)
        }
        val code = response.code
        if (code in 200..299) {
            return try {
                JSONObject(response.body)
            } catch (e: JSONException) {
                throw YouTubeException(YtError.BadResponse)
            }
        }
        val body = response.body
        throw YouTubeException(
            when {
                code == 400 && ("API key not valid" in body || "keyInvalid" in body) -> YtError.InvalidKey
                code == 401 -> YtError.InvalidKey
                code == 403 && "quotaExceeded" in body -> YtError.QuotaExceeded
                code == 403 -> YtError.InvalidKey
                code == 429 -> YtError.QuotaExceeded
                code >= 500 -> YtError.Server(code)
                else -> YtError.BadRequest(body.take(ERROR_TEXT_LIMIT))
            },
        )
    }

    private suspend fun <T> attempt(block: suspend () -> T): YtOutcome<T> = try {
        YtOutcome.Ok(block())
    } catch (e: YouTubeException) {
        YtOutcome.Failed(e.error)
    }

    private companion object {
        const val BASE_URL = "https://www.googleapis.com/youtube/v3"
        const val ERROR_TEXT_LIMIT = 200
    }
}
