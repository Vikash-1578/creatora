package com.creatora.app.data.tiktok

import com.creatora.app.data.ai.HttpTransport
import com.creatora.app.data.platform.PlatformApi
import com.creatora.app.domain.account.AdapterError
import com.creatora.app.domain.account.AdapterOutcome
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.platform.PlatformKeyProvider
import com.creatora.app.domain.platform.PlatformPublisher
import com.creatora.app.domain.platform.PublishRequest
import com.creatora.app.domain.platform.PublishSource
import com.creatora.app.domain.platform.PublishStatus
import com.creatora.app.domain.platform.TokenStore
import com.creatora.app.domain.repository.ErrorReporter
import org.json.JSONObject

/**
 * TikTok Content Posting API (direct post, file upload in one piece). Until TikTok audits Creatora, TikTok only allows
 * posts that are visible to the user alone; this reports that as [PublishStatus.PrivateUntilApproved]. Files over 64 MB
 * need chunked upload and are refused with a clear message. Not yet run against a live account.
 */
class TikTokPublisher(
    http: HttpTransport,
    errors: ErrorReporter,
    tokens: TokenStore,
    keys: PlatformKeyProvider,
    now: () -> Long = System::currentTimeMillis,
) : PlatformPublisher {
    override val platform = ConnectPlatform.TIKTOK
    private val api = PlatformApi(http, errors, "TikTok")
    private val session = TikTokSession(keys, tokens, api, now)

    private fun fail(message: String) = AdapterOutcome.Failed(AdapterError.NotAvailable(message))

    override suspend fun publish(request: PublishRequest): AdapterOutcome<String> {
        val bearer = session.token(request.accountId) ?: return AdapterOutcome.Failed(AdapterError.NotAuthorized)
        val file = (request.source as? PublishSource.LocalFile)?.file ?: return fail("TikTok needs the video file")
        if (!file.exists()) return fail("The video file is missing")
        if (file.length() > MAX_SINGLE_UPLOAD) return fail("This video is over 64 MB; TikTok needs it sent in parts, which Creatora does not do yet")
        val headers = mapOf("Authorization" to "Bearer $bearer", "Content-Type" to "application/json; charset=UTF-8")

        val allowed = when (val r = api.post("$TIKTOK_API/post/publish/creator_info/query/", "", headers) { privacyOptions(it) }) {
            is AdapterOutcome.Ok -> r.value
            is AdapterOutcome.Failed -> return r
        }
        // Public only when the user chose it and TikTok offers it to this account; otherwise visible to the user alone.
        val privacy = if (request.makePublic && "PUBLIC_TO_EVERYONE" in allowed) "PUBLIC_TO_EVERYONE" else "SELF_ONLY"
        val size = file.length()
        val body = JSONObject()
            .put("post_info", JSONObject().put("title", request.caption.take(MAX_CAPTION)).put("privacy_level", privacy))
            .put("source_info", JSONObject().put("source", "FILE_UPLOAD").put("video_size", size).put("chunk_size", size).put("total_chunk_count", 1))
        val started = when (val r = api.post("$TIKTOK_API/post/publish/video/init/", body.toString(), headers) { json ->
            JSONObject(json).getJSONObject("data").let { it.getString("publish_id") to it.getString("upload_url") }
        }) {
            is AdapterOutcome.Ok -> r.value
            is AdapterOutcome.Failed -> return r
        }
        val bytes = file.readBytes()
        val upload = api.put(started.second, bytes, mapOf("Content-Type" to "video/mp4", "Content-Range" to "bytes 0-${size - 1}/$size")) { it }
        if (upload is AdapterOutcome.Failed) return upload
        return AdapterOutcome.Ok(started.first)
    }

    override suspend fun status(accountId: String, remoteId: String): AdapterOutcome<PublishStatus> {
        val bearer = session.token(accountId) ?: return AdapterOutcome.Failed(AdapterError.NotAuthorized)
        val headers = mapOf("Authorization" to "Bearer $bearer", "Content-Type" to "application/json; charset=UTF-8")
        return api.post("$TIKTOK_API/post/publish/status/fetch/", JSONObject().put("publish_id", remoteId).toString(), headers) { json ->
            val data = JSONObject(json).getJSONObject("data")
            when (val state = data.optString("status")) {
                "FAILED" -> PublishStatus.Failed(data.optString("fail_reason").ifBlank { "TikTok could not post the video" })
                "PUBLISH_COMPLETE" ->
                    if ((data.optJSONArray("publicaly_available_post_id")?.length() ?: 0) > 0) PublishStatus.Live(null)
                    else PublishStatus.PrivateUntilApproved("TikTok keeps posts from apps it has not approved visible to you only")
                else -> PublishStatus.Processing(state)
            }
        }
    }

    private fun privacyOptions(json: String): List<String> {
        val options = JSONObject(json).getJSONObject("data").optJSONArray("privacy_level_options") ?: return emptyList()
        return (0 until options.length()).map { options.getString(it) }
    }

    private companion object {
        const val MAX_SINGLE_UPLOAD = 64L * 1024 * 1024
        const val MAX_CAPTION = 2200
    }
}
