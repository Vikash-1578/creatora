package com.creatora.app.data.facebook

import android.app.Application
import com.creatora.app.data.ai.FakeTransport
import com.creatora.app.data.ai.HttpResponse
import com.creatora.app.data.platform.redirectParams
import com.creatora.app.domain.account.AdapterError
import com.creatora.app.domain.account.AdapterOutcome
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.account.PlatformRef
import com.creatora.app.domain.platform.LoginOutcome
import com.creatora.app.domain.platform.PlatformKeys
import com.creatora.app.domain.platform.PublishRequest
import com.creatora.app.domain.platform.PublishSource
import com.creatora.app.domain.platform.PublishStatus
import com.creatora.app.domain.platform.StoredToken
import com.creatora.app.testutil.FakeBrowser
import com.creatora.app.testutil.FakeErrorReporter
import com.creatora.app.testutil.FakeKeys
import com.creatora.app.testutil.FakeTokenStore
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val PAGES = """{"data":[
 {"id":"p1","name":"Maya Codes","about":"Shorts","followers_count":900,"fan_count":800,"picture":{"data":{"url":"https://cdn/p1.jpg"}},"access_token":"page-tok-1"},
 {"id":"p2","name":"Old Page","fan_count":50,"access_token":"page-tok-2"},
 {"id":"p3","name":"No token"}]}"""
