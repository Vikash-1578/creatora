package com.creatora.app.data.tiktok

import android.app.Application
import com.creatora.app.data.ai.FakeTransport
import com.creatora.app.data.ai.HttpResponse
import com.creatora.app.data.platform.Pkce
import com.creatora.app.data.platform.redirectParams
import com.creatora.app.domain.account.AdapterError
import com.creatora.app.domain.account.AdapterOutcome
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.account.PlatformRef
import com.creatora.app.domain.platform.LoginOutcome
import com.creatora.app.domain.platform.PublishRequest
import com.creatora.app.domain.platform.PublishSource
import com.creatora.app.domain.platform.PublishStatus
import com.creatora.app.domain.platform.StoredToken
import com.creatora.app.testutil.FakeBrowser
import com.creatora.app.testutil.FakeErrorReporter
import com.creatora.app.testutil.FakeKeys
import com.creatora.app.testutil.FakeTokenStore
import java.io.File
import java.io.RandomAccessFile
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val USER = """{"data":{"user":{"open_id":"oid1","display_name":"Maya","username":"mr.maya","bio_description":"Coding Shorts","avatar_url":"https://cdn/a.jpg","follower_count":4300,"video_count":12}},"error":{"code":"ok","message":""}}"""
private const val VIDEOS = """{"data":{"videos":[
 {"id":"v1","title":"Python in 30s #coding","create_time":1793000000,"cover_image_url":"https://cdn/c1.jpg","view_count":9000,"like_count":700,"comment_count":40},
 {"id":"v2","video_description":"no count","create_time":1792000000}],"cursor":1,"has_more":false},"error":{"code":"ok"}}"""

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TikTokTest {
    @get:Rule val folder = TemporaryFolder()
    private val transport = FakeTransport()
    private val tokens = FakeTokenStore()
    private val errors = FakeErrorReporter()
    private val keys = FakeKeys()
    private var now = 1_000_000_000_000L
    private val adapter get() = TikTokAdapter(transport, errors, tokens, keys) { now }
    private fun signedIn(expiresAt: Long = now + 3_600_000L, refresh: String? = "refresh-1") =
        tokens.put(ConnectPlatform.TIKTOK, "oid1", StoredToken("tt-tok", refresh, expiresAt, now))

    @Test
    fun user_andVideos_areReadWithRealCountsOnly() {
        val user = parseTikTokUser(USER)
        assertEquals("oid1", user.id)
        assertEquals("@mr.maya", user.handle)
        assertEquals(4300L, user.subscribers)
        assertEquals(12L, user.videoCount)
        val videos = parseTikTokVideos(VIDEOS)
        assertEquals(listOf("v1"), videos.map { it.id }) // a video without a view count is left out
        assertEquals(9000L, videos.single().views)
        assertEquals(listOf("coding"), videos.single().tags)
        assertEquals("2026-10-26T07:33:20Z", videos.single().publishedAt)
        assertEquals(TikTokTokens("a", "r", 86400, "oid"), parseTikTokTokens("""{"access_token":"a","refresh_token":"r","expires_in":86400,"open_id":"oid"}"""))
    }

    // ---------------------------------------------------------------- login
    private fun login(browser: FakeBrowser) = TikTokLogin(keys, browser, transport, errors, tokens) { now }

    @Test
    fun signIn_usesPkce_andKeepsBothTokens() = runTest {
        transport.postResponses["oauth/token"] = HttpResponse(200, """{"access_token":"tt-tok","refresh_token":"r1","expires_in":86400,"open_id":"oid1","scope":"x"}""")
        transport.getResponses["user/info"] = HttpResponse(200, USER)
        val browser = FakeBrowser()

        val outcome = login(browser).signIn() as LoginOutcome.Signed

        val query = redirectParams(browser.opened.single())
        assertEquals("tt-key", query["client_key"])
        assertEquals("S256", query["code_challenge_method"])
        assertTrue(query.getValue("scope").contains("video.publish"))
        // The verifier sent with the code belongs to the challenge sent at the start.
        val verifier = redirectParams("x?" + transport.postBodies.single())["code_verifier"]!!
        assertEquals(query["code_challenge"], Pkce.challenge(verifier))
        assertEquals("Maya", outcome.accounts.single().title)
        assertEquals("Bearer tt-tok", transport.getHeaders["Authorization"])
        val saved = tokens.get(ConnectPlatform.TIKTOK, "oid1")!!
        assertEquals("r1", saved.refresh)
        assertEquals(now + 86_400_000L, saved.expiresAt)
    }

    @Test
    fun signIn_reportsRealReasons() = runTest {
        keys.all.clear()
        assertEquals(LoginOutcome.NotConfigured(listOf("TIKTOK_CLIENT_KEY", "TIKTOK_CLIENT_SECRET")), login(FakeBrowser()).signIn())
        keys.all[ConnectPlatform.TIKTOK] = com.creatora.app.domain.platform.PlatformKeys("k", "s")
        assertEquals(LoginOutcome.Cancelled, login(FakeBrowser { "creatora://oauth?error=access_denied" }).signIn())
        transport.postResponses["oauth/token"] = HttpResponse(400, """{"error":"invalid_grant","error_description":"Authorization code is expired"}""")
        assertEquals(LoginOutcome.Failed("Authorization code is expired"), login(FakeBrowser()).signIn())
        assertTrue(tokens.saved.isEmpty())
    }

    // ---------------------------------------------------------------- data
    @Test
    fun recentVideos_postTheCountLimit_withTheBearerToken() = runTest {
        signedIn()
        transport.postResponses["video/list"] = HttpResponse(200, VIDEOS)

        val videos = (adapter.recentVideos("oid1", 50) as AdapterOutcome.Ok).value

        assertEquals(listOf("v1"), videos.map { it.id })
        assertEquals("""{"max_count":20}""", transport.postBodies.single()) // TikTok allows at most 20 per page
        assertEquals("Bearer tt-tok", transport.headers["Authorization"])
    }

    @Test
    fun anExpiredToken_isRefreshed_andTheNewOneSaved() = runTest {
        signedIn(expiresAt = now + 60_000L)
        transport.postResponses["oauth/token"] = HttpResponse(200, """{"access_token":"tt-new","refresh_token":"refresh-2","expires_in":86400,"open_id":"oid1"}""")
        transport.postResponses["video/list"] = HttpResponse(200, VIDEOS)

        adapter.recentVideos("oid1", 5)

        assertTrue(transport.postBodies.first().contains("grant_type=refresh_token"))
        assertTrue(transport.postBodies.first().contains("refresh_token=refresh-1"))
        assertEquals("Bearer tt-new", transport.headers["Authorization"])
        assertEquals("refresh-2", tokens.get(ConnectPlatform.TIKTOK, "oid1")!!.refresh)
    }

    @Test
    fun notSignedIn_orRefusedByTikTok_saysSo() = runTest {
        assertEquals(AdapterOutcome.Failed(AdapterError.NotAuthorized), adapter.recentVideos("oid1", 5))
        assertTrue((adapter.lookup(PlatformRef(ConnectPlatform.TIKTOK, "someone", null)) as AdapterOutcome.Failed).error is AdapterError.NotAvailable)
        signedIn()
        transport.postResponses["video/list"] = HttpResponse(403, """{"error":{"code":"scope_not_authorized","message":"video.list not granted"}}""")
        assertEquals(AdapterOutcome.Failed(AdapterError.PermissionMissing), adapter.recentVideos("oid1", 5))
        assertEquals(1, errors.recorded.size)
    }

    @Test
    fun whatTikTokDoesNotShare_saysSoInsteadOfInventingNumbers() = runTest {
        val m = adapter.channelMetrics("oid1", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 28)) as AdapterOutcome.Failed
        assertTrue((m.error as AdapterError.NotAvailable).reason.contains("analytics"))
        assertTrue(adapter.trending("IN", null, 5) is AdapterOutcome.Failed)
        assertTrue(adapter.topComments("v1", 5) is AdapterOutcome.Failed)
        signedIn()
        transport.getResponses["user/info"] = HttpResponse(200, USER)
        assertEquals(4300L, (adapter.lookup(PlatformRef(ConnectPlatform.TIKTOK, "oid1", null)) as AdapterOutcome.Ok).value.subscribers)
    }

    // ---------------------------------------------------------------- publishing
    private fun publisher() = TikTokPublisher(transport, errors, tokens, keys) { now }
    private fun video(bytes: Int = 1000) = folder.newFile().also { it.writeBytes(ByteArray(bytes) { 7 }) }

    private fun answerPublish(options: String) {
        transport.postResponses["creator_info"] = HttpResponse(200, """{"data":{"privacy_level_options":$options},"error":{"code":"ok"}}""")
        transport.postResponses["video/init"] = HttpResponse(200, """{"data":{"publish_id":"pub-1","upload_url":"https://upload.example/u1"},"error":{"code":"ok"}}""")
        transport.putResponse = HttpResponse(201, "")
    }

    @Test
    fun publish_uploadsTheFileInOnePiece_andIsPrivateByDefault() = runTest {
        signedIn()
        answerPublish("""["PUBLIC_TO_EVERYONE","SELF_ONLY"]""")

        assertEquals(AdapterOutcome.Ok("pub-1"), publisher().publish(PublishRequest("oid1", "My video #fyp", PublishSource.LocalFile(video(1000)))))

        val init = transport.postBodies.last()
        assertTrue(init.contains("\"privacy_level\":\"SELF_ONLY\"")) // public only when the user chose it
        assertTrue(init.contains("\"video_size\":1000"))
        assertTrue(init.contains("\"total_chunk_count\":1"))
        assertEquals("https://upload.example/u1", transport.putUrls.single())
        assertEquals("bytes 0-999/1000", transport.putHeaders.single()["Content-Range"])
    }

    @Test
    fun publish_isPublicOnlyWhenChosenAndOffered() = runTest {
        signedIn()
        answerPublish("""["PUBLIC_TO_EVERYONE","SELF_ONLY"]""")
        publisher().publish(PublishRequest("oid1", "x", PublishSource.LocalFile(video()), makePublic = true))
        assertTrue(transport.postBodies.last().contains("PUBLIC_TO_EVERYONE"))

        answerPublish("""["SELF_ONLY"]""") // TikTok has not approved the app: only private is offered
        publisher().publish(PublishRequest("oid1", "x", PublishSource.LocalFile(video()), makePublic = true))
        assertTrue(transport.postBodies.last().contains("SELF_ONLY"))
    }

    @Test
    fun publish_refusesWhatItCannotSend_andShowsTikToksReason() = runTest {
        signedIn()
        answerPublish("""["SELF_ONLY"]""")
        val big = folder.newFile("big.mp4").also { RandomAccessFile(it, "rw").use { f -> f.setLength(65L * 1024 * 1024) } }
        val tooBig = publisher().publish(PublishRequest("oid1", "x", PublishSource.LocalFile(big))) as AdapterOutcome.Failed
        assertTrue(tooBig.error.toString().contains("64 MB"))
        assertTrue(transport.postUrls.isEmpty()) // nothing was started

        assertTrue((publisher().publish(PublishRequest("oid1", "x", PublishSource.RemoteUrl("https://x/v.mp4"))) as AdapterOutcome.Failed).error.toString().contains("video file"))
        assertTrue((publisher().publish(PublishRequest("oid1", "x", PublishSource.LocalFile(File("missing.mp4")))) as AdapterOutcome.Failed).error.toString().contains("missing"))

        transport.postResponses["video/init"] = HttpResponse(403, """{"error":{"code":"scope_not_authorized","message":"video.publish not granted"}}""")
        assertEquals(AdapterOutcome.Failed(AdapterError.PermissionMissing), publisher().publish(PublishRequest("oid1", "x", PublishSource.LocalFile(video()))))
    }

    @Test
    fun publish_failsWhenTheUploadIsRejected() = runTest {
        signedIn()
        answerPublish("""["SELF_ONLY"]""")
        transport.putResponse = HttpResponse(416, "bad range")
        assertTrue(publisher().publish(PublishRequest("oid1", "x", PublishSource.LocalFile(video()))) is AdapterOutcome.Failed)
    }

    @Test
    fun status_saysPrivateUntilApproved_whenTikTokReturnsNoPublicPostId() = runTest {
        signedIn()
        transport.postResponses["status/fetch"] = HttpResponse(200, """{"data":{"status":"PROCESSING_UPLOAD"},"error":{"code":"ok"}}""")
        assertEquals(AdapterOutcome.Ok(PublishStatus.Processing("PROCESSING_UPLOAD")), publisher().status("oid1", "pub-1"))

        transport.postResponses["status/fetch"] = HttpResponse(200, """{"data":{"status":"PUBLISH_COMPLETE","publicaly_available_post_id":[]},"error":{"code":"ok"}}""")
        assertTrue((publisher().status("oid1", "pub-1") as AdapterOutcome.Ok).value is PublishStatus.PrivateUntilApproved)

        transport.postResponses["status/fetch"] = HttpResponse(200, """{"data":{"status":"PUBLISH_COMPLETE","publicaly_available_post_id":[123]},"error":{"code":"ok"}}""")
        assertEquals(AdapterOutcome.Ok(PublishStatus.Live(null)), publisher().status("oid1", "pub-1"))

        transport.postResponses["status/fetch"] = HttpResponse(200, """{"data":{"status":"FAILED","fail_reason":"file_format_check_failed"},"error":{"code":"ok"}}""")
        assertEquals(AdapterOutcome.Ok(PublishStatus.Failed("file_format_check_failed")), publisher().status("oid1", "pub-1"))
    }
}
