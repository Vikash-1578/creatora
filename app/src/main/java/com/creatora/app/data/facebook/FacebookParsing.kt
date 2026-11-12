package com.creatora.app.data.facebook

import com.creatora.app.data.platform.firstLine
import com.creatora.app.data.platform.hashtagsOf
import com.creatora.app.data.platform.longOrNull
import com.creatora.app.data.platform.metaTimeToIso
import com.creatora.app.data.platform.stringOrNull
import com.creatora.app.domain.account.ChannelPreview
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.account.VideoStats
import org.json.JSONObject

/** A Page the user manages and the token Facebook gave for acting as that Page. */
data class FacebookPage(val preview: ChannelPreview, val token: String?)

private fun pageOf(o: JSONObject) = ChannelPreview(
    platform = ConnectPlatform.FACEBOOK,
    id = o.getString("id"),
    title = o.stringOrNull("name") ?: o.getString("id"),
    description = o.optString("about"),
    photoUrl = o.optJSONObject("picture")?.optJSONObject("data")?.stringOrNull("url"),
    // followers_count is the current figure; fan_count (likes) is the older one.
    subscribers = o.longOrNull("followers_count") ?: o.longOrNull("fan_count"),
)

/** `me/accounts`: every Page the user manages. */
fun parseFacebookPages(json: String): List<FacebookPage> {
    val data = JSONObject(json).optJSONArray("data") ?: return emptyList()
    return (0 until data.length()).map { data.getJSONObject(it).let { o -> FacebookPage(pageOf(o), o.stringOrNull("access_token")) } }
}

fun parseFacebookPage(json: String): ChannelPreview = pageOf(JSONObject(json))

data class FacebookPost(val id: String, val message: String?, val publishedAt: String, val permalink: String?, val picture: String?, val reactions: Long?, val comments: Long?)

fun parseFacebookPosts(json: String): List<FacebookPost> {
    val data = JSONObject(json).optJSONArray("data") ?: return emptyList()
    return (0 until data.length()).mapNotNull { i ->
        val o = data.getJSONObject(i)
        val time = metaTimeToIso(o.stringOrNull("created_time")) ?: return@mapNotNull null
        FacebookPost(
            id = o.getString("id"), message = o.stringOrNull("message"), publishedAt = time, permalink = o.stringOrNull("permalink_url"),
            picture = o.stringOrNull("full_picture"),
            reactions = o.optJSONObject("reactions")?.optJSONObject("summary")?.longOrNull("total_count"),
            comments = o.optJSONObject("comments")?.optJSONObject("summary")?.longOrNull("total_count"),
        )
    }
}

fun FacebookPost.toVideoStats(views: Long) = VideoStats(
    id = id, title = firstLine(message), publishedAt = publishedAt, views = views, likes = reactions, comments = comments,
    thumbnailUrl = picture, tags = hashtagsOf(message),
)

/** `insights`: each metric's values added up over the days asked for. Metrics Facebook left out are absent, never zero. */
fun parseFacebookInsights(json: String): Map<String, Long> {
    val data = JSONObject(json).optJSONArray("data") ?: return emptyMap()
    val result = mutableMapOf<String, Long>()
    for (i in 0 until data.length()) {
        val metric = data.getJSONObject(i)
        val values = metric.optJSONArray("values") ?: continue
        if (values.length() == 0) continue
        // A value is a number, or (for breakdown metrics) an object we cannot add up.
        result[metric.getString("name")] = (0 until values.length()).sumOf { (values.getJSONObject(it).opt("value") as? Number)?.toLong() ?: 0L }
    }
    return result
}

fun parseFacebookComments(json: String): List<String> {
    val data = JSONObject(json).optJSONArray("data") ?: return emptyList()
    return (0 until data.length()).mapNotNull { data.getJSONObject(it).stringOrNull("message") }
}

fun parseFacebookToken(json: String): String = JSONObject(json).getString("access_token")