private const val POSTS = """{"data":[
 {"id":"p1_1","message":"New #tips video\nlink","created_time":"2026-11-02T09:00:00+0000","permalink_url":"https://fb/1","full_picture":"https://cdn/1.jpg","reactions":{"summary":{"total_count":30}},"comments":{"summary":{"total_count":5}}},
 {"id":"p1_2","message":"Older","created_time":"2026-10-01T09:00:00+0000"}]}"""

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FacebookTest {
    private val transport = FakeTransport()
    private val tokens = FakeTokenStore()
    private val errors = FakeErrorReporter()
    private val keys = FakeKeys()
    private val adapter get() = FacebookAdapter(transport, errors, tokens)
    private fun signedIn() = tokens.put(ConnectPlatform.FACEBOOK, "p1", StoredToken("page-tok-1"))

    @Test
    fun pages_useFollowers_thenLikes_andKeepEachPagesToken() {
        val pages = parseFacebookPages(PAGES)
        assertEquals(900L, pages[0].preview.subscribers)
        assertEquals(50L, pages[1].preview.subscribers) // the older "fan_count" when there is no follower count
        assertEquals("https://cdn/p1.jpg", pages[0].preview.photoUrl)
        assertEquals(listOf("page-tok-1", "page-tok-2", null), pages.map { it.token })
    }

    @Test
    fun posts_areReadWithReactionsAndComments_andNullWhenAbsent() {
        val posts = parseFacebookPosts(POSTS)
        val first = posts[0].toVideoStats(400)
        assertEquals("2026-11-02T09:00:00Z", first.publishedAt)
        assertEquals(30L, first.likes)
        assertEquals(5L, first.comments)
        assertEquals(listOf("tips"), first.tags)
        assertNull(posts[1].reactions)
    }

    @Test
    fun insights_addUpTheDays_andSkipBreakdowns() {
        val json = """{"data":[{"name":"page_media_view","values":[{"value":10},{"value":15}]},{"name":"x","values":[]},{"name":"page_video_view_time","values":[{"value":120000}]}]}"""
        assertEquals(mapOf("page_media_view" to 25L, "page_video_view_time" to 120000L), parseFacebookInsights(json))
        assertEquals(listOf("hi"), parseFacebookComments("""{"data":[{"message":"hi"},{"id":"1"}]}"""))
    }

    // ---------------------------------------------------------------- login
    private fun login(browser: FakeBrowser) = FacebookLogin(keys, browser, transport, errors, tokens) { 5L }

    private fun answerLogin(pages: String = PAGES) {
        transport.getResponses["fb_exchange_token"] = HttpResponse(200, """{"access_token":"user-long"}""")
        transport.getResponses["oauth/access_token?client_id"] = HttpResponse(200, """{"access_token":"user-short"}""")
        transport.getResponses["me/accounts"] = HttpResponse(200, pages)
    }

    @Test
    fun signIn_usesTheConfigurationId_andSavesATokenPerPage() = runTest {
        answerLogin()
        val browser = FakeBrowser()

        val outcome = login(browser).signIn() as LoginOutcome.Signed

        val query = redirectParams(browser.opened.single())
        assertEquals("fb-app", query["client_id"])
        assertEquals("fb-config", query["config_id"])
        assertNull(query["scope"])
        assertEquals(listOf("Maya Codes", "Old Page"), outcome.accounts.map { it.title }) // the Page without a token is left out
        assertEquals("page-tok-1", tokens.get(ConnectPlatform.FACEBOOK, "p1")!!.access)
        assertEquals("page-tok-2", tokens.get(ConnectPlatform.FACEBOOK, "p2")!!.access)
        assertTrue(transport.getUrls.last().contains("access_token=user-long"))
    }

    @Test
    fun signIn_withoutAConfigurationId_asksForThePermissionsDirectly() = runTest {
        keys.all[ConnectPlatform.FACEBOOK] = PlatformKeys("fb-app", "fb-secret")
        answerLogin()
        val browser = FakeBrowser()
        login(browser).signIn()
        val scope = redirectParams(browser.opened.single()).getValue("scope")
        assertTrue(scope.contains("pages_manage_posts") && scope.contains("read_insights"))
    }

    @Test
    fun signIn_saysWhenThereAreNoPages_andWhatIsMissing() = runTest {
        answerLogin(pages = """{"data":[]}""")
        val none = login(FakeBrowser()).signIn() as LoginOutcome.Failed
        assertTrue(none.reason.contains("no Pages"))
        assertTrue(tokens.saved.isEmpty())

        keys.all.clear()
        assertEquals(LoginOutcome.NotConfigured(listOf("FACEBOOK_APP_ID", "FACEBOOK_APP_SECRET")), login(FakeBrowser()).signIn())
    }

    @Test
    fun signIn_showsFacebooksOwnReason_whenTheCodeIsRefused() = runTest {
        transport.getResponses["oauth/access_token"] = HttpResponse(400, """{"error":{"message":"This authorization code has expired.","code":100}}""")
        assertEquals(LoginOutcome.Failed("This authorization code has expired."), login(FakeBrowser()).signIn())
    }

    // ---------------------------------------------------------------- data
    @Test
    fun recentVideos_getViewsPerPost_andSkipThoseWithout() = runTest {
        signedIn()
        transport.getResponses["p1/posts"] = HttpResponse(200, POSTS)
        transport.getResponses["p1_1/insights"] = HttpResponse(200, """{"data":[{"name":"post_media_view","values":[{"value":400}]}]}""")
        transport.getResponses["p1_2/insights"] = HttpResponse(400, """{"error":{"message":"Metric not supported","code":100}}""")

        val videos = (adapter.recentVideos("p1", 10) as AdapterOutcome.Ok).value

        assertEquals(listOf("p1_1"), videos.map { it.id })
        assertEquals(400L, videos.single().views)
        assertTrue(transport.getUrls.all { it.contains("access_token=page-tok-1") })
    }

    @Test
    fun channelMetrics_askEachMetricOnItsOwn_soOneRefusalLeavesOnlyThatFigureEmpty() = runTest {
        signedIn()
        transport.getResponses["metric=page_media_view"] = HttpResponse(200, """{"data":[{"name":"page_media_view","values":[{"value":1000},{"value":500}]}]}""")
        transport.getResponses["metric=page_daily_follows_unique"] = HttpResponse(200, """{"data":[{"name":"page_daily_follows_unique","values":[{"value":20}]}]}""")
        transport.getResponses["metric=page_daily_unfollows_unique"] = HttpResponse(200, """{"data":[{"name":"page_daily_unfollows_unique","values":[{"value":5}]}]}""")
        transport.getResponses["metric=page_video_view_time"] = HttpResponse(400, """{"error":{"message":"deprecated","code":100}}""")

        val m = (adapter.channelMetrics("p1", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 28)) as AdapterOutcome.Ok).value

        assertEquals(1500L, m.views)
        assertEquals(15L, m.netSubscribers)
        assertNull(m.watchMinutes)
    }

    @Test
    fun channelMetrics_failWithTheRealReason_whenViewsAreRefused() = runTest {
        signedIn()
        transport.getResponses["metric=page_media_view"] = HttpResponse(403, """{"error":{"message":"Needs read_insights","code":200}}""")
        assertEquals(AdapterOutcome.Failed(AdapterError.PermissionMissing), adapter.channelMetrics("p1", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 28)))
    }

    @Test
    fun lookup_isForOwnPagesOnly_andCommentsAreRead() = runTest {
        assertTrue((adapter.lookup(PlatformRef(ConnectPlatform.FACEBOOK, "p9", null)) as AdapterOutcome.Failed).error is AdapterError.NotAvailable)
        signedIn()
        transport.getResponses["p1?fields="] = HttpResponse(200, """{"id":"p1","name":"Maya Codes","fan_count":800}""")
        assertEquals(800L, (adapter.lookup(PlatformRef(ConnectPlatform.FACEBOOK, "p1", null)) as AdapterOutcome.Ok).value.subscribers)
        transport.getResponses["p1_1/comments"] = HttpResponse(200, """{"data":[{"message":"love it"}]}""")
        assertEquals(listOf("love it"), (adapter.comments("p1", "p1_1") as AdapterOutcome.Ok).value)
        assertTrue(adapter.trending("IN", null, 5) is AdapterOutcome.Failed)
    }

    // ---------------------------------------------------------------- publishing
    private fun publisher() = FacebookPublisher(transport, errors, tokens)

    @Test
    fun publish_postsAVideoFromALink_orTextOnly() = runTest {
        signedIn()
        transport.postResponses["/p1/videos"] = HttpResponse(200, """{"id":"v1"}""")
        transport.postResponses["/p1/feed"] = HttpResponse(200, """{"id":"p1_77"}""")

        assertEquals(AdapterOutcome.Ok("v1"), publisher().publish(PublishRequest("p1", "Hello", PublishSource.RemoteUrl("https://f.example/v.mp4"))))
        assertTrue(transport.postBodies.last().contains("file_url=https%3A%2F%2Ff.example%2Fv.mp4"))
        assertTrue(!transport.postBodies.last().contains("published"))

        assertEquals(AdapterOutcome.Ok("p1_77"), publisher().publish(PublishRequest("p1", "Just text")))
        assertTrue(transport.postBodies.last().contains("message=Just+text"))
    }

    @Test
    fun publish_schedulesNatively_andRefusesALocalFile() = runTest {
        signedIn()
        transport.postResponses["/p1/videos"] = HttpResponse(200, """{"id":"v2"}""")
        publisher().publish(PublishRequest("p1", "Later", PublishSource.RemoteUrl("https://f.example/v.mp4"), scheduledAtMillis = 1_800_000_000_000L))
        val body = transport.postBodies.last()
        assertTrue(body.contains("published=false"))
        assertTrue(body.contains("scheduled_publish_time=1800000000"))

        val local = publisher().publish(PublishRequest("p1", "x", PublishSource.LocalFile(File("v.mp4")))) as AdapterOutcome.Failed
        assertTrue(local.error.toString().contains("public https link"))
        assertEquals(AdapterOutcome.Failed(AdapterError.NotAuthorized), FacebookPublisher(transport, errors, FakeTokenStore()).publish(PublishRequest("p1", "x")))
    }

    @Test
    fun publishFailures_showFacebooksReason() = runTest {
        signedIn()
        transport.postResponses["/p1/feed"] = HttpResponse(403, """{"error":{"message":"pages_manage_posts is not granted","code":200}}""")
        assertEquals(AdapterOutcome.Failed(AdapterError.PermissionMissing), publisher().publish(PublishRequest("p1", "x")))
        assertEquals(1, errors.recorded.size)
    }

    @Test
    fun status_mapsProcessingErrorAndLive() = runTest {
        signedIn()
        transport.getResponses["v1?fields"] = HttpResponse(200, """{"status":{"video_status":"processing"},"permalink_url":"/x"}""")
        assertEquals(AdapterOutcome.Ok(PublishStatus.Processing("processing")), publisher().status("p1", "v1"))
        transport.getResponses["v1?fields"] = HttpResponse(200, """{"status":{"video_status":"error"}}""")
        assertTrue((publisher().status("p1", "v1") as AdapterOutcome.Ok).value is PublishStatus.Failed)
        transport.getResponses["v1?fields"] = HttpResponse(200, """{"status":{"video_status":"ready"},"permalink_url":"https://fb/v1"}""")
        assertEquals(AdapterOutcome.Ok(PublishStatus.Live("https://fb/v1")), publisher().status("p1", "v1"))
    }
}
