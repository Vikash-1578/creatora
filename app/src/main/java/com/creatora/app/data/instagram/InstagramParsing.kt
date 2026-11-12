package com.creatora.app.data.instagram

import com.creatora.app.data.platform.firstLine
import com.creatora.app.data.platform.hashtagsOf
import com.creatora.app.data.platform.longOrNull
import com.creatora.app.data.platform.metaTimeToIso
import com.creatora.app.data.platform.stringOrNull
import com.creatora.app.domain.account.ChannelPreview
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.account.VideoStats
import org.json.JSONObject

/** `/me`: the professional account behind the sign-in. [followers_count] and the rest are null when the platform hides them. */
fun parseInstagramProfile(json: String): ChannelPreview {
    val o = JSONObject(json)
    val username = o.stringOrNull("username")
    return ChannelPreview(
        platform = ConnectPlatform.INSTAGRAM,
        id = o.stringOrNull("user_id") ?: o.getString("id"),
        title = o.stringOrNull("name") ?: username ?: o.getString("id"),
        handle = username?.let { "@$it" },
        description = o.optString("biography"),
        photoUrl = o.stringOrNull("profile_picture_url"),
        subscribers = o.longOrNull("followers_count"),
        videoCount = o.longOrNull("media_count"),
    )
}

/** The type Instagram reports for the account ("BUSINESS", "MEDIA_CREATOR"); personal accounts cannot use this API. */
fun parseInstagramAccountType(json: String): String? = JSONObject(json).stringOrNull("account_type")

data class InstagramMedia(
    val id: String,
    val caption: String?,
    val permalink: String?,
    val thumbnailUrl: String?,
    val publishedAt: String,
    val likes: Long?,
    val comments: Long?,
)

fun parseInstagramMedia(json: String): List<InstagramMedia> {
    val data = JSONObject(json).optJSONArray("data") ?: return emptyList()
    return (0 until data.length()).mapNotNull { i ->
        val o = data.getJSONObject(i)
        val time = metaTimeToIso(o.stringOrNull("timestamp")) ?: return@mapNotNull null
        InstagramMedia(
            id = o.getString("id"),
            caption = o.stringOrNull("caption"),
            permalink = o.stringOrNull("permalink"),
            // Videos have a thumbnail_url; photos only a media_url.
            thumbnailUrl = o.stringOrNull("thumbnail_url") ?: o.stringOrNull("media_url"),
            publishedAt = time,
            likes = o.longOrNull("like_count"),
            comments = o.longOrNull("comments_count"),
        )
    }
}

fun InstagramMedia.toVideoStats(views: Long) = VideoStats(
    id = id, title = firstLine(caption), publishedAt = publishedAt, views = views, likes = likes, comments = comments,
    thumbnailUrl = thumbnailUrl, tags = hashtagsOf(caption),
)

/**
 * `insights`: each metric's number. Totals (`total_value`) give one value; a day-by-day series is added up. Metrics the
 * platform left out are absent from the result, never zero.
 */
fun parseInstagramInsights(json: String): Map<String, Long> {
    val data = JSONObject(json).optJSONArray("data") ?: return emptyMap()
    val result = mutableMapOf<String, Long>()
    for (i in 0 until data.length()) {
        val metric = data.getJSONObject(i)
        val name = metric.getString("name")
        val total = metric.optJSONObject("total_value")?.longOrNull("value")
        val series = metric.optJSONArray("values")?.let { values -> (0 until values.length()).sumOf { values.getJSONObject(it).optLong("value") } }
        (total ?: series)?.let { result[name] = it }
    }
    return result
}

fun parseInstagramComments(json: String): List<String> {
    val data = JSONObject(json).optJSONArray("data") ?: return emptyList()
    return (0 until data.length()).mapNotNull { data.getJSONObject(it).stringOrNull("text") }
}

/** `{"data":[{"access_token":..}]}` (current) or `{"access_token":..}` (older). */
fun parseInstagramShortLivedToken(json: String): String {
    val root = JSONObject(json)
    return root.optJSONArray("data")?.getJSONObject(0)?.getString("access_token") ?: root.getString("access_token")
}

/** The token and the seconds it lasts. */
fun parseInstagramLongLivedToken(json: String): Pair<String, Long> = JSONObject(json).let { it.getString("access_token") to it.getLong("expires_in") }
