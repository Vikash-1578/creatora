package com.creatora.app.data.youtube

import com.creatora.app.data.ai.HttpTransport
import com.creatora.app.data.platform.PlatformApi
import com.creatora.app.domain.account.AdapterError
import com.creatora.app.domain.account.AdapterOutcome
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.account.YouTubeScopes
import com.creatora.app.domain.platform.PlatformPublisher
import com.creatora.app.domain.platform.PublishRequest
import com.creatora.app.domain.platform.PublishSource
import com.creatora.app.domain.platform.PublishStatus
import com.creatora.app.domain.repository.AuthorizationOutcome
import com.creatora.app.domain.repository.ErrorReporter
import com.creatora.app.domain.repository.YouTubeAuthorizer
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

/**
 * Posts to YouTube with the Data API: a resumable upload (`videos.insert`, 1,600 quota units), the thumbnail
 * (`thumbnails.set`) and `status.publishAt` for scheduling, which YouTube honours even with the app closed.
 * Until Google audits Creatora's API project, **uploaded videos are locked to private**: [status] reports that as
 * [PublishStatus.PrivateUntilApproved] and the screen offers "Open in YouTube to make public". Not yet run against a
 * live channel.
 */
class YouTubeUploader(
    http: HttpTransport,
    private val authorizer: YouTubeAuthorizer,
    errors: ErrorReporter,
) : PlatformPublisher {
    override val platform = ConnectPlatform.YOUTUBE
    private val api = PlatformApi(http, errors, "YouTube")

    /** Asks Google for the upload permission (shows its screen the first time). Call from the screen, never from a background job. */
    suspend fun ensureUploadAccess(): Boolean =
        (authorizer.authorize(listOf(YouTubeScopes.UPLOAD)) as? AuthorizationOutcome.Granted)?.let { YouTubeScopes.UPLOAD in it.grantedScopes || it.grantedScopes.isEmpty() } ?: false

    private suspend fun token(vararg scopes: String): String? = (authorizer.authorize(scopes.toList()) as? AuthorizationOutcome.Granted)?.accessToken

    private fun fail(message: String) = AdapterOutcome.Failed(AdapterError.NotAvailable(message))

    override suspend fun publish(request: PublishRequest): AdapterOutcome<String> {
        val file = (request.source as? PublishSource.LocalFile)?.file ?: return fail("YouTube needs the video file")
        if (!file.exists()) return fail("The video file is missing")
        val bearer = token(YouTubeScopes.UPLOAD) ?: return AdapterOutcome.Failed(AdapterError.NotAuthorized)
        val details = request.details
        // Only an explicit answer is sent: the two questions are required before this runs.
        val kids = details.madeForKids ?: return fail("Answer \"Made for kids\" first")
        val synthetic = details.aiContent ?: return fail("Answer the AI-content question first")

        val status = JSONObject().put("selfDeclaredMadeForKids", kids).put("containsSyntheticMedia", synthetic)
        val schedule = request.scheduledAtMillis
        if (schedule != null) status.put("privacyStatus", "private").put("publishAt", Instant.ofEpochMilli(schedule).toString())
        else status.put("privacyStatus", if (request.makePublic) "public" else "private")
        val body = JSONObject()
            .put("snippet", JSONObject().put("title", details.title.take(MAX_TITLE)).put("description", request.caption.take(MAX_DESCRIPTION)).put("tags", JSONArray(details.tags)).put("categoryId", CATEGORY_PEOPLE_AND_BLOGS))
            .put("status", status)
        val upload = when (val r = api.postRaw(
            "$UPLOAD/videos?uploadType=resumable&part=snippet,status", body.toString(),
            mapOf("Authorization" to "Bearer $bearer", "Content-Type" to "application/json; charset=UTF-8", "X-Upload-Content-Length" to file.length().toString(), "X-Upload-Content-Type" to "video/mp4"),
        ) { it.headers["location"] }) {
            is AdapterOutcome.Ok -> r.value ?: return fail("YouTube did not give an upload address")
            is AdapterOutcome.Failed -> return r
        }
        val id = when (val r = api.putFile(upload, file, mapOf("Content-Type" to "video/mp4")) { JSONObject(it).getString("id") }) {
            is AdapterOutcome.Ok -> r.value
            is AdapterOutcome.Failed -> return r
        }
        // The thumbnail is a nice-to-have: if it fails the video is still posted (the error was reported).
        details.thumbnail?.takeIf { it.exists() && it.length() <= MAX_THUMBNAIL_BYTES }?.let { image ->
            val mime = if (image.extension.lowercase() == "png") "image/png" else "image/jpeg"
            api.postBytes("$UPLOAD/thumbnails/set?videoId=$id&uploadType=media", image.readBytes(), mapOf("Authorization" to "Bearer $bearer", "Content-Type" to mime)) { it }
        }
        return AdapterOutcome.Ok(id)
    }

    override suspend fun status(accountId: String, remoteId: String): AdapterOutcome<PublishStatus> {
        val bearer = token(YouTubeScopes.READ) ?: return AdapterOutcome.Failed(AdapterError.NotAuthorized)
        return api.get("$DATA/videos?part=status,processingDetails&id=$remoteId", mapOf("Authorization" to "Bearer $bearer")) { json ->
            val item = JSONObject(json).optJSONArray("items")?.optJSONObject(0) ?: error("not found")
            statusOf(item, remoteId)
        }
    }

    /** The video's views so far (1 quota unit), for the first-24-hours reading. */
    suspend fun views(remoteId: String): AdapterOutcome<Long> {
        val bearer = token(YouTubeScopes.READ) ?: return AdapterOutcome.Failed(AdapterError.NotAuthorized)
        return api.get("$DATA/videos?part=statistics&id=$remoteId", mapOf("Authorization" to "Bearer $bearer")) { json ->
            JSONObject(json).getJSONArray("items").getJSONObject(0).getJSONObject("statistics").getString("viewCount").toLong()
        }
    }

    /**
     * Moves the publish time of a video that is already uploaded. `videos.update` replaces the whole status part, so the
     * answers are sent again. Needs the broader `youtube` permission, asked for here on first use.
     */
    suspend fun reschedule(remoteId: String, atMillis: Long, madeForKids: Boolean, aiContent: Boolean): AdapterOutcome<Unit> {
        val bearer = token(YouTubeScopes.MANAGE) ?: return AdapterOutcome.Failed(AdapterError.NotAuthorized)
        val body = JSONObject().put("id", remoteId).put(
            "status",
            JSONObject().put("privacyStatus", "private").put("publishAt", Instant.ofEpochMilli(atMillis).toString())
                .put("selfDeclaredMadeForKids", madeForKids).put("containsSyntheticMedia", aiContent),
        )
        return api.put("$DATA/videos?part=status", body.toString().toByteArray(), mapOf("Authorization" to "Bearer $bearer", "Content-Type" to "application/json; charset=UTF-8")) { }
    }

    companion object {
        private const val UPLOAD = "https://www.googleapis.com/upload/youtube/v3"
        private const val DATA = "https://www.googleapis.com/youtube/v3"
        private const val CATEGORY_PEOPLE_AND_BLOGS = "22"
        private const val MAX_TITLE = 100
        private const val MAX_DESCRIPTION = 5000
        private const val MAX_THUMBNAIL_BYTES = 2L * 1024 * 1024

        /** One video item of `videos.list(status, processingDetails)` as a [PublishStatus]. */
        fun statusOf(item: JSONObject, id: String): PublishStatus {
            val status = item.optJSONObject("status")
            val upload = status?.optString("uploadStatus").orEmpty()
            if (upload == "failed" || upload == "rejected") {
                return PublishStatus.Failed(status?.optString("failureReason")?.ifBlank { null } ?: status?.optString("rejectionReason")?.ifBlank { null } ?: upload)
            }
            if (item.optJSONObject("processingDetails")?.optString("processingStatus") == "processing") return PublishStatus.Processing("processing")
            val publishAt = status?.optString("publishAt")?.takeIf { it.isNotBlank() }?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
            return when (status?.optString("privacyStatus")) {
                "public", "unlisted" -> PublishStatus.Live("https://youtu.be/$id")
                else -> if (publishAt != null && publishAt > System.currentTimeMillis()) PublishStatus.Scheduled(publishAt)
                else PublishStatus.PrivateUntilApproved("YouTube keeps uploads from apps it has not audited private")
            }
        }
    }
}
