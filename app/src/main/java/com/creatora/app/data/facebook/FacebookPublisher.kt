package com.creatora.app.data.facebook

import com.creatora.app.data.ai.HttpTransport
import com.creatora.app.data.facebook.FacebookLogin.Companion.GRAPH
import com.creatora.app.data.facebook.FacebookLogin.Companion.enc
import com.creatora.app.data.platform.PlatformApi
import com.creatora.app.data.platform.formEncode
import com.creatora.app.domain.account.AdapterError
import com.creatora.app.domain.account.AdapterOutcome
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.platform.PlatformPublisher
import com.creatora.app.domain.platform.PublishRequest
import com.creatora.app.domain.platform.PublishSource
import com.creatora.app.domain.platform.PublishStatus
import com.creatora.app.domain.platform.TokenStore
import com.creatora.app.domain.repository.ErrorReporter
import org.json.JSONObject

/**
 * Posts to a Facebook Page the user signed in to: a video from a public https link, or a text post. Facebook schedules
 * natively, so [PublishRequest.scheduledAtMillis] (between 10 minutes and 30 days ahead) is passed on. Needs the
 * `pages_manage_posts` permission, which Meta's app review grants. Not yet run against a live Page.
 */
class FacebookPublisher(http: HttpTransport, errors: ErrorReporter, private val tokens: TokenStore) : PlatformPublisher {
    override val platform = ConnectPlatform.FACEBOOK
    private val api = PlatformApi(http, errors, "Facebook")

    override suspend fun publish(request: PublishRequest): AdapterOutcome<String> {
        val token = tokens.get(platform, request.accountId)?.access ?: return AdapterOutcome.Failed(AdapterError.NotAuthorized)
        val form = mapOf("Content-Type" to "application/x-www-form-urlencoded")
        val schedule = request.scheduledAtMillis?.let { mapOf("published" to "false", "scheduled_publish_time" to (it / 1000).toString()) }.orEmpty()
        return when (val source = request.source) {
            null -> api.post("$GRAPH/${request.accountId}/feed", formEncode(mapOf("message" to request.caption, "access_token" to token) + schedule), form, { idOf(it) })
            is PublishSource.RemoteUrl -> api.post("$GRAPH/${request.accountId}/videos", formEncode(mapOf("file_url" to source.url, "description" to request.caption, "access_token" to token) + schedule), form, { idOf(it) })
            is PublishSource.LocalFile -> AdapterOutcome.Failed(AdapterError.NotAvailable("Facebook needs the video at a public https link"))
        }
    }

    override suspend fun status(accountId: String, remoteId: String): AdapterOutcome<PublishStatus> {
        val token = tokens.get(platform, accountId)?.access ?: return AdapterOutcome.Failed(AdapterError.NotAuthorized)
        return api.get("$GRAPH/$remoteId?fields=status,permalink_url&access_token=${enc(token)}") { json ->
            val o = JSONObject(json)
            val state = o.optJSONObject("status")?.optString("video_status").orEmpty()
            when (state) {
                "error" -> PublishStatus.Failed("Facebook could not process the video")
                "processing", "upload_complete" -> PublishStatus.Processing(state)
                else -> PublishStatus.Live(o.optString("permalink_url").ifBlank { null })
            }
        }
    }

    private fun idOf(json: String) = JSONObject(json).getString("id")
}
