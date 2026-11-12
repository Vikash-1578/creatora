package com.creatora.app.data.tiktok

import com.creatora.app.data.platform.epochSecondsToIso
import com.creatora.app.data.platform.firstLine
import com.creatora.app.data.platform.hashtagsOf
import com.creatora.app.data.platform.longOrNull
import com.creatora.app.data.platform.stringOrNull
import com.creatora.app.domain.account.ChannelPreview
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.account.VideoStats
import org.json.JSONObject

/** `user/info`: the signed-in account. Counts are null when the `user.info.stats` permission was not granted. */
fun parseTikTokUser(json: String): ChannelPreview {
    val user = JSONObject(json).getJSONObject("data").getJSONObject("user")
    val username = user.stringOrNull("username")
    return ChannelPreview(
        platform = ConnectPlatform.TIKTOK,
        id = user.getString("open_id"),
        title = user.stringOrNull("display_name") ?: username ?: user.getString("open_id"),
        handle = username?.let { "@$it" },
        description = user.optString("bio_description"),
        photoUrl = user.stringOrNull("avatar_url"),
        subscribers = user.longOrNull("follower_count"),
        videoCount = user.longOrNull("video_count"),
        viewCount = null,
    )
}

/** `video/list`: the account's videos with their totals so far. A video without a view count is left out, not shown as zero. */
fun parseTikTokVideos(json: String): List<VideoStats> {
    val videos = JSONObject(json).optJSONObject("data")?.optJSONArray("videos") ?: return emptyList()
    return (0 until videos.length()).mapNotNull { i ->
        val v = videos.getJSONObject(i)
        val views = v.longOrNull("view_count") ?: return@mapNotNull null
        val text = v.stringOrNull("title") ?: v.stringOrNull("video_description")
        VideoStats(
            id = v.getString("id"), title = firstLine(text), publishedAt = epochSecondsToIso(v.optLong("create_time")), views = views,
            likes = v.longOrNull("like_count"), comments = v.longOrNull("comment_count"), thumbnailUrl = v.stringOrNull("cover_image_url"),
            tags = hashtagsOf(text),
        )
    }
}

/** `oauth/token`: the tokens and when they end. [accessSeconds] is how long the access token lasts. */
data class TikTokTokens(val access: String, val refresh: String?, val accessSeconds: Long, val openId: String?)

fun parseTikTokTokens(json: String): TikTokTokens = JSONObject(json).let {
    TikTokTokens(it.getString("access_token"), it.stringOrNull("refresh_token"), it.optLong("expires_in", 86_400), it.stringOrNull("open_id"))
}
