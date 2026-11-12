package com.creatora.app.data.instagram

import com.creatora.app.data.ai.HttpTransport
import com.creatora.app.data.instagram.InstagramLogin.Companion.GRAPH
import com.creatora.app.data.instagram.InstagramLogin.Companion.enc
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
import kotlinx.coroutines.delay
import org.json.JSONObject

/**
 * Instagram content publishing for professional accounts: makes a Reel from a video at a public https link, waits for
 * Instagram to process it, then publishes. Posts only when called (the user's Publish tap). Not yet run against a live
 * account; Meta's app review is needed before it works for accounts other than the app's testers.
 */
class InstagramPublisher(
    http: HttpTransport,
    errors: ErrorReporter,
    private val tokens: TokenStore,
    private val pause: suspend (Long) -> Unit = { delay(it) },
) : PlatformPublisher {
    override val platform = ConnectPlatform.INSTAGRAM
    private val api = PlatformApi(http, errors, "Instagram")

    private fun fail(message: String) = AdapterOutcome.Failed(AdapterError.NotAvailable(message))

    override suspend fun publish(request: PublishRequest): AdapterOutcome<String> {
        val token = tokens.get(platform, request.accountId)?.access ?: return AdapterOutcome.Failed(AdapterError.NotAuthorized)
        val url = (request.source as? PublishSource.RemoteUrl)?.url ?: return fail("Instagram needs the video at a public https link")
        val form = mapOf("Content-Type" to "application/x-www-form-urlencoded")
        val container = when (val r = api.post(
            "$GRAPH/me/media",
            formEncode(mapOf("media_type" to "REELS", "video_url" to url, "caption" to request.caption, "share_to_feed" to "true", "access_token" to token)),
            form, { idOf(it) },
        )) {
            is AdapterOutcome.Ok -> r.value
            is AdapterOutcome.Failed -> return r
        }
        repeat(POLLS) {
            when (val s = api.get("$GRAPH/$container?fields=status_code,status&access_token=${enc(token)}") { JSONObject(it).optString("status_code") }) {
                is AdapterOutcome.Failed -> return s
                is AdapterOutcome.Ok -> when (s.value) {
                    "FINISHED" -> return api.post("$GRAPH/me/media_publish", formEncode(mapOf("creation_id" to container, "access_token" to token)), form, { idOf(it) })
                    "ERROR", "EXPIRED" -> return fail("Instagram could not process the video (${s.value.lowercase()})")
                }
            }
            pause(POLL_MILLIS)
        }
        return fail("Instagram is still processing the video. Nothing was posted; try again in a few minutes.")
    }

    override suspend fun status(accountId: String, remoteId: String): AdapterOutcome<PublishStatus> {
        val token = tokens.get(platform, accountId)?.access ?: return AdapterOutcome.Failed(AdapterError.NotAuthorized)
        return api.get("$GRAPH/$remoteId?fields=permalink&access_token=${enc(token)}") { PublishStatus.Live(JSONObject(it).optString("permalink").ifBlank { null }) }
    }

    private fun idOf(json: String) = JSONObject(json).getString("id")

    private companion object {
        const val POLLS = 40
        const val POLL_MILLIS = 3_000L
    }
}
