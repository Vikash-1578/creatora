package com.creatora.app.data.youtube

import android.app.Application
import com.creatora.app.data.ai.FakeTransport
import com.creatora.app.data.ai.HttpResponse
import com.creatora.app.domain.account.AdapterError
import com.creatora.app.domain.account.AdapterOutcome
import com.creatora.app.domain.platform.PostDetails
import com.creatora.app.domain.platform.PublishRequest
import com.creatora.app.domain.platform.PublishSource
import com.creatora.app.domain.platform.PublishStatus
import com.creatora.app.domain.repository.AuthorizationOutcome
import com.creatora.app.testutil.FakeErrorReporter
import com.creatora.app.testutil.FakeYouTubeAuthorizer
import java.io.File
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class YouTubeUploaderTest {
    @get:Rule val folder = TemporaryFolder()
    private val transport = FakeTransport()
    private val authorizer = FakeYouTubeAuthorizer()
    private val errors = FakeErrorReporter()
    private val uploader = YouTubeUploader(transport, authorizer, errors)

    private val video get() = folder.newFile().also { it.writeBytes(ByteArray(2048) { 1 }) }
    private fun details(kids: Boolean? = false, ai: Boolean? = true, thumbnail: File? = null) = PostDetails("My title", listOf("coding", "python"), kids, ai, thumbnail)
    private fun request(file: File, scheduledAt: Long? = null, makePublic: Boolean = false, details: PostDetails = details()) =
        PublishRequest("UC1", "A description", PublishSource.LocalFile(file), scheduledAt, makePublic, details)

    private fun answerUpload() {
        transport.postResponses["uploadType=resumable"] = HttpResponse(200, "", mapOf("location" to "https://upload.example/session1"))
        transport.putFileResponse = HttpResponse(200, """{"id":"vid1","status":{"privacyStatus":"private"}}""")
    }

    @Test
    fun publish_startsAResumableUpload_sendsTheFile_andReturnsTheVideoId() = runTest {
        answerUpload()
        val file = video

        assertEquals(AdapterOutcome.Ok("vid1"), uploader.publish(request(file)))

        val body = JSONObject(transport.postBodies.single())
        assertEquals("My title", body.getJSONObject("snippet").getString("title"))
        assertEquals("A description", body.getJSONObject("snippet").getString("description"))
        assertEquals(2, body.getJSONObject("snippet").getJSONArray("tags").length())
        val status = body.getJSONObject("status")
        assertEquals("private", status.getString("privacyStatus")) // public only when chosen
        assertFalse(status.getBoolean("selfDeclaredMadeForKids"))
        assertTrue(status.getBoolean("containsSyntheticMedia"))
        assertFalse(status.has("publishAt"))
        assertEquals("2048", transport.headers["X-Upload-Content-Length"])
        assertEquals("Bearer token", transport.headers["Authorization"])
        assertEquals(listOf("https://upload.example/session1"), transport.putFileUrls)
        assertEquals("video/mp4", transport.putFileHeaders.single()["Content-Type"])
    }

    @Test
    fun publish_schedulesWithPublishAt_orAsksForPublicOnlyWhenChosen() = runTest {
        answerUpload()
        uploader.publish(request(video, scheduledAt = 1_800_000_000_000L, makePublic = true))
        val scheduled = JSONObject(transport.postBodies.last()).getJSONObject("status")
        assertEquals("private", scheduled.getString("privacyStatus")) // YouTube publishes it at the time
        assertEquals("2027-01-15T08:00:00Z", scheduled.getString("publishAt"))

        uploader.publish(request(video, makePublic = true))
        assertEquals("public", JSONObject(transport.postBodies.last()).getJSONObject("status").getString("privacyStatus"))
    }

    @Test
    fun publish_setsTheThumbnail_andStillSucceedsIfItFails() = runTest {
        answerUpload()
        val image = folder.newFile("t.png").also { it.writeBytes(ByteArray(100)) }

        assertEquals(AdapterOutcome.Ok("vid1"), uploader.publish(request(video, details = details(thumbnail = image))))
        assertEquals(listOf("https://www.googleapis.com/upload/youtube/v3/thumbnails/set?videoId=vid1&uploadType=media"), transport.postBytesUrls)

        transport.postBytesResponse = HttpResponse(403, """{"error":{"message":"The video cannot have a custom thumbnail"}}""")
        assertEquals(AdapterOutcome.Ok("vid1"), uploader.publish(request(video, details = details(thumbnail = image))))
        assertEquals(1, errors.recorded.size) // the reason was reported
    }

    @Test
    fun publish_neverSendsAnUnansweredQuestion_andNeedsTheFileAndTheUploadPermission() = runTest {
        answerUpload()
        val noKids = uploader.publish(request(video, details = details(kids = null))) as AdapterOutcome.Failed
        assertTrue((noKids.error as AdapterError.NotAvailable).reason.contains("Made for kids"))
        val noAi = uploader.publish(request(video, details = details(ai = null))) as AdapterOutcome.Failed
        assertTrue((noAi.error as AdapterError.NotAvailable).reason.contains("AI-content"))
        assertTrue(transport.postUrls.isEmpty()) // nothing was started

        assertTrue((uploader.publish(request(File("missing.mp4"))) as AdapterOutcome.Failed).error is AdapterError.NotAvailable)
        assertTrue((uploader.publish(PublishRequest("UC1", "x", null, details = details())) as AdapterOutcome.Failed).error is AdapterError.NotAvailable)

        authorizer.outcome = AuthorizationOutcome.Denied
        assertEquals(AdapterOutcome.Failed(AdapterError.NotAuthorized), uploader.publish(request(video)))
        assertFalse(uploader.ensureUploadAccess())
    }

    @Test
    fun publish_showsGooglesOwnReason_whenTheUploadIsRefused() = runTest {
        transport.postResponses["uploadType=resumable"] = HttpResponse(403, """{"error":{"code":403,"message":"The request cannot be completed because you have exceeded your quota."}}""")
        val result = uploader.publish(request(video)) as AdapterOutcome.Failed
        assertEquals(AdapterError.NotAvailable("The request cannot be completed because you have exceeded your quota."), result.error)
        assertTrue(transport.putFileUrls.isEmpty())

        transport.postResponses["uploadType=resumable"] = HttpResponse(200, "") // no Location header
        assertTrue((uploader.publish(request(video)) as AdapterOutcome.Failed).error is AdapterError.NotAvailable)
    }

    @Test
    fun status_saysPrivateUntilApproved_scheduled_live_processing_orFailed() {
        fun item(json: String) = JSONObject(json)
        val future = java.time.Instant.now().plusSeconds(3_600).toString()
        assertEquals(PublishStatus.Processing("processing"), YouTubeUploader.statusOf(item("""{"status":{"privacyStatus":"private"},"processingDetails":{"processingStatus":"processing"}}"""), "v"))
        assertTrue(YouTubeUploader.statusOf(item("""{"status":{"privacyStatus":"private"}}"""), "v") is PublishStatus.PrivateUntilApproved)
        assertTrue(YouTubeUploader.statusOf(item("""{"status":{"privacyStatus":"private","publishAt":"$future"}}"""), "v") is PublishStatus.Scheduled)
        assertEquals(PublishStatus.Live("https://youtu.be/v"), YouTubeUploader.statusOf(item("""{"status":{"privacyStatus":"public"}}"""), "v"))
        assertEquals(PublishStatus.Failed("duplicate"), YouTubeUploader.statusOf(item("""{"status":{"uploadStatus":"rejected","rejectionReason":"duplicate"}}"""), "v"))
    }

    @Test
    fun statusAndViews_areReadFromTheVideoList() = runTest {
        transport.getResponses["part=status,processingDetails"] = HttpResponse(200, """{"items":[{"status":{"privacyStatus":"public"}}]}""")
        assertEquals(AdapterOutcome.Ok(PublishStatus.Live("https://youtu.be/vid1")), uploader.status("UC1", "vid1"))
        transport.getResponses["part=statistics"] = HttpResponse(200, """{"items":[{"statistics":{"viewCount":"1234"}}]}""")
        assertEquals(AdapterOutcome.Ok(1234L), uploader.views("vid1"))
        transport.getResponses["part=status,processingDetails"] = HttpResponse(200, """{"items":[]}""")
        assertTrue(uploader.status("UC1", "gone") is AdapterOutcome.Failed)
    }

    @Test
    fun reschedule_sendsTheWholeStatusAgain_withTheNewTime() = runTest {
        transport.putResponse = HttpResponse(200, "{}")
        assertEquals(AdapterOutcome.Ok(Unit), uploader.reschedule("vid1", 1_800_000_000_000L, madeForKids = false, aiContent = true))
        val body = JSONObject(transport.putBodies.single())
        assertEquals("vid1", body.getString("id"))
        val status = body.getJSONObject("status")
        assertEquals("2027-01-15T08:00:00Z", status.getString("publishAt"))
        assertFalse(status.getBoolean("selfDeclaredMadeForKids"))
        assertTrue(status.getBoolean("containsSyntheticMedia"))
        assertTrue(transport.putUrls.single().endsWith("videos?part=status"))
    }
}
